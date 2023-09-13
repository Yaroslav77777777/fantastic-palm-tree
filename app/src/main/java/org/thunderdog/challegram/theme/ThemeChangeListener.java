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
 * File created on 21/01/2017
 */
package org.thunderdog.challegram.theme;

import androidx.annotation.Nullable;

public interface ThemeChangeListener {
  boolean needsTempUpdates ();
  void onThemeColorsChanged (boolean areTemp, @Nullable ColorState state);
  default void onThemeChanged (ThemeDelegate fromTheme, ThemeDelegate toTheme) { }
  default void onThemeAutoNightModeChanged (int autoNightMode) { }
  default void onThemePropertyChanged (int themeId, @PropertyId int propertyId, float value, boolean isDefault) { }
}
