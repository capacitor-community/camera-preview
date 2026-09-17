package com.ahm.capacitor.camera.preview;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;
import java.util.List;

class Preview extends RelativeLayout implements SurfaceHolder.Callback, TextureView.SurfaceTextureListener {

    private final String TAG = "Preview";

    /**
     * Notified about the readiness of the native preview. Implemented by {@link CameraActivity},
     * which routes the events to the Capacitor call that is waiting for them.
     *
     * <p>Every readiness event names the preview session it belongs to. The listener uses that id
     * to decide whether the event settles the pending {@code start()} call, the pending
     * {@code flip()} call, or nothing at all - see {@link PreviewOperationRouter}.
     */
    interface PreviewStateListener {
        /** The camera delivered the first preview frame of {@code sessionId}. */
        void onPreviewReady(long sessionId);

        /** The preview of {@code sessionId} could not be started, or stopped being usable. */
        void onPreviewStartFailed(long sessionId, String message);

        /**
         * The output target backing the running preview was destroyed. A still capture accepted
         * against that output can never complete and must be aborted by the listener.
         */
        void onPreviewOutputLost();
    }

    CustomSurfaceView mSurfaceView;
    CustomTextureView mTextureView;
    SurfaceHolder mHolder;
    SurfaceTexture mSurface;
    Camera.Size mPreviewSize;
    List<Camera.Size> mSupportedPreviewSizes;
    Camera mCamera;
    int cameraId;
    int displayOrientation;
    int facing = Camera.CameraInfo.CAMERA_FACING_BACK;
    int viewWidth;
    int viewHeight;
    private boolean enableOpacity = false;
    private float opacity = 1F;

    /**
     * Reconciles "camera attached" against "output target available", which can arrive in either
     * order, and tracks what counts as preview readiness. See issue #424.
     */
    private final PreviewSessionCoordinator coordinator = new PreviewSessionCoordinator();

    private PreviewStateListener stateListener;

    /** Size of the current output target, 0 while it is unknown. */
    private int outputWidth;
    private int outputHeight;

    /** Output size the running preview was configured for, 0 while no preview is configured. */
    private int configuredWidth;
    private int configuredHeight;

    Preview(Context context) {
        this(context, false);
    }

    Preview(Context context, boolean enableOpacity) {
        super(context);
        this.enableOpacity = enableOpacity;
        if (!enableOpacity) {
            mSurfaceView = new CustomSurfaceView(context);
            addView(mSurfaceView);
            requestLayout();

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = mSurfaceView.getHolder();
            mHolder.addCallback(this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        } else {
            // Use a TextureView so we can manage opacity
            mTextureView = new CustomTextureView(context);
            // Install a SurfaceTextureListener so we get notified
            mTextureView.setSurfaceTextureListener(this);
            mTextureView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            addView(mTextureView);
            requestLayout();
        }
    }

    void setStateListener(PreviewStateListener listener) {
        this.stateListener = listener;
    }

    /**
     * Starts a new preview lifecycle. Callbacks registered by the previous one become stale and
     * can no longer report readiness or failure.
     *
     * @return the id of the new session
     */
    long beginSession() {
        long session = coordinator.beginSession();
        configuredWidth = 0;
        configuredHeight = 0;
        // The output target may already exist (resume, camera switch); re-declare it so the
        // reconciliation does not wait for a callback that has already fired.
        if (isOutputTargetAvailable()) {
            coordinator.onOutputAvailable();
        }
        Log.d(TAG, "preview session " + session + " begun; " + coordinator);
        return session;
    }

    long getSessionId() {
        return coordinator.getSessionId();
    }

    /** @return true only when the current session has delivered a first preview frame. */
    boolean isPreviewReady() {
        return coordinator.isReady();
    }

    /**
     * Attaches an opened camera. Passing null detaches the current camera; see
     * {@link #detachCamera()}.
     *
     * <p>This is one of the two sides of the startup race: whichever of the camera and the output
     * target arrives last triggers the configure/start work.
     */
    public void setCamera(Camera camera, int cameraId) {
        if (camera == null) {
            detachCamera();
            return;
        }

        mCamera = camera;
        this.cameraId = cameraId;

        final long session = coordinator.getSessionId();
        try {
            Camera.Parameters params = camera.getParameters();
            mSupportedPreviewSizes = params.getSupportedPreviewSizes();
            setCameraDisplayOrientation();

            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            }
            camera.setParameters(params);
        } catch (Exception exception) {
            notifyStartFailure(session, "failed to configure the camera: " + CaptureCoordinator.describe(exception));
            return;
        }

        Log.d(TAG, "camera attached to preview; " + coordinator);
        if (coordinator.onCameraAttached()) {
            startPreviewInternal();
        }
    }

    /**
     * Detaches the current camera. The caller remains responsible for releasing it. Readiness is
     * cleared immediately so no capture can be attempted against a camera that is going away.
     */
    void detachCamera() {
        Camera camera = mCamera;
        mCamera = null;
        mPreviewSize = null;
        mSupportedPreviewSizes = null;
        configuredWidth = 0;
        configuredHeight = 0;
        coordinator.onCameraDetached();

        if (camera != null) {
            try {
                // Drops any pending one-shot readiness callback for the detached camera.
                camera.setPreviewCallback(null);
            } catch (Exception exception) {
                Log.w(TAG, "failed to clear the preview callback while detaching: " + CaptureCoordinator.describe(exception));
            }
        }
        Log.d(TAG, "camera detached from preview; " + coordinator);
    }

    /** Reports a startup failure for {@code session}; announced at most once per session. */
    void failStartup(long session, String message) {
        notifyStartFailure(session, message);
    }

    /** Reports that the first preview frame never arrived within {@code timeoutMs}. */
    void notifyStartupTimeout(long session, long timeoutMs) {
        if (!coordinator.onTimeout(session)) {
            return;
        }
        String message = "camera preview did not deliver a first frame within " + timeoutMs + "ms";
        Log.e(TAG, "session " + session + ": " + message);
        PreviewStateListener listener = stateListener;
        if (listener != null) {
            listener.onPreviewStartFailed(session, message);
        }
    }

    /**
     * Camera1 stops the preview when a picture is taken, so it has to be restarted once the JPEG
     * has been delivered.
     *
     * <p>The outcome is <em>returned</em> rather than announced. The pending start call was
     * resolved long ago, so routing a restart failure through the startup-failure path would reach
     * no one - or, worse, would settle an unrelated pending operation. The failure belongs to the
     * capture that caused it, and the caller rejects that capture with the returned message
     * (issue #424 corrective pass).
     *
     * <p>A failed restart also marks the session unusable, so {@code isCameraStarted()} reports
     * false and later captures are refused rather than reaching a dead preview.
     *
     * @param expectedCamera the camera the completed capture belongs to; the restart is skipped
     *                       when it is no longer the active camera, so a late callback can never
     *                       restart a replacement camera
     * @return null when the preview was restarted (or there was nothing to restart), otherwise the
     *         failure message
     */
    String restartPreviewAfterCapture(Camera expectedCamera) {
        Camera camera = mCamera;
        if (camera == null) {
            Log.d(TAG, "skipping the post-capture preview restart: no active camera");
            return null;
        }
        if (expectedCamera != null && camera != expectedCamera) {
            Log.d(TAG, "skipping the post-capture preview restart: the capture's camera is no longer active");
            return null;
        }

        final long session = coordinator.getSessionId();
        try {
            camera.startPreview();
            return null;
        } catch (Exception exception) {
            String message = "failed to restart the camera preview after capture: " + CaptureCoordinator.describe(exception);
            coordinator.markFailed(session);
            Log.e(TAG, "session " + session + ": " + message);
            return message;
        }
    }

    public int getDisplayOrientation() {
        return displayOrientation;
    }

    public int getCameraFacing() {
        return facing;
    }

    public void printPreviewSize(String from) {
        if (mPreviewSize == null) {
            Log.d(TAG, "printPreviewSize from " + from + ": > no preview size selected yet");
            return;
        }
        Log.d(TAG, "printPreviewSize from " + from + ": > width: " + mPreviewSize.width + " height: " + mPreviewSize.height);
    }

    public void setCameraPreviewSize() {
        if (mCamera != null && mPreviewSize != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            mCamera.setParameters(parameters);
        }
    }

    public void setCameraDisplayOrientation() {
        if (mCamera == null) {
            return;
        }

        Camera.CameraInfo info = new Camera.CameraInfo();
        int rotation = ((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        DisplayMetrics dm = new DisplayMetrics();

        Camera.getCameraInfo(cameraId, info);
        ((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(dm);

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        facing = info.facing;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (info.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (info.orientation - degrees + 360) % 360;
        }

        Log.d(TAG, "screen is rotated " + degrees + "deg from natural");
        Log.d(
            TAG,
            (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back") +
                " camera is oriented -" +
                info.orientation +
                "deg from natural"
        );
        Log.d(TAG, "need to rotate preview " + displayOrientation + "deg");
        mCamera.setDisplayOrientation(displayOrientation);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);

        if (mSupportedPreviewSizes != null) {
            Camera.Size optimalSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
            if (optimalSize != null) {
                mPreviewSize = optimalSize;
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            int width = r - l;
            int height = b - t;

            int previewWidth = width;
            int previewHeight = height;

            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;

                if (displayOrientation == 90 || displayOrientation == 270) {
                    previewWidth = mPreviewSize.height;
                    previewHeight = mPreviewSize.width;
                }
                //        LOG.d(TAG, "previewWidth:" + previewWidth + " previewHeight:" + previewHeight);
            }

            int nW;
            int nH;
            int top;
            int left;

            float scale = 1.0f;

            // Center the child SurfaceView within the parent.
            if (width * previewHeight < height * previewWidth) {
                Log.d(TAG, "center horizontally");
                int scaledChildWidth = (int) (((previewWidth * height) / previewHeight) * scale);
                nW = (width + scaledChildWidth) / 2;
                nH = (int) (height * scale);
                top = 0;
                left = (width - scaledChildWidth) / 2;
            } else {
                Log.d(TAG, "center vertically");
                int scaledChildHeight = (int) (((previewHeight * width) / previewWidth) * scale);
                nW = (int) (width * scale);
                nH = (height + scaledChildHeight) / 2;
                top = (height - scaledChildHeight) / 2;
                left = 0;
            }
            child.layout(left, top, nW, nH);

            Log.d("layout", "left:" + left);
            Log.d("layout", "top:" + top);
            Log.d("layout", "right:" + nW);
            Log.d("layout", "bottom:" + nH);
        }
    }

    //  SurfaceView callbacks

    public void surfaceCreated(SurfaceHolder holder) {
        mHolder = holder;
        if (mSurfaceView != null) {
            mSurfaceView.setWillNotDraw(false);
        }
        // surfaceCreated carries no size; surfaceChanged always follows with the real one. Only
        // declare the output usable once a size is known, otherwise no preview size can be chosen.
        handleOutputAvailable(mSurfaceView != null ? mSurfaceView.getWidth() : 0, mSurfaceView != null ? mSurfaceView.getHeight() : 0);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        mHolder = holder;
        handleOutputAvailable(w, h);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        handleOutputLost();
        try {
            if (mCamera != null) {
                mCamera.stopPreview();
            }
        } catch (Exception exception) {
            Log.e(TAG, "Exception caused by surfaceDestroyed()", exception);
        }
    }

    //  TextureView callbacks

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mSurface = surface;
        if (mTextureView != null) {
            mTextureView.setAlpha(opacity);
        }
        handleOutputAvailable(width, height);
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        mSurface = surface;
        handleOutputAvailable(width, height);
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mSurface = null;
        handleOutputLost();
        try {
            if (mCamera != null) {
                mCamera.stopPreview();
            }
        } catch (Exception exception) {
            Log.e(TAG, "Exception caused by onSurfaceTextureDestroyed()", exception);
            return false;
        }
        return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    public void setOpacity(final float opacity) {
        this.opacity = opacity;
        if (enableOpacity && mTextureView != null) {
            mTextureView.setAlpha(opacity);
        }
    }

    //  Reconciliation

    /**
     * The output-target side of the startup race. Safe to call repeatedly: the preview is started
     * once per camera/output lifecycle, and only reconfigured when the size actually changes.
     */
    private void handleOutputAvailable(int width, int height) {
        if (width <= 0 || height <= 0) {
            Log.d(TAG, "output target not usable yet (" + width + "x" + height + ")");
            return;
        }

        outputWidth = width;
        outputHeight = height;

        boolean start = coordinator.onOutputAvailable();
        if (!start && (width != configuredWidth || height != configuredHeight)) {
            start = coordinator.onOutputResized();
        }

        Log.d(TAG, "output target available " + width + "x" + height + "; start=" + start + "; " + coordinator);
        if (start) {
            startPreviewInternal();
        }
    }

    private void handleOutputLost() {
        outputWidth = 0;
        outputHeight = 0;
        configuredWidth = 0;
        configuredHeight = 0;
        coordinator.onOutputLost();
        Log.d(TAG, "output target lost; " + coordinator);

        // A still capture accepted against this output can no longer complete: Camera1 will not
        // deliver its JPEG callback once the surface is gone. The listener aborts it (issue #424).
        PreviewStateListener listener = stateListener;
        if (listener != null) {
            listener.onPreviewOutputLost();
        }
    }

    private boolean isOutputTargetAvailable() {
        if (outputWidth <= 0 || outputHeight <= 0) {
            return false;
        }
        if (enableOpacity) {
            return mSurface != null;
        }
        return mHolder != null && mHolder.getSurface() != null && mHolder.getSurface().isValid();
    }

    /**
     * Binds the output target, configures the preview size and starts the preview, then arms the
     * one-shot frame callback that defines readiness. Any failure on this path is reported through
     * the state listener rather than being logged and forgotten.
     */
    private void startPreviewInternal() {
        final long session = coordinator.getSessionId();
        final Camera camera = mCamera;
        coordinator.onPreviewStarting();

        try {
            if (camera == null) {
                throw new IllegalStateException("camera was released before the preview could start");
            }

            // Camera1 refuses to rebind an output target or change the preview size while the
            // preview is running, so a reconfiguration (a resized or recreated output target)
            // must stop it first. Stopping a preview that is not running is a no-op.
            try {
                camera.stopPreview();
            } catch (Exception exception) {
                Log.w(TAG, "stopPreview() before (re)configuring: " + CaptureCoordinator.describe(exception));
            }

            if (enableOpacity) {
                if (mSurface == null) {
                    throw new IllegalStateException("no surface texture available");
                }
                mTextureView.setAlpha(opacity);
                camera.setPreviewTexture(mSurface);
            } else {
                if (mHolder == null) {
                    throw new IllegalStateException("no surface holder available");
                }
                mSurfaceView.setWillNotDraw(false);
                camera.setPreviewDisplay(mHolder);
            }

            Camera.Parameters parameters = camera.getParameters();
            mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
            Camera.Size optimalSize = getOptimalPreviewSize(mSupportedPreviewSizes, outputWidth, outputHeight);
            if (optimalSize == null) {
                throw new IllegalStateException("no supported preview size for " + outputWidth + "x" + outputHeight);
            }
            mPreviewSize = optimalSize;
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            camera.setParameters(parameters);
            requestLayout();

            // Armed before startPreview() so the very first delivered frame counts. Readiness is
            // the frame, not the return of startPreview().
            camera.setOneShotPreviewCallback((data, cam) -> onFirstPreviewFrame(session));
            camera.startPreview();

            configuredWidth = outputWidth;
            configuredHeight = outputHeight;
            coordinator.onPreviewStarted();
            Log.d(TAG, "session " + session + ": startPreview() returned, waiting for the first frame");
        } catch (Exception exception) {
            try {
                if (camera != null) {
                    camera.setOneShotPreviewCallback(null);
                }
            } catch (Exception ignored) {
                // The camera is already unusable; nothing further to clean up here.
            }
            notifyStartFailure(session, "failed to start the camera preview: " + CaptureCoordinator.describe(exception));
        }
    }

    private void onFirstPreviewFrame(long session) {
        if (!coordinator.onFirstFrame(session)) {
            Log.d(TAG, "ignoring preview frame for stale or already settled session " + session);
            return;
        }
        Log.d(TAG, "session " + session + ": first preview frame received, preview is ready");
        PreviewStateListener listener = stateListener;
        if (listener != null) {
            listener.onPreviewReady(session);
        }
    }

    private void notifyStartFailure(long session, String message) {
        if (!coordinator.onStartFailure(session)) {
            Log.w(TAG, "suppressed stale or duplicate preview failure: " + message);
            return;
        }
        Log.e(TAG, "session " + session + ": " + message);
        PreviewStateListener listener = stateListener;
        if (listener != null) {
            listener.onPreviewStartFailed(session, message);
        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;

        if (sizes == null || sizes.isEmpty() || w <= 0 || h <= 0) {
            return null;
        }

        double targetRatio = (double) w / h;
        if (displayOrientation == 90 || displayOrientation == 270) {
            targetRatio = (double) h / w;
        }

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        if (optimalSize != null) {
            Log.d(TAG, "optimal preview size: w: " + optimalSize.width + " h: " + optimalSize.height);
        }
        return optimalSize;
    }
}
