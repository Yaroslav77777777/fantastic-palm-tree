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
 * File created on 11/07/2017
 */
package org.thunderdog.challegram.mediaview.paint;

import android.os.SystemClock;

public class PaintAction {
  public static final int SIMPLE_DRAWING = 0;

  private final int type;
  private final Object data;
  private final long creationTimeMs;

  public PaintAction (int type, Object data) {
    this.type = type;
    this.data = data;
    this.creationTimeMs = SystemClock.uptimeMillis();
  }

  public int getType () {
    return type;
  }

  public long getCreationTimeMs () {
    return creationTimeMs;
  }
}
