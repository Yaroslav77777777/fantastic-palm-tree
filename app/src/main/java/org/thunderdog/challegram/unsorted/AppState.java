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
 * File created on 11/04/2017
 */
package org.thunderdog.challegram.unsorted;

import android.os.Build;
import android.os.Process;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.drinkmore.Tracer;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationUtils;
import org.thunderdog.challegram.util.Crash;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AppState {
  private static final AtomicBoolean isInitialized = new AtomicBoolean(false);

  private static void onInitializationAlreadyCompleted () {
    TdlibManager.instance().watchDog().letsHelpDoge();
  }

  private static final AtomicLong startupTime = new AtomicLong(SystemClock.uptimeMillis());

  public static long uptime () {
    final long startedAt = startupTime.get();
    return startedAt != 0 ? SystemClock.uptimeMillis() - startedAt : 0;
  }

  public static void resetUptime () {
    startupTime.set(SystemClock.uptimeMillis());
  }

  public static synchronized void initApplication () {
    // check if already initialized

    if (isInitialized.get()) {
      onInitializationAlreadyCompleted();
      return;
    }

    // initialization

    CrashManager.instance().register();

    long startStep = SystemClock.uptimeMillis();

    N.init();
    Settings.instance();
    TdlibNotificationUtils.initialize();

    if (BuildConfig.DEBUG || BuildConfig.EXPERIMENTAL) {
      Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
      AtomicBoolean isCrashing = new AtomicBoolean(false);
      Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException (@NonNull Thread thread, @NonNull Throwable error) {
          if (isCrashing.getAndSet(true)) {
            return;
          }
          error.printStackTrace();
          Settings.instance().storeCrash(new Crash.Builder("Uncaught exception!", thread, error));
          isCrashing.set(false);
          if (defaultUncaughtExceptionHandler != null) {
            Thread.setDefaultUncaughtExceptionHandler(defaultUncaughtExceptionHandler);
            defaultUncaughtExceptionHandler.uncaughtException(thread, error);
            Thread.setDefaultUncaughtExceptionHandler(this);
          } else {
            Process.killProcess(Process.myPid());
            System.exit(10);
          }
        }
      });
    }

    boolean needMeasure = Log.needMeasureLaunchSpeed();

    try {
      if (BuildConfig.DEBUG)
        Test.executeBeforeAppInit();
      if (needMeasure) {
        Log.i("==== INITIALIZATION STARTED IN %dMS ===\nManufacturer: %s, Product: %s", SystemClock.uptimeMillis() - startStep, Build.MANUFACTURER, Build.PRODUCT);
        startStep = SystemClock.uptimeMillis();
      }
      TdlibManager.instance();
      if (needMeasure) {
        Log.i("==== INITIALIZATION FINISHED IN %dms ===", SystemClock.uptimeMillis() - startStep);
      }
    } catch (Throwable t) {
      Tracer.onLaunchError(t);
      Log.e("App initialization failed", t);
      return;
    }

    isInitialized.set(true);

    // after

    if (BuildConfig.DEBUG) {
      Test.executeAfterAppInit();
    }
  }

  public static void ensureReady () {
    if (!isInitialized.get()) {
      try {
        throw new AssertionError("Trying to do something before application initialization. Log: \n" + NLoader.instance().collectLog());
      } catch (AssertionError e) {
        Tracer.onLaunchError(e);
      }
    }
  }
}
