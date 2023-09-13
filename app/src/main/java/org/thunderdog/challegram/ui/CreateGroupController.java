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
 *
 * File created on 09/02/2016 at 18:31
 */
package org.thunderdog.challegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.UiThread;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.filegen.SimpleGenerationInfo;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.EditHeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.Unlockable;
import org.thunderdog.challegram.widget.ListInfoView;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;

public class CreateGroupController extends ViewController<Void> implements EditHeaderView.ReadyCallback, OptionDelegate, Client.ResultHandler, Unlockable, ActivityResultHandler,
  TdlibCache.UserDataChangeListener, TdlibCache.UserStatusChangeListener {
  private ArrayList<TGUser> members;

  public CreateGroupController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public void setMembers (ArrayList<TGUser> members) {
    this.members = members;
  }

  private EditHeaderView headerCell;
  private MembersAdapter adapter;
  private RecyclerView recyclerView;

  @Override
  protected View onCreateView (Context context) {
    headerCell = new EditHeaderView(context, this);
    headerCell.setInputOptions(R.string.GroupName, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    headerCell.setImeOptions(EditorInfo.IME_ACTION_DONE);
    headerCell.setReadyCallback(this);
    setLockFocusView(headerCell.getInputView());

    FrameLayoutFix contentView;

    contentView = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(contentView, ColorId.filling, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = Size.getHeaderSizeDifference(false);

    recyclerView = new RecyclerView(context);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    recyclerView.setAdapter(adapter = new MembersAdapter(context, this));
    recyclerView.setLayoutParams(params);

    contentView.addView(recyclerView);

    subscribeToUpdates();

    return contentView;
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    long[] userIds = getUserIds();
    if (userIds != null && userIds.length > 0) {
      super.saveInstanceState(outState, keyPrefix);
      outState.putLongArray(keyPrefix + "userIds", userIds);
      return true;
    }
    return false;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    long[] userIds = in.getLongArray(keyPrefix + "userIds");
    if (userIds == null || userIds.length == 0) {
      return false;
    }
    ArrayList<TGUser> members = null;
    for (long userId : userIds) {
      TdApi.User user = tdlib.cache().user(userId);
      if (user == null) {
        return false;
      }
      if (members == null) {
        members = new ArrayList<>(userIds.length);
      }
      members.add(new TGUser(tdlib, user));
    }
    if (members != null) {
      super.restoreInstanceState(in, keyPrefix);
      this.members = members;
      return true;
    }
    return false;
  }

  private long[] getUserIds () {
    if (members == null || members.isEmpty()) {
      return ArrayUtils.EMPTY_LONGS;
    }
    long[] userIds = new long[members.size()];
    int i = 0;
    for (TGUser user : members) {
      userIds[i++] = user.getUserId();
    }
    return userIds;
  }

  @Override
  public void destroy () {
    super.destroy();
    Views.destroyRecyclerView(recyclerView);
    unsubscribeFromUpdates();
  }

  private void subscribeToUpdates () {
    tdlib.cache().subscribeToUserUpdates(getUserIds(), this);
  }

  private void unsubscribeFromUpdates () {
    tdlib.cache().unsubscribeFromUserUpdates(getUserIds(), this);
  }

  private int indexOfUser (long userId) {
    if (members == null || members.isEmpty()) {
      return -1;
    }
    int i = 0;
    for (TGUser futureMember : members) {
      if (futureMember.getUserId() == userId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.ui().post(() -> updateUser(user));
  }

  @Override
  public boolean needUserStatusUiUpdates () {
    return true;
  }

  @Override
  @UiThread
  public void onUserStatusChanged (final long userId, final TdApi.UserStatus status, boolean uiOnly) {
    updateUserStatus(userId, status);
  }

  private void updateUser (TdApi.User user) {
    int i = indexOfUser(user.id);
    if (i != 0) {
      members.get(i).setUser(user, 0);
      updateUserAtIndex(i + 1, false);
    }
  }

  private void updateUserStatus (long userId, TdApi.UserStatus status) {
    int i = indexOfUser(userId);
    if (i != 0) {
      members.get(i).setStatus(status);
      updateUserAtIndex(i + 1, true);
    }
  }

  private void updateUserAtIndex (int i, boolean subtextOnly) {
    View view = recyclerView.getLayoutManager().findViewByPosition(i);
    if (view instanceof UserView) {
      if (subtextOnly) {
        ((UserView) view).updateSubtext();
      } else {
        ((UserView) view).updateAll();
      }
      view.invalidate();
    } else {
      adapter.notifyItemChanged(i);
    }
  }

  // Recycler

  private static class MembersAdapter extends RecyclerView.Adapter<MemberViewHolder> implements View.OnClickListener {
    private Context context;
    private CreateGroupController controller;

    public MembersAdapter (Context context, CreateGroupController controller) {
      this.context = context;
      this.controller = controller;
    }

    @Override
    public void onClick (View v) {
      if (v != null && v instanceof UserView) {
        controller.onUserClick(((UserView) v).getUser());
      }
    }

    @Override
    public void onViewAttachedToWindow (MemberViewHolder holder) {
      holder.attachReceiver();
    }

    @Override
    public void onViewDetachedFromWindow (MemberViewHolder holder) {
      holder.detachReceiver();
    }

    @Override
    public MemberViewHolder onCreateViewHolder (ViewGroup parent, int viewType) {
      switch (viewType) {
        case MemberViewHolder.TYPE_USER: {
          UserView view = new UserView(context, controller.tdlib);
          view.setOffsetLeft(Screen.dp(22f));
          Views.setClickable(view);
          RippleSupport.setSimpleWhiteBackground(view);
          view.setOnClickListener(this);
          return new MemberViewHolder(view);
        }
        case MemberViewHolder.TYPE_INFO: {
          return new MemberViewHolder(new ListInfoView(context));
        }
        case MemberViewHolder.TYPE_HEADER: {
          View view = new View(context);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(11f)));
          return new MemberViewHolder(view);
        }
      }
      return null;
    }

    @Override
    public void onBindViewHolder (MemberViewHolder holder, int position) {
      switch (holder.getItemViewType()) {
        case MemberViewHolder.TYPE_USER: {
          ((UserView) holder.itemView).setUser(controller.members.get(position - 1));
          break;
        }
        case MemberViewHolder.TYPE_INFO: {
          ((ListInfoView) holder.itemView).showInfo(Lang.pluralBold(R.string.xMembers, controller.members.size()));
          break;
        }
      }
    }

    @Override
    public int getItemViewType (int position) {
      return position == 0 ? MemberViewHolder.TYPE_HEADER : position == controller.members.size() + 1 ? MemberViewHolder.TYPE_INFO : MemberViewHolder.TYPE_USER;
    }

    @Override
    public int getItemCount () {
      int size = controller.members == null ? 0 : controller.members.size();
      return size == 0 ? 0 : size + 2;
    }
  }

  private static class MemberViewHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_USER = 0;
    public static final int TYPE_INFO = 1;
    public static final int TYPE_HEADER = 2;

    public MemberViewHolder (View itemView) {
      super(itemView);
    }

    public void attachReceiver () {
      if (getItemViewType() == TYPE_USER) {
        ((UserView) itemView).attachReceiver();
      }
    }

    public void detachReceiver () {
      if (getItemViewType() == TYPE_USER) {
        ((UserView) itemView).detachReceiver();
      }
    }
  }

  // Popup

  private TGUser pickedUser;

  private void onUserClick (TGUser user) {
    pickedUser = user;
    showOptions(null, new int[] {R.id.btn_deleteMember, R.id.btn_cancel}, new String[] {Lang.getString(R.string.GroupDontAdd), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24});
  }

  @Override
  public boolean onOptionItemPressed (View optionItemView, int id) {
    if (id == R.id.btn_deleteMember) {
      if (pickedUser != null) {
        long userId = pickedUser.getUserId();
        int index = indexOfUser(userId);
        if (index != -1) {
          tdlib.cache().unsubscribeFromUserUpdates(userId, this);
          members.remove(index);
          if (members.isEmpty()) {
            adapter.notifyItemRangeRemoved(0, 3);
            Keyboard.hide(headerCell.getInputView());
            navigateBack();
          } else {
            adapter.notifyItemRemoved(index + 1);
            adapter.notifyItemChanged(members.size() + 1);
          }
        }
      }
    } else {
      tdlib.ui().handlePhotoOption(context, id, null, headerCell);
    }
    return true;
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_OK) {
      tdlib.ui().handlePhotoChange(requestCode, data, headerCell);
    }
  }

  @Override
  public boolean allowPopupInterruption () {
    return members.size() > 0;
  }

  // Creation

  @Override
  protected void onFloatingButtonPressed () {
    createGroup();
  }

  private boolean isCreating;
  private String currentPhoto;
  private long[] currentMemberIds;
  private boolean currentIsChannel;

  public interface Callback {
    boolean onGroupCreated (CreateGroupController context, TdApi.Chat chat);
    default boolean forceSupergroupChat () { return false; }
  }

  private Callback groupCreationCallback;

  public void setGroupCreationCallback (Callback callback) {
    this.groupCreationCallback = callback;
  }

  public void createGroup () {
    if (isCreating) {
      return;
    }

    if (!isReady) {
      UI.showToast(R.string.GroupEnterValidName, Toast.LENGTH_SHORT);
      return;
    }

    headerCell.setInputEnabled(false);
    isCreating = true;
    currentPhoto = headerCell.getPhoto();

    String title = headerCell.getInput();

    this.currentMemberIds = new long[members.size()];
    int i = 0;
    for (TGUser member : members) {
      currentMemberIds[i++] = member.getUserId();
    }

    currentIsChannel = currentMemberIds.length > tdlib.basicGroupMaxSize();

    if (currentIsChannel) {
      tdlib.client().send(new TdApi.CreateNewSupergroupChat(title, false, false, null, null, 0, false), this);
    } else if (groupCreationCallback != null && groupCreationCallback.forceSupergroupChat()) {
      tdlib.client().send(new TdApi.CreateNewSupergroupChat(title, false, false, null, null, 0, false), result -> {
        switch (result.getConstructor()) {
          case TdApi.Chat.CONSTRUCTOR: {
            TdApi.Chat createdGroup = (TdApi.Chat) result;
            tdlib.client().send(new TdApi.AddChatMembers(createdGroup.id, currentMemberIds), addResult -> {
              tdlib.okHandler().onResult(addResult);
              CreateGroupController.this.onResult(result);
            });
            break;
          }
          case TdApi.Error.CONSTRUCTOR:
            CreateGroupController.this.onResult(result);
            break;
        }
      });
    } else {
      tdlib.client().send(new TdApi.CreateNewBasicGroupChat(currentMemberIds, title, 0), this);
    }
  }

  @Override
  public void unlock () {
    isCreating = false;
    headerCell.setInputEnabled(true);
  }

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.Ok.CONSTRUCTOR: {
        // OK
        break;
      }
      case TdApi.Chat.CONSTRUCTOR: {
        final long chatId = TD.getChatId(object);
        if (currentIsChannel) {
          tdlib.client().send(new TdApi.AddChatMembers(chatId, currentMemberIds), this);
        }
        if (currentPhoto != null) {
          tdlib.client().send(new TdApi.SetChatPhoto(chatId, new TdApi.InputChatPhotoStatic(new TdApi.InputFileGenerated(currentPhoto, SimpleGenerationInfo.makeConversion(currentPhoto), 0))), this);
        }
        tdlib.ui().post(() -> {
          if (groupCreationCallback == null || !groupCreationCallback.onGroupCreated(this, (TdApi.Chat) object)) {
            tdlib.ui().openChat(this, chatId, null);
          }
        });
        UI.unlock(this);
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        UI.showError(object);
        UI.unlock(this);
        break;
      }
    }
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (adapter != null) {
      adapter.notifyDataSetChanged();
    }
  }

  // Setup

  private boolean isReady;

  @Override
  public void onReadyStateChanged (boolean ready) {
    isReady = ready;
  }

  @Override
  public int getId () {
    return R.id.controller_newGroup;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected int getHeaderHeight () {
    return Size.getHeaderBigPortraitSize(false);
  }

  @Override
  protected int getFloatingButtonId () {
    return R.drawable.baseline_check_24;
  }
}
