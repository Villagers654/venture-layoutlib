package android.content.res;

import android.util.ArraySet;
import com.venture.runtime.layoutlib.Layoutlib;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

/**
 * The system assets are layoutlib's framework-res.apk beside it, not
 * /system/framework, and a 2D process has no runtime resource overlays
 * (AOSP's version also asks OverlayConfig for the partitions' overlays).
 */
public final class AssetManager_Delegate {
  private AssetManager_Delegate() {}

  static void createSystemAssetsInZygoteLocked(boolean reinitialize, String path) {
    try {
      Field system = AssetManager.class.getDeclaredField("sSystem");
      system.setAccessible(true);
      if (system.get(null) != null && !reinitialize) return;
      ApkAssets[] assets = {ApkAssets.loadFromPath(Layoutlib.frameworkResources(), ApkAssets.PROPERTY_SYSTEM)};
      Field set = AssetManager.class.getDeclaredField("sSystemApkAssetsSet");
      set.setAccessible(true);
      set.set(null, new ArraySet<>(List.of(assets)));
      Field array = AssetManager.class.getDeclaredField("sSystemApkAssets");
      array.setAccessible(true);
      array.set(null, assets);
      if (system.get(null) == null) {
        Constructor<AssetManager> sentinel = AssetManager.class.getDeclaredConstructor(boolean.class);
        sentinel.setAccessible(true);
        system.set(null, sentinel.newInstance(true));
      }
      ((AssetManager) system.get(null)).setApkAssets(assets, false);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to create system AssetManager", e);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
