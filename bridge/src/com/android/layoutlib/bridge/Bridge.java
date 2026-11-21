package com.android.layoutlib.bridge;

import android.os.SystemProperties;
import android.view.InputDevice;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.venture.runtime.layoutlib.Layoutlib;
import java.util.concurrent.locks.ReentrantLock;

/**
 * What layoutlib's natives and delegates ask of Android Studio's Bridge class in
 * a Venture 2D process: the log, the system properties set while its natives
 * load (JNI_OnLoad calls setSystemProperties and setInputManager), and the lock
 * its delegates take. Venture runs no render sessions.
 */
public final class Bridge {
  private static final ReentrantLock lock = new ReentrantLock();

  private Bridge() {}

  public static ILayoutLog getLog() { return Layoutlib.LOG; }

  public static ReentrantLock getLock() { return lock; }

  public static String getIcuDataPath() { return Layoutlib.property("ro.icu.data.path"); }

  public static boolean isLocaleRtl(String locale) {
    return android.text.TextUtils.getLayoutDirectionFromLocale(java.util.Locale.forLanguageTag(locale))
        == android.view.View.LAYOUT_DIRECTION_RTL;
  }

  private static void setSystemProperties() {
    for (var entry : Layoutlib.properties().entrySet()) SystemProperties.set(entry.getKey(), entry.getValue());
  }

  private static void setInputManager(InputDevice[] devices) {
    Layoutlib.setInputDevices(devices);
  }
}
