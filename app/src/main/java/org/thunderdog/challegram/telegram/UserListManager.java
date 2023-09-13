/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.collection.LongSet;

public abstract class UserListManager extends ListManager<Long> implements TdlibCache.UserDataChangeListener, TdlibCache.UserStatusChangeListener {
  public interface ChangeListener extends ListManager.ListChangeListener<Long> { }

  private final LongSet userIdsCheck = new LongSet();

  public UserListManager (Tdlib tdlib, int initialLoadCount, int loadCount, @Nullable ChangeListener listener) {
    super(tdlib, initialLoadCount, loadCount, false, listener);
  }

  @Override
  protected void subscribeToUpdates() {
    tdlib.cache().addGlobalUsersListener(this);
  }

  @Override
  protected void unsubscribeFromUpdates() {
    tdlib.cache().removeGlobalUsersListener(this);
  }

  @Override
  protected Response<Long> processResponse (TdApi.Object response, Client.ResultHandler retryHandler, int retryLoadCount, boolean reverse) {
    TdApi.Users users = (TdApi.Users) response;
    long[] rawUserIds = users.userIds;
    List<Long> userIds = new ArrayList<>(rawUserIds.length);
    for (long userId : rawUserIds) {
      if (userIdsCheck.add(userId)) {
        userIds.add(userId);
      }
    }
    return new Response<>(userIds, users.totalCount);
  }

  // Updates

  private void runWithUser (long userId, Runnable act) {
    runOnUiThreadIfReady(() -> {
      if (userIdsCheck.has(userId)) {
        act.run();
      }
    });
  }

  public static final int REASON_USER_CHANGED = 0;
  public static final int REASON_USER_FULL_CHANGED = 1;
  public static final int REASON_STATUS_CHANGED = 2;

  @Override
  public void onUserUpdated (TdApi.User user) {
    runWithUser(user.id, () -> {
      int index = indexOfItem(user.id);
      if (index != -1) {
        notifyItemChanged(index, REASON_USER_CHANGED);
      }
    });
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    runWithUser(userId, () -> {
      int index = indexOfItem(userId);
      if (index != -1) {
        notifyItemChanged(index, REASON_USER_FULL_CHANGED);
      }
    });
  }

  @Override
  public void onUserStatusChanged(long userId, TdApi.UserStatus status, boolean uiOnly) {
    runWithUser(userId, () -> {
      int index = indexOfItem(userId);
      if (index != -1) {
        notifyItemChanged(index, REASON_STATUS_CHANGED);
      }
    });
  }

  @Override
  public boolean needUserStatusUiUpdates() {
    return false;
  }
}
