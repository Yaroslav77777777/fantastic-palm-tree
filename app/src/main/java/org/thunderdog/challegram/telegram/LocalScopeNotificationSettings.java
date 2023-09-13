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
 * File created on 20/03/2019
 */
package org.thunderdog.challegram.telegram;

import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.core.StringUtils;

public class LocalScopeNotificationSettings {
  public final int accountId;
  public final TdApi.NotificationSettingsScope scope;

  @Nullable
  private Long _channelVersion;

  @Nullable
  private Integer _vibrateMode;
  @Nullable
  private Boolean _vibrateOnlyIfSilent;

  @Nullable
  private String _sound, _soundName, _soundPath;
  private boolean soundLoaded, soundNameLoaded, soundPathLoaded;

  @Nullable
  private Boolean _contentPreview;

  @Nullable
  private Integer _ledColor;
  @Nullable
  private Integer _priorityOrImportance;

  public LocalScopeNotificationSettings (int accountId, TdApi.NotificationSettingsScope scope) {
    this.accountId = accountId;
    this.scope = scope;
  }

  public void resetToDefault (SharedPreferences.Editor editor) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      editor.remove(prefix(TdlibNotificationManager.KEY_PREFIX_CHANNEL_VERSION));
    }
    editor
      .remove(suffix(TdlibNotificationManager.KEY_SUFFIX_SOUND_NAME))
      .remove(suffix(TdlibNotificationManager.KEY_SUFFIX_SOUND_NAME))
      .remove(suffix(TdlibNotificationManager.KEY_SUFFIX_VIBRATE_ONLYSILENT))
      .remove(suffix(TdlibNotificationManager.KEY_SUFFIX_VIBRATE))
      .remove(suffix(TdlibNotificationManager.KEY_SUFFIX_PRIORITY_OR_IMPORTANCE_KEY))
      .remove(suffix(TdlibNotificationManager.KEY_SUFFIX_LED));

    _channelVersion = null;
    _vibrateMode = null;
    _vibrateOnlyIfSilent = null;
    _soundPath = null; _soundName = null; _sound = null;
    soundPathLoaded = false; soundNameLoaded = false; soundLoaded = false;
    _ledColor = null;
  }

  public static String getKeyPrefix (TdApi.NotificationSettingsScope scope) {
    switch (scope.getConstructor()) {
      case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR:
        return "private";
      case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR:
        return "groups";
      case TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR:
        return "channels";
    }
    throw new RuntimeException();
  }

  public String prefix (String prefix) {
    return TdlibNotificationManager.key(prefix + getKeyPrefix(scope), accountId);
  }

  public String suffix (String suffix) {
    return TdlibNotificationManager.key(getKeyPrefix(scope) + suffix, accountId);
  }

  public long getChannelVersion () {
    if (_channelVersion == null)
      _channelVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? Settings.instance().getLong(prefix(TdlibNotificationManager.KEY_PREFIX_CHANNEL_VERSION), 0) : 0;
    return _channelVersion;
  }

  public void setChannelVersion (long version) {
    this._channelVersion = version;
  }

  public void setVibrateMode (int mode, boolean onlyIfSilent) {
    this._vibrateMode = mode;
    if (Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE) {
      this._vibrateOnlyIfSilent = onlyIfSilent;
    }
  }

  int getVibrateMode () {
    if (_vibrateMode == null)
      _vibrateMode = Settings.instance().getInt(suffix(TdlibNotificationManager.KEY_SUFFIX_VIBRATE), TdlibNotificationManager.VIBRATE_MODE_DEFAULT);
    return _vibrateMode;
  }

  boolean getVibrateOnlyIfSilent () {
    if (_vibrateOnlyIfSilent == null)
      _vibrateOnlyIfSilent = Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE && Settings.instance().getBoolean(suffix(TdlibNotificationManager.KEY_SUFFIX_VIBRATE_ONLYSILENT), false);
    return _vibrateOnlyIfSilent;
  }

  @Nullable
  public String getSound () {
    if (!soundLoaded) {
      _sound = Settings.instance().getString(suffix(TdlibNotificationManager.KEY_SUFFIX_SOUND), null);
      soundLoaded = true;
    }
    return _sound;
  }

  @Nullable
  String getSoundName () {
    if (!soundNameLoaded) {
      String sound = getSound();
      _soundName = StringUtils.isEmpty(sound) ? sound : Settings.instance().getString(suffix(TdlibNotificationManager.KEY_SUFFIX_SOUND_NAME), null);
      soundNameLoaded = true;
    }
    return _soundName;
  }

  @Nullable
  String getSoundPath () {
    if (!soundPathLoaded) {
      String sound = getSound();
      _soundPath = StringUtils.isEmpty(sound) ? sound : Settings.instance().getString(suffix(TdlibNotificationManager.KEY_SUFFIX_SOUND_PATH), null);
      soundPathLoaded = true;
    }
    return _soundPath;
  }

  void setSound (String sound, String soundName, String soundPath) {
    this._sound = sound;
    this.soundLoaded = true;
    this._soundName = soundName;
    this.soundNameLoaded = true;
    this._soundPath = soundPath;
    this.soundPathLoaded = true;
  }

  void setNeedContentPreview (boolean needContentPreview) {
    this._contentPreview = needContentPreview;
  }

  boolean needContentPreview () {
    if (_contentPreview == null)
      _contentPreview = Settings.instance().getBoolean(suffix(TdlibNotificationManager.KEY_SUFFIX_CONTENT_PREVIEW), scope.getConstructor() != TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR); // disabled for group chats by default
    return _contentPreview;
  }

  int getLedColor () {
    if (_ledColor == null)
      _ledColor = Settings.instance().getInt(suffix(TdlibNotificationManager.KEY_SUFFIX_LED), TdlibNotificationManager.LED_COLOR_DEFAULT);
    return _ledColor;
  }

  void setLedColor (int color) {
    this._ledColor = color;
  }

  int getPriorityOrImportance () {
    if (_priorityOrImportance == null)
      _priorityOrImportance = Settings.instance().getInt(suffix(TdlibNotificationManager.KEY_SUFFIX_PRIORITY_OR_IMPORTANCE_KEY), TdlibNotificationManager.DEFAULT_PRIORITY_OR_IMPORTANCE);
    return _priorityOrImportance;
  }

  void setPriorityOrImportance (int priorityOrImportance) {
    this._priorityOrImportance = priorityOrImportance;
  }
}
