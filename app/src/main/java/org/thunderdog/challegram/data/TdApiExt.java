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
 * File created on 09/03/2017
 */
package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;

public class TdApiExt {
  public static class MessageChatEvent extends TdApi.MessageContent {
    public static final int CONSTRUCTOR = 0;

    public TdApi.ChatEvent event;
    public final boolean isFull;
    public final boolean hideDate;

    public MessageChatEvent (TdApi.ChatEvent event, boolean isFull, boolean hideDate) {
      this.event = event;
      this.isFull = isFull;
      this.hideDate = hideDate;
    }

    @Override
    public int getConstructor () {
      //noinspection WrongConstant
      return CONSTRUCTOR;
    }

    @Override
    @NonNull
    public String toString () {
      return "MessageChatEvent{" +
        "event=" + event +
        ", isFull=" + isFull +
        ", hideDate=" + hideDate +
        '}';
    }
  }
}
