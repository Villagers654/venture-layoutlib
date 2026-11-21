package dalvik.system;

import com.venture.runtime.layoutlib.ArrayAddresses;

/**
 * layoutlib's VMRuntime delegate, plus the non-movable arrays and their
 * addresses that TypedArray asks for. On HotSpot an array has no stable native
 * address; as on AOSP's host JVM runtime (Ravenwood), an address names the
 * array, and the framework methods that pass one to native code run their
 * Ravenwood versions, which look the array up (layoutlib-rules.txt).
 */
public class VMRuntime_Delegate {
  static Object newUnpaddedArray(VMRuntime runtime, Class<?> type, int minLength) {
    return VMRuntimeCommonHelper.newUnpaddedArray(runtime, type, minLength);
  }

  static int getNotifyNativeInterval() { return VMRuntimeCommonHelper.getNotifyNativeInterval(); }

  public static boolean is64Bit(VMRuntime runtime) { return true; }

  public static boolean setThreadNiceness(VMRuntime runtime, Thread thread, int niceness) { return true; }

  public static int getThreadNiceness(VMRuntime runtime, Thread thread) { return 0; }

  static Object newNonMovableArray(VMRuntime runtime, Class<?> type, int length) {
    return java.lang.reflect.Array.newInstance(type, length);
  }

  static long addressOf(VMRuntime runtime, Object array) { return ArrayAddresses.addressOf(array); }
}
