package android.os;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The system services a 2D process's framework code reaches over Binder. There
 * is no service manager on a desktop: Venture registers in-process binders for
 * the services it serves (com.venture.runtime.layoutlib.SystemServices), and a
 * lookup of any other service finds none, as layoutlib's own class does.
 */
public final class ServiceManager {
  private static final Map<String, IBinder> services = new ConcurrentHashMap<>();

  private ServiceManager() {}

  public static IBinder getService(String name) { return name == null ? null : services.get(name); }

  public static IBinder getServiceOrThrow(String name) throws ServiceNotFoundException {
    IBinder binder = getService(name);
    if (binder == null) throw new ServiceNotFoundException(name);
    return binder;
  }

  public static IBinder checkService(String name) { return getService(name); }

  public static IBinder waitForService(String name) { return getService(name); }

  public static IBinder waitForDeclaredService(String name) { return getService(name); }

  public static boolean isDeclared(String name) { return services.containsKey(name); }

  public static void addService(String name, IBinder service) { services.put(name, service); }

  public static void addService(String name, IBinder service, boolean allowIsolated) { addService(name, service); }

  public static void addService(String name, IBinder service, boolean allowIsolated, int dumpPriority) {
    addService(name, service);
  }

  public static String[] listServices() { return services.keySet().toArray(new String[0]); }

  public static void initServiceCache(Map<String, IBinder> cache) { services.putAll(cache); }

  public static class ServiceNotFoundException extends Exception {
    public ServiceNotFoundException(String name) {
      super("No service published for: " + name);
    }
  }
}
