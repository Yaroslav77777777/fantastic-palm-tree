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
 * File created on 05/03/2023
 */
package org.thunderdog.challegram.util;

import android.text.TextPaint;
import android.text.style.ClickableSpan;

import androidx.annotation.NonNull;

public abstract class NoUnderlineClickableSpan extends ClickableSpan {
  public NoUnderlineClickableSpan () { }

  @Override
  public void updateDrawState (@NonNull TextPaint ds) {
    boolean isUnderlineText = ds.isUnderlineText();
    super.updateDrawState(ds);
    ds.setUnderlineText(isUnderlineText);
  }
}
