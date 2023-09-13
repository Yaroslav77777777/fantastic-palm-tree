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
 * File created on 11/05/2015 at 22:47
 */
package org.thunderdog.challegram.loader;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TD;

import java.io.File;

public class ImageFileLocal extends ImageFile {
  private static int CURRENT_ID = ImageFile.LOCAL_START_ID;

  private final String path;
  public static TdApi.File newFakeLocalFile (String path, boolean accurateSize) {
    long size = 1;
    if (accurateSize) {
      File storageFile = new File(path);
      if (storageFile.exists()) {
        size = storageFile.length();
      }
    }
    return newFakeLocalFile(path, size);
  }

  public static TdApi.File newFakeLocalFile (String path, long size) {
    return TD.newFile(CURRENT_ID--, Integer.toString(CURRENT_ID), path, size);
  }

  public ImageFileLocal (String path) {
    super(null, newFakeLocalFile(path, false));
    this.path = path;
  }

  public ImageFileLocal (TdApi.Minithumbnail minithumbnail) {
    this(minithumbnail.data, false);
  }

  public ImageFileLocal (byte[] jpeg, boolean noBlur) {
    super(null, newFakeLocalFile(null, jpeg.length), jpeg);
    String fakePath = U.base64(U.sha1(jpeg));
    this.path = noBlur ? fakePath + "_noblur" : fakePath;
    if (noBlur) {
      setNoBlur();
    }
  }

  public ImageFileLocal (ImageFileLocal copy) {
    super(null, copy.file, copy.bytes);
    this.path = copy.path;
  }

  @Override
  public int getId () {
    return path.hashCode();
  }

  @Override
  public boolean isProbablyRotated () {
    return true;
  }

  public String getPath () {
    return path;
  }

  @Override
  protected String buildImageKey () {
    return (needDecodeSquare() ? path + "?square" : path) + "_" + getSize();
  }

  @Override
  public byte getType () {
    return TYPE_LOCAL;
  }
}
