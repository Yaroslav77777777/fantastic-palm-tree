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
 * File created on 13/12/2019
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;

public interface CounterChangeListener {
  default void onChatCounterChanged (@NonNull TdApi.ChatList chatList, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) { }
  default void onMessageCounterChanged (@NonNull TdApi.ChatList chatList, int unreadCount, int unreadUnmutedCount) { }
}
