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
 * File created on 30/06/2019
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class SnackBar extends RelativeLayout {
  public interface Callback {
    void onSnackBarTransition (SnackBar v, float factor);
    default void onDestroySnackBar (SnackBar v) { }
  }

  private TextView textView;
  private TextView actionView;

  private final BoolAnimator isShowing;

  public SnackBar (Context context) {
    super(context);

    RelativeLayout.LayoutParams rp;

    rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    rp.addRule(RelativeLayout.LEFT_OF, R.id.text_title);
    rp.topMargin = rp.bottomMargin = Screen.dp(2f);

    textView = new TextView(context);
    textView.setTextColor(Theme.getColor(ColorId.snackbarUpdateText));
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    textView.setPadding(Screen.dp(12f), Screen.dp(12f), 0, Screen.dp(12f));
    textView.setLayoutParams(rp);
    addView(textView);

    rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    rp.leftMargin = rp.rightMargin = rp.topMargin = rp.bottomMargin = Screen.dp(2f);
    rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    actionView = new TextView(context);
    actionView.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(12f), Screen.dp(12f));
    actionView.setTextColor(Theme.getColor(ColorId.snackbarUpdateAction));
    actionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    actionView.setLayoutParams(rp);
    Views.setClickable(actionView);
    addView(actionView);

    ViewSupport.setThemedBackground(this, ColorId.snackbarUpdate);

    isShowing = new BoolAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        updateTranslation();
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        updateTranslation();
        if (finalFactor == 0f && !isShowing.getValue() && callback != null) {
          callback.onDestroySnackBar(SnackBar.this);
        }
      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    setOnTouchListener((v, e) -> true);
  }

  private Callback callback;

  public SnackBar setCallback (Callback callback) {
    this.callback = callback;
    return this;
  }

  public SnackBar setText (String text) {
    textView.setText(text);
    return this;
  }

  public SnackBar setAction (String action, Runnable callback, boolean dismissAutomatically) {
    Views.setMediumText(actionView, action.toUpperCase());
    actionView.setOnClickListener(v -> {
      callback.run();
      if (dismissAutomatically) {
        dismissSnackBar(true);
      }
    });
    return this;
  }

  public SnackBar showSnackBar (boolean animated) {
    isShowing.setValue(true, animated);
    return this;
  }

  public SnackBar dismissSnackBar (boolean animated) {
    isShowing.setValue(false, animated);
    return this;
  }

  public SnackBar addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(actionView, ColorId.snackbarUpdateAction);
      themeProvider.addThemeTextColorListener(textView, ColorId.snackbarUpdateText);
      themeProvider.addThemeInvalidateListener(this);
    }
    return this;
  }

  public SnackBar removeThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.removeThemeListenerByTarget(textView);
      themeProvider.removeThemeListenerByTarget(actionView);
      themeProvider.removeThemeListenerByTarget(this);
    }
    return this;
  }

  private void updateTranslation () {
    float y = getMeasuredHeight() * (1f - isShowing.getFloatValue());
    if (getTranslationY() != y || y == 0) {
      if (callback != null) {
        callback.onSnackBarTransition(this, isShowing.getFloatValue());
      }
      setTranslationY(y);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updateTranslation();
  }
}
