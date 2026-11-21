package com.android.internal.lang;

/**
 * layoutlib's build sends the framework's System.nanoTime, currentTimeMillis,
 * loadLibrary and log calls here so Android Studio can freeze time. A Venture 2D
 * process runs on real time; the framework's own JNI libraries are already in
 * libandroid_runtime, and an app's libraries load through Venture's linker.
 */
public final class System_Delegate {
  private System_Delegate() {}

  public static long nanoTime() { return System.nanoTime(); }
  public static long currentTimeMillis() { return System.currentTimeMillis(); }
  public static void loadLibrary(String name) {}
  public static void log(String message) { android.util.Log.i("System", message); }
  public static void log(String message, Throwable failure) { android.util.Log.w("System", message, failure); }
  public static void setNanosTime(long nanos) {}
  public static void setBootTimeNanos(long nanos) {}
}
