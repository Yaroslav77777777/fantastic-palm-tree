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
 * File created on 09/11/2016
 */
package org.thunderdog.challegram.data;

public class TGSwitchInline {
  private final String username;
  private final String query;

  public TGSwitchInline (String username, String query) {
    this.username = username;
    this.query = query;
  }

  public String getUsername () {
    return username;
  }

  public String getQuery () {
    return query;
  }

  @Override
  public String toString () {
    return "@" + username + " " + query;
  }
}
