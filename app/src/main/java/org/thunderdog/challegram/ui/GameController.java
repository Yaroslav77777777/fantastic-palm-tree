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
 * File created on 05/12/2016
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.v.WebViewProxy;

public class GameController extends WebkitController<GameController.Args> implements Menu, MoreDelegate {
  public static class Args {
    public long userId;
    public TdApi.Game game;
    public String username;
    public String gameUrl;
    public TdApi.Message message;
    public MessagesController ownerController;

    public Args (long userId, TdApi.Game game, String username, String gameUrl, TdApi.Message message, MessagesController ownerController) {
      this.userId = userId;
      this.game = game;
      this.username = username;
      this.gameUrl = gameUrl;
      this.message = message;
      this.ownerController = ownerController;
    }
  }

  public GameController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  /*@Override
  protected boolean useDarkMode () {
    return true;
  }

  @Override
  protected int getHeaderColor () {
    return 0xff000000;
  }*/

  @Override
  protected int getMenuId () {
    return R.id.menu_game;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_game) {
      header.addForwardButton(menu, this);
      header.addMoreButton(menu, this);
    }
  }

  @Override
  protected void onFocusStateChanged () {
    super.onFocusStateChanged();
    checkPlaying();
  }

  @Override
  public void onActivityPause () {
    super.onActivityPause();
    checkPlaying();
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    checkPlaying();
  }

  private void checkPlaying () {
    if (getArgumentsStrict().ownerController != null) {
      getArgumentsStrict().ownerController.setBroadcastAction(isFocused() && !isDestroyed() && context.getActivityState() == UI.STATE_RESUMED ? TdApi.ChatActionStartPlayingGame.CONSTRUCTOR : TdApi.ChatActionCancel.CONSTRUCTOR);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_more) {
      showMore(new int[] {R.id.btn_openLink}, new String[] {Lang.getString(R.string.OpenInExternalApp)}, 0);
    } else if (id == R.id.menu_btn_forward) {
      if (getArguments() != null) {
        Args args = getArgumentsStrict();
        ShareController c = new ShareController(context, tdlib);
        c.setArguments(new ShareController.Args(args.game, args.userId, args.message, false));
        c.show();
      }
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (id == R.id.btn_openLink) {
      if (getArguments() != null) {
        Intents.openUri(getArguments().gameUrl);
      }
    }
  }

  @SuppressLint("AddJavascriptInterface")
  @Override
  protected void onCreateWebView (DoubleHeaderView headerCell, WebView webView) {
    if (getArguments() != null) {
      headerCell.setTitle(getArguments().game.title);
      headerCell.setSubtitle(getArguments().username);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      webView.addJavascriptInterface(new WebViewProxy(this), "TelegramWebviewProxy");
    }
    if (getArguments() != null) {
      webView.loadUrl(getArguments().gameUrl);
    }
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return y < Size.getHeaderPortraitSize() || x <= Screen.dp(15f);
  }
}
