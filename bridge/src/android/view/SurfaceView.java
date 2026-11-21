package android.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import com.venture.runtime.NativeHost;
import com.venture.runtime.layoutlib.Panel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A view with its own Surface, as Android's SurfaceView: the Surface exists
 * while the view is shown in a visible window and has a size, and the app's
 * producer (EGL, Vulkan, ANativeWindow or lockCanvas) posts frames to it on
 * its own thread. The panel composites the newest frame under the window, where
 * this view leaves a hole, or over it (setZOrderOnTop), as SurfaceFlinger
 * composites a SurfaceView's layer. layoutlib's own SurfaceView only previews.
 */
public class SurfaceView extends View {
  private final Holder holder = new Holder();
  private final List<SurfaceHolder.Callback> callbacks = new CopyOnWriteArrayList<>();
  private final Object lock = new Object();
  private long window;
  private Surface surface;
  private int requestedWidth = -1, requestedHeight = -1;
  private int format = PixelFormat.RGB_565;
  private int surfaceWidth, surfaceHeight;
  private boolean creating, onTop, mediaOverlay;
  private Panel panel;
  // The newest frame the producer posted, for the panel (main thread).
  private Bitmap frame;
  private int[] framePixels;
  private long frameSerial;
  // lockCanvas: a software buffer posted on unlockCanvasAndPost.
  private Bitmap canvasBuffer;
  private Canvas canvas;
  private int[] canvasPixels;

  public SurfaceView(Context context) { this(context, null); }
  public SurfaceView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
  public SurfaceView(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }
  public SurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    setWillNotDraw(false);
  }

  public SurfaceHolder getHolder() { return holder; }

  public void setZOrderOnTop(boolean top) {
    if (onTop == top) return;
    onTop = top;
    invalidate();
  }

  public void setZOrderMediaOverlay(boolean overlay) { mediaOverlay = overlay; }

  public boolean isZOrderedOnTop() { return onTop; }

  /** @hide Whether the order changed; AOSP's second argument applies it in a transaction. */
  public boolean setZOrderedOnTop(boolean top, boolean allowDynamicChange) {
    if (onTop == top) return false;
    setZOrderOnTop(top);
    return true;
  }

  /** @hide */
  public void setUseAlpha() {}

  /** @hide */
  public void setEnableSurfaceClipping(boolean enabled) {}

  public void setSecure(boolean secure) {}

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    updateSurface();
  }

  @Override
  protected void onDetachedFromWindow() {
    destroySurface();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    updateSurface();
  }

  @Override
  public void onVisibilityAggregated(boolean isVisible) {
    super.onVisibilityAggregated(isVisible);
    post(this::updateSurface);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    updateSurface();
  }

  @Override
  public void draw(Canvas canvas) {
    // Below its window, the Surface shows through the hole this view leaves.
    if (!onTop && surface != null) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    super.draw(canvas);
  }

  private void updateSurface() {
    boolean shown = isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown();
    int width = requestedWidth > 0 ? requestedWidth : getWidth();
    int height = requestedHeight > 0 ? requestedHeight : getHeight();
    if (!shown || width <= 0 || height <= 0) {
      destroySurface();
      return;
    }
    boolean created = surface == null;
    boolean changed = created || width != surfaceWidth || height != surfaceHeight;
    if (created) {
      long handle = NativeHost.createPanelSurface(width, height);
      synchronized (lock) {
        window = handle;
        surface = Surface.ventureAdopt(handle);
      }
      Display display = getDisplay();
      panel = Panel.forDisplay(display != null ? display.getDisplayId() : Display.DEFAULT_DISPLAY);
      if (panel != null) panel.addSurface(this);
      invalidate();
    } else if (changed) {
      NativeHost.resizeSurface(window, width, height);
    }
    if (!changed) return;
    surfaceWidth = width;
    surfaceHeight = height;
    creating = created;
    try {
      if (created) for (SurfaceHolder.Callback callback : callbacks) callback.surfaceCreated(holder);
      for (SurfaceHolder.Callback callback : callbacks) callback.surfaceChanged(holder, format, width, height);
      for (SurfaceHolder.Callback callback : callbacks)
        if (callback instanceof SurfaceHolder.Callback2 redraw) redraw.surfaceRedrawNeeded(holder);
    } finally {
      creating = false;
    }
  }

  private void destroySurface() {
    if (surface == null) return;
    try {
      for (SurfaceHolder.Callback callback : callbacks) callback.surfaceDestroyed(holder);
    } finally {
      if (panel != null) panel.removeSurface(this);
      panel = null;
      Surface released;
      synchronized (lock) {
        released = surface;
        surface = null;
        window = 0;
      }
      released.release();
      frame = null;
      frameSerial = 0;
      surfaceWidth = surfaceHeight = 0;
      invalidate();
    }
  }

  /** Takes the producer's newest frame, if it posted one since the last; the panel's main thread polls. */
  public boolean ventureFetchFrame() {
    long handle = window;
    if (handle == 0) return false;
    int[] data = NativeHost.surfaceFrame(handle, frameSerial);
    if (data == null) return false;
    int width = data[0], height = data[1];
    frameSerial = (data[2] & 0xffffffffL) | ((long) data[3] << 32);
    if (frame == null || frame.getWidth() != width || frame.getHeight() != height) {
      frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      framePixels = null;
    }
    // An opaque Surface ignores the alpha a producer leaves (an EGL clear to 0).
    if (PixelFormat.formatHasAlpha(format)) {
      frame.setPixels(data, 4, width, 0, 0, width, height);
    } else {
      if (framePixels == null) framePixels = new int[width * height];
      for (int i = 0; i < framePixels.length; ++i) framePixels[i] = data[4 + i] | 0xff000000;
      frame.setPixels(framePixels, 0, width, 0, 0, width, height);
    }
    return true;
  }

  /** The newest frame for the panel to composite, or null before the first. */
  public Bitmap ventureFrame() { return frame; }

  private final class Holder implements SurfaceHolder {
    @Override public void addCallback(Callback callback) {
      if (callback != null && !callbacks.contains(callback)) callbacks.add(callback);
    }
    @Override public void removeCallback(Callback callback) { callbacks.remove(callback); }
    @Override public boolean isCreating() { return creating; }
    @Override public void setType(int type) {}
    @Override public void setFixedSize(int width, int height) {
      if (requestedWidth == width && requestedHeight == height) return;
      requestedWidth = width;
      requestedHeight = height;
      post(SurfaceView.this::updateSurface);
    }
    @Override public void setSizeFromLayout() {
      requestedWidth = requestedHeight = -1;
      post(SurfaceView.this::updateSurface);
    }
    @Override public void setFormat(int format) {
      // OPAQUE resolves to RGB_565 as on Android; the window's frames stay RGBA either way.
      SurfaceView.this.format = format == PixelFormat.OPAQUE ? PixelFormat.RGB_565 : format;
    }
    @Override public void setKeepScreenOn(boolean screenOn) { SurfaceView.this.setKeepScreenOn(screenOn); }
    @Override public Surface getSurface() { synchronized (lock) { return surface; } }
    @Override public Rect getSurfaceFrame() { return new Rect(0, 0, surfaceWidth, surfaceHeight); }
    @Override public Canvas lockCanvas() { return lockCanvas(null); }
    @Override public Canvas lockCanvas(Rect dirty) {
      int width = surfaceWidth, height = surfaceHeight;
      if (window == 0 || width <= 0 || height <= 0) return null;
      if (canvasBuffer == null || canvasBuffer.getWidth() != width || canvasBuffer.getHeight() != height) {
        canvasBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(canvasBuffer);
        canvasPixels = new int[width * height];
      }
      return canvas;
    }
    @Override public void unlockCanvasAndPost(Canvas posted) {
      if (posted != canvas || canvasBuffer == null) throw new IllegalArgumentException("canvas object must be the same instance that was previously returned by lockCanvas");
      long handle = window;
      if (handle == 0) return;
      int width = canvasBuffer.getWidth(), height = canvasBuffer.getHeight();
      canvasBuffer.getPixels(canvasPixels, 0, width, 0, 0, width, height);
      NativeHost.publishSurfacePixels(handle, width, height, canvasPixels);
    }
  }
}
