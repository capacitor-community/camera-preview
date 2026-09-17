package com.ahm.capacitor.camera.preview;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CameraActivity extends Fragment implements Preview.PreviewStateListener {

    public interface CameraPreviewListener {
        void onPictureTaken(String originalPicture);
        void onPictureTakenError(String message);
        void onSnapshotTaken(String originalPicture);
        void onSnapshotTakenError(String message);
        void onFocusSet(int pointX, int pointY);
        void onFocusSetError(String message);
        void onBackButton();
        void onCameraStarted();
        void onCameraStartError(String message);
        void onCameraFlipped();
        void onCameraFlipError(String message);
        void onStartRecordVideo();
        void onStartRecordVideoError(String message);
        void onStopRecordVideo(String file);
        void onStopRecordVideoError(String error);
    }

    private CameraPreviewListener eventListener;
    private static final String TAG = "CameraActivity";
    public FrameLayout mainLayout;
    public FrameLayout frameContainerLayout;

    private Preview mPreview;

    /**
     * Upper bound on how long start() or flip() waits for the first preview frame before rejecting.
     */
    static final long PREVIEW_READY_TIMEOUT_MS = 10000L;

    /**
     * Camera1 is not thread safe and its object is owned by the looper it was opened on, which is
     * the main looper. Every Camera1 call and every timing callback is serialised here.
     */
    private final Handler cameraHandler = new Handler(Looper.getMainLooper());

    /** Serialises still capture and guarantees exactly one outcome per capture request. */
    private final CaptureCoordinator captureCoordinator = new CaptureCoordinator();

    /**
     * Maps a preview session's readiness or failure back to the plugin call waiting for it, so a
     * flip cannot consume the start call and a stale session cannot settle a live operation.
     */
    private final PreviewOperationRouter operationRouter = new PreviewOperationRouter();

    private Runnable startupTimeoutRunnable;

    private boolean previewResumed = false;

    private View view;
    private Camera.Parameters cameraParameters;
    private Camera mCamera;
    private int numberOfCameras;
    private int cameraCurrentlyLocked;

    private enum RecordingState {
        INITIALIZING,
        STARTED,
        STOPPED
    }

    /**
     * Immutable identity of an accepted still capture.
     *
     * <p>Binding the JPEG callback to the capture token, the preview session and the {@code Camera}
     * instance - rather than reading whatever the CameraActivity fields happen to hold when the
     * callback fires - is what stops a late callback from settling a newer capture, processing an
     * image with a switched camera's parameters, or restarting a replacement camera (issue #424).
     */
    private static final class PendingCapture {

        final long token;
        final long sessionId;
        final Camera camera;
        final int cameraId;
        final int quality;

        PendingCapture(long token, long sessionId, Camera camera, int cameraId, int quality) {
            this.token = token;
            this.sessionId = sessionId;
            this.camera = camera;
            this.cameraId = cameraId;
            this.quality = quality;
        }
    }

    private RecordingState mRecordingState = RecordingState.INITIALIZING;
    private MediaRecorder mRecorder = null;
    private String recordFilePath;
    private float opacity;

    // The first rear facing camera
    private int defaultCameraId;
    public String defaultCamera;
    public boolean tapToTakePicture;
    public boolean dragEnabled;
    public boolean tapToFocus;
    public boolean disableExifHeaderStripping;
    public boolean storeToFile;
    public boolean toBack;
    public boolean enableOpacity = false;
    public boolean enableZoom = false;

    public int width;
    public int height;
    public int x;
    public int y;

    public void setEventListener(CameraPreviewListener listener) {
        eventListener = listener;
    }

    private String appResourcesPackage;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        appResourcesPackage = getActivity().getPackageName();

        // Inflate the layout for this fragment
        view = inflater.inflate(getResources().getIdentifier("camera_activity", "layout", appResourcesPackage), container, false);
        createCameraPreview();
        return view;
    }

    public void setRect(int x, int y, Integer width, Integer height) {
        this.x = x;
        this.y = y;
        this.width = width == null ? ViewGroup.LayoutParams.MATCH_PARENT : width;
        this.height = height == null ? ViewGroup.LayoutParams.MATCH_PARENT : height;
    }

    private void createCameraPreview() {
        if (mPreview == null) {
            setDefaultCameraId();

            //set box position and size
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
            layoutParams.setMargins(x, y, 0, 0);
            frameContainerLayout = (FrameLayout) view.findViewById(
                getResources().getIdentifier("frame_container", "id", appResourcesPackage)
            );
            frameContainerLayout.setLayoutParams(layoutParams);

            //video view
            mPreview = new Preview(getActivity(), enableOpacity);
            mPreview.setStateListener(this);
            mainLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("video_view", "id", appResourcesPackage));
            mainLayout.setLayoutParams(
                new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
            );
            mainLayout.addView(mPreview);
            mainLayout.setEnabled(false);

            if (enableZoom) {
                this.setupTouchAndBackButton();
            }
        }
    }

    private void setupTouchAndBackButton() {
        final GestureDetector gestureDetector = new GestureDetector(getActivity().getApplicationContext(), new TapGestureDetector());

        getActivity().runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    frameContainerLayout.setClickable(true);
                    frameContainerLayout.setOnTouchListener(
                        new View.OnTouchListener() {
                            private int mLastTouchX;
                            private int mLastTouchY;
                            private int mPosX = 0;
                            private int mPosY = 0;

                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) frameContainerLayout.getLayoutParams();

                                boolean isSingleTapTouch = gestureDetector.onTouchEvent(event);
                                int action = event.getAction();
                                int eventCount = event.getPointerCount();
                                Log.d(TAG, "onTouch event, action, count: " + event + ", " + action + ", " + eventCount);
                                if (eventCount > 1) {
                                    // handle multi-touch events
                                    Camera.Parameters params = mCamera.getParameters();
                                    if (action == MotionEvent.ACTION_POINTER_DOWN) {
                                        mDist = getFingerSpacing(event);
                                    } else if (action == MotionEvent.ACTION_MOVE && params.isZoomSupported()) {
                                        handleZoom(event, params);
                                    }
                                } else {
                                    if (action != MotionEvent.ACTION_MOVE && isSingleTapTouch) {
                                        if (tapToTakePicture && tapToFocus) {
                                            setFocusArea(
                                                (int) event.getX(0),
                                                (int) event.getY(0),
                                                new Camera.AutoFocusCallback() {
                                                    public void onAutoFocus(boolean success, Camera camera) {
                                                        if (success) {
                                                            takePicture(0, 0, 85);
                                                        } else {
                                                            Log.d(TAG, "onTouch:" + " setFocusArea() did not suceed");
                                                        }
                                                    }
                                                }
                                            );
                                        } else if (tapToTakePicture) {
                                            takePicture(0, 0, 85);
                                        } else if (tapToFocus) {
                                            setFocusArea(
                                                (int) event.getX(0),
                                                (int) event.getY(0),
                                                new Camera.AutoFocusCallback() {
                                                    public void onAutoFocus(boolean success, Camera camera) {
                                                        if (success) {
                                                            // A callback to JS might make sense here.
                                                        } else {
                                                            Log.d(TAG, "onTouch:" + " setFocusArea() did not suceed");
                                                        }
                                                    }
                                                }
                                            );
                                        }
                                        return true;
                                    } else {
                                        if (dragEnabled) {
                                            int x;
                                            int y;

                                            switch (event.getAction()) {
                                                case MotionEvent.ACTION_DOWN:
                                                    if (mLastTouchX == 0 || mLastTouchY == 0) {
                                                        mLastTouchX = (int) event.getRawX() - layoutParams.leftMargin;
                                                        mLastTouchY = (int) event.getRawY() - layoutParams.topMargin;
                                                    } else {
                                                        mLastTouchX = (int) event.getRawX();
                                                        mLastTouchY = (int) event.getRawY();
                                                    }
                                                    break;
                                                case MotionEvent.ACTION_MOVE:
                                                    x = (int) event.getRawX();
                                                    y = (int) event.getRawY();

                                                    final float dx = x - mLastTouchX;
                                                    final float dy = y - mLastTouchY;

                                                    mPosX += dx;
                                                    mPosY += dy;

                                                    layoutParams.leftMargin = mPosX;
                                                    layoutParams.topMargin = mPosY;

                                                    frameContainerLayout.setLayoutParams(layoutParams);

                                                    // Remember this touch position for the next move event
                                                    mLastTouchX = x;
                                                    mLastTouchY = y;

                                                    break;
                                                default:
                                                    break;
                                            }
                                        }
                                    }
                                }
                                return true;
                            }
                        }
                    );
                    frameContainerLayout.setFocusableInTouchMode(true);
                    frameContainerLayout.requestFocus();
                    frameContainerLayout.setOnKeyListener(
                        new View.OnKeyListener() {
                            @Override
                            public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                                    eventListener.onBackButton();
                                    return true;
                                }
                                return false;
                            }
                        }
                    );
                }

                private float mDist = 0F;

                private void handleZoom(MotionEvent event, Camera.Parameters params) {
                    if (mCamera != null) {
                        mCamera.cancelAutoFocus();
                        int maxZoom = params.getMaxZoom();
                        int zoom = params.getZoom();
                        float newDist = getFingerSpacing(event);
                        if (newDist > mDist) {
                            //zoom in
                            if (zoom < maxZoom) zoom++;
                        } else if (newDist < mDist) {
                            //zoom out
                            if (zoom > 0) zoom--;
                        }
                        mDist = newDist;
                        params.setZoom(zoom);
                        mCamera.setParameters(params);
                    }
                }
            }
        );
    }

    private void setDefaultCameraId() {
        // Find the total number of cameras available
        numberOfCameras = Camera.getNumberOfCameras();

        int facing = "front".equals(defaultCamera) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;

        // Find the ID of the default camera
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == facing) {
                defaultCameraId = i;
                break;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        previewResumed = true;

        // Defensive: nothing from a previous lifecycle may still be waiting when a new session
        // replaces it. onPause() already settles both, so this is normally a no-op.
        settleStalePendingOperations("camera preview was restarted before the operation completed");

        // A resume always begins a fresh preview lifecycle. Callbacks still queued for a previous
        // camera or output target become stale and can no longer report readiness or failure.
        final long session = mPreview.beginSession();
        operationRouter.awaitStart(session);
        scheduleStartupTimeout(session);

        try {
            mCamera = Camera.open(defaultCameraId);

            if (cameraParameters != null) {
                mCamera.setParameters(cameraParameters);
            }
        } catch (Exception exception) {
            mCamera = null;
            cancelStartupTimeout();
            mPreview.failStartup(session, "failed to open the camera: " + CaptureCoordinator.describe(exception));
            return;
        }

        cameraCurrentlyLocked = defaultCameraId;

        attachCameraToPreview(session);

        Log.d(TAG, "cameraCurrentlyLocked:" + cameraCurrentlyLocked);

        final FrameLayout frameContainerLayout = (FrameLayout) view.findViewById(
            getResources().getIdentifier("frame_container", "id", appResourcesPackage)
        );

        ViewTreeObserver viewTreeObserver = frameContainerLayout.getViewTreeObserver();

        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        frameContainerLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        frameContainerLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                        Activity activity = getActivity();
                        if (isAdded() && activity != null) {
                            final RelativeLayout frameCamContainerLayout = (RelativeLayout) view.findViewById(
                                getResources().getIdentifier("frame_camera_cont", "id", appResourcesPackage)
                            );

                            FrameLayout.LayoutParams camViewLayout = new FrameLayout.LayoutParams(
                                frameContainerLayout.getWidth(),
                                frameContainerLayout.getHeight()
                            );
                            camViewLayout.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
                            frameCamContainerLayout.setLayoutParams(camViewLayout);
                        }
                    }
                }
            );
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        previewResumed = false;
        cancelStartupTimeout();

        final long session = mPreview != null ? mPreview.getSessionId() : PreviewOperationRouter.NO_SESSION;

        // The accepted capture is settled BEFORE the camera is detached and released: once the
        // Camera object is gone its JPEG callback will never arrive (issue #424).
        abortActiveCapture("camera preview was paused before the capture completed");

        // Readiness is dropped before the camera goes away, so an in-flight capture request can no
        // longer reach a released Camera object.
        if (mPreview != null) {
            mPreview.detachCamera();
        }

        // Because the Camera object is a shared resource, it's very important to release it when the activity is paused.
        if (mCamera != null) {
            setDefaultCameraId();
            try {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
            } catch (Exception exception) {
                Log.w(TAG, "failed to stop the preview while pausing: " + CaptureCoordinator.describe(exception));
            }
            mCamera.release();
            mCamera = null;
        }

        // A start or flip that never reached its first frame must reject instead of hanging
        // forever. failStartup routes through the session id, so it settles whichever of the two
        // was waiting on this session.
        //
        // Readiness cannot decide this. During a normal stop() the container view is removed first,
        // and the resulting output loss moves an already-ready session out of READY before onPause()
        // runs, so readiness would report a start that resolved long ago as a startup failure and
        // mark the settled session failed. Only a session that still owns a pending operation has a
        // failure to report. The check is session-specific, so an operation left registered to
        // another session cannot get this one marked failed; the sweep below settles that one.
        if (mPreview != null && operationRouter.hasPendingOperationForSession(session)) {
            mPreview.failStartup(session, "camera preview was paused before the first frame arrived");
        }

        // Defensive sweep: nothing may stay pending across a pause, whatever session it waited on.
        settleStalePendingOperations("camera preview was paused before the operation completed");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        cancelStartupTimeout();
        abortActiveCapture("camera preview was destroyed before the capture completed");
        settleStalePendingOperations("camera preview was destroyed before the operation completed");
        if (mPreview != null) {
            mPreview.setStateListener(null);
        }
    }

    /**
     * The single, exactly-once capture abort path.
     *
     * <p>Every lifecycle event that invalidates an accepted still capture - pause, destruction, a
     * camera switch, the loss of the preview output - funnels through here <em>before</em> the
     * camera it belongs to is detached or released. The capture token is retired, so the JPEG
     * callback for it becomes a no-op and can neither settle the plugin call a second time nor
     * interfere with a later capture, and the coordinator is left free for that later capture.
     *
     * @return true when a capture was actually aborted
     */
    private boolean abortActiveCapture(String reason) {
        long token = captureCoordinator.abortActiveCapture();
        if (token == CaptureCoordinator.NO_CAPTURE) {
            return false;
        }
        Log.w(TAG, "aborting capture " + token + ": " + reason);
        // The plugin layer ignores this when stop() already consumed the saved call, so an explicit
        // stop followed by the fragment teardown cannot reject the same call twice.
        reportCaptureError(reason);
        return true;
    }

    /**
     * Rejects any start or flip still waiting on a preview session that can no longer become ready.
     * Safe to call repeatedly: each operation is consumed at most once.
     */
    private void settleStalePendingOperations(String reason) {
        CameraPreviewListener listener = eventListener;
        if (operationRouter.consumeFlip() && listener != null) {
            Log.w(TAG, "settling a pending flip: " + reason);
            listener.onCameraFlipError(reason);
        }
        if (operationRouter.consumeStart() && listener != null) {
            Log.w(TAG, "settling a pending start: " + reason);
            listener.onCameraStartError(reason);
        }
    }

    /**
     * Discards a flip that could not be carried out, so its session does not stay registered and
     * block every later flip with "a camera flip is already in progress".
     */
    public void discardPendingFlip(String reason) {
        CameraPreviewListener listener = eventListener;
        if (operationRouter.consumeFlip() && listener != null) {
            Log.w(TAG, "discarding a pending flip: " + reason);
            listener.onCameraFlipError(reason);
        }
    }

    /**
     * @return null when a camera flip may start, otherwise the reason it may not. A user capture in
     *         flight refuses the flip rather than being thrown away.
     */
    public String checkCanFlip() {
        return operationRouter.checkCanFlip(
            mPreview != null && mCamera != null,
            isPreviewReady(),
            previewResumed,
            captureCoordinator.isCaptureInProgress()
        );
    }

    /**
     * Attaches the opened camera to the current preview session. Ignored if the session has been
     * replaced, so stale lifecycle work cannot bind a camera to a newer preview session.
     */
    private void attachCameraToPreview(long session) {
        if (mPreview == null || mCamera == null) {
            Log.d(TAG, "skipping camera attachment: no preview or camera");
            return;
        }
        if (mPreview.getSessionId() != session) {
            Log.d(TAG, "skipping stale camera attachment for session " + session);
            return;
        }
        mPreview.setCamera(mCamera, cameraCurrentlyLocked);
    }

    private void scheduleStartupTimeout(final long session) {
        cancelStartupTimeout();
        startupTimeoutRunnable = () -> {
            startupTimeoutRunnable = null;
            if (mPreview != null) {
                mPreview.notifyStartupTimeout(session, PREVIEW_READY_TIMEOUT_MS);
            }
        };
        cameraHandler.postDelayed(startupTimeoutRunnable, PREVIEW_READY_TIMEOUT_MS);
    }

    private void cancelStartupTimeout() {
        if (startupTimeoutRunnable != null) {
            cameraHandler.removeCallbacks(startupTimeoutRunnable);
            startupTimeoutRunnable = null;
        }
    }

    /** Runs {@code runnable} on the looper that owns the Camera1 object. */
    private void runOnCameraThread(Runnable runnable) {
        if (Looper.myLooper() == cameraHandler.getLooper()) {
            runnable.run();
        } else {
            cameraHandler.post(runnable);
        }
    }

    /** @return true only when the native preview has delivered a first frame. */
    public boolean isPreviewReady() {
        return mPreview != null && mPreview.isPreviewReady();
    }

    private void reportCaptureError(String message) {
        Log.e(TAG, "capture failed: " + message);
        CameraPreviewListener listener = eventListener;
        if (listener != null) {
            listener.onPictureTakenError(message);
        }
    }

    @Override
    public void onPreviewReady(long sessionId) {
        cancelStartupTimeout();
        Log.d(TAG, "camera preview session " + sessionId + " delivered its first frame");

        PreviewOperationRouter.Operation operation = operationRouter.settle(sessionId);
        CameraPreviewListener listener = eventListener;
        if (listener == null) {
            return;
        }

        switch (operation) {
            case FLIP:
                listener.onCameraFlipped();
                break;
            case START:
                listener.onCameraStarted();
                break;
            default:
                Log.w(TAG, "preview session " + sessionId + " became ready with no operation waiting on it");
                break;
        }
    }

    @Override
    public void onPreviewStartFailed(long sessionId, String message) {
        cancelStartupTimeout();
        Log.e(TAG, "camera preview session " + sessionId + " failed: " + message);

        PreviewOperationRouter.Operation operation = operationRouter.settle(sessionId);
        CameraPreviewListener listener = eventListener;
        if (listener == null) {
            return;
        }

        switch (operation) {
            case FLIP:
                listener.onCameraFlipError(message);
                break;
            case START:
                listener.onCameraStartError(message);
                break;
            default:
                Log.w(TAG, "preview session " + sessionId + " failed with no operation waiting on it: " + message);
                break;
        }
    }

    @Override
    public void onPreviewOutputLost() {
        // Camera1 will not deliver a JPEG callback once the output surface is gone, so an accepted
        // capture would otherwise stay pending forever (issue #424).
        abortActiveCapture("camera preview output was destroyed before the capture completed");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        final FrameLayout frameContainerLayout = (FrameLayout) view.findViewById(
            getResources().getIdentifier("frame_container", "id", appResourcesPackage)
        );

        final int previousOrientation =
            frameContainerLayout.getHeight() > frameContainerLayout.getWidth()
                ? Configuration.ORIENTATION_PORTRAIT
                : Configuration.ORIENTATION_LANDSCAPE;
        // Checks if the orientation of the screen has changed
        if (newConfig.orientation != previousOrientation) {
            final RelativeLayout frameCamContainerLayout = (RelativeLayout) view.findViewById(
                getResources().getIdentifier("frame_camera_cont", "id", appResourcesPackage)
            );

            frameContainerLayout.getLayoutParams().width = frameCamContainerLayout.getHeight();
            frameContainerLayout.getLayoutParams().height = frameCamContainerLayout.getWidth();

            frameCamContainerLayout.getLayoutParams().width = frameCamContainerLayout.getHeight();
            frameCamContainerLayout.getLayoutParams().height = frameCamContainerLayout.getWidth();

            frameContainerLayout.invalidate();
            frameContainerLayout.requestLayout();

            frameCamContainerLayout.forceLayout();

            mPreview.setCameraDisplayOrientation();
        }
    }

    public Camera getCamera() {
        return mCamera;
    }

    /**
     * Method to get the front camera id if the current camera is back and visa versa
     *
     * @return front or back camera id depending on the currently active camera
     */
    private int getNextCameraId() {
        int nextCameraId = 0;

        // Find the total number of cameras available
        // NOTE: The getNumberOfCameras() method in Android's android.hardware.camera API returns the total
        // number of cameras available on the device. The number might not be limited to just the front
        // and back cameras because modern smartphones often come with more than two cameras.
        // For example, devices might have:
        // - a main (back) camera.
        // - a wide-angle camera.
        // - a telephoto camera.
        // - a depth-sensing camera.
        // - an ultrawide camera.
        // - a macro camera.
        // etc.
        numberOfCameras = Camera.getNumberOfCameras();

        int nextFacing =
            cameraCurrentlyLocked == Camera.CameraInfo.CAMERA_FACING_BACK
                ? Camera.CameraInfo.CAMERA_FACING_FRONT
                : Camera.CameraInfo.CAMERA_FACING_BACK;

        // Find the next ID of the camera to switch to (front if the current is back and visa versa)
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == nextFacing) {
                nextCameraId = i;
                break;
            }
        }
        return nextCameraId;
    }

    public void switchCamera() {
        // check for availability of multiple cameras
        if (numberOfCameras == 1) {
            // There is only one camera available, so there is nothing to switch to. flip() has
            // always resolved successfully in that case, so that behaviour is preserved.
            Log.d(TAG, "switchCamera: only one camera available, nothing to switch");
            operationRouter.consumeFlip();
            CameraPreviewListener singleCameraListener = eventListener;
            if (singleCameraListener != null) {
                singleCameraListener.onCameraFlipped();
            }
            return;
        }

        Log.d(TAG, "numberOfCameras: " + numberOfCameras);

        cancelStartupTimeout();

        // Defensive invariant: the public flip gate refuses a flip while a capture is in flight,
        // but releasing the camera underneath an accepted capture must never strand it.
        abortActiveCapture("camera was switched before the capture completed");

        // OK, we have multiple cameras. Release this camera -> cameraCurrentlyLocked
        if (mCamera != null) {
            try {
                mCamera.stopPreview();
            } catch (Exception exception) {
                Log.w(TAG, "failed to stop the preview while switching camera: " + CaptureCoordinator.describe(exception));
            }
            mPreview.detachCamera();
            mCamera.release();
            mCamera = null;
        }

        Log.d(TAG, "cameraCurrentlyLocked := " + Integer.toString(cameraCurrentlyLocked));
        try {
            cameraCurrentlyLocked = getNextCameraId();
            Log.d(TAG, "cameraCurrentlyLocked new: " + cameraCurrentlyLocked);
        } catch (Exception exception) {
            Log.d(TAG, "failed to resolve the next camera id: " + CaptureCoordinator.describe(exception));
        }

        // The switched-in camera is a new preview lifecycle with its own readiness, and it is the
        // session whose first frame resolves the pending flip() call.
        final long session = mPreview.beginSession();
        if (operationRouter.consumeStart()) {
            // Defensive: the flip gate requires a ready preview, so a start should never still be
            // waiting here. If it somehow is, its session is now gone and it must not hang.
            CameraPreviewListener staleStartListener = eventListener;
            if (staleStartListener != null) {
                staleStartListener.onCameraStartError("camera preview was replaced by a camera switch before the first frame arrived");
            }
        }
        operationRouter.awaitFlip(session);
        scheduleStartupTimeout(session);

        // Acquire the next camera and request Preview to reconfigure parameters.
        try {
            mCamera = Camera.open(cameraCurrentlyLocked);
        } catch (Exception exception) {
            mCamera = null;
            cancelStartupTimeout();
            mPreview.failStartup(session, "failed to open the camera: " + CaptureCoordinator.describe(exception));
            return;
        }

        if (cameraParameters != null) {
            Log.d(TAG, "camera parameter not null");

            try {
                // Check for flashMode as well to prevent error on frontward facing camera.
                List<String> supportedFlashModesNewCamera = mCamera.getParameters().getSupportedFlashModes();
                String currentFlashModePreviousCamera = cameraParameters.getFlashMode();
                if (supportedFlashModesNewCamera != null && supportedFlashModesNewCamera.contains(currentFlashModePreviousCamera)) {
                    Log.d(TAG, "current flash mode supported on new camera. setting params");
                    /* mCamera.setParameters(cameraParameters);
            The line above is disabled because parameters that can actually be changed are different from one device to another. Makes less sense trying to reconfigure them when changing camera device while those settings gan be changed using plugin methods.
         */
                } else {
                    Log.d(TAG, "current flash mode NOT supported on new camera");
                }
            } catch (Exception exception) {
                Log.w(TAG, "failed to read the flash modes of the switched-in camera: " + CaptureCoordinator.describe(exception));
            }
        } else {
            Log.d(TAG, "camera parameter NULL");
        }

        // Attaching reconciles against the existing output target and starts the preview; an extra
        // startPreview() here would start it twice.
        attachCameraToPreview(session);
    }

    public void setCameraParameters(Camera.Parameters params) {
        cameraParameters = params;

        if (mCamera != null && cameraParameters != null) {
            mCamera.setParameters(cameraParameters);
        }
    }

    public boolean hasFrontCamera() {
        return getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    public static Bitmap applyMatrix(Bitmap source, Matrix matrix) {
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    ShutterCallback shutterCallback = new ShutterCallback() {
        public void onShutter() {
            // do nothing, availabilty of this callback causes default system shutter sound to work
        }
    };

    private static int exifToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }

    private String getTempDirectoryPath() {
        File cache = null;

        // Use internal storage
        cache = getActivity().getCacheDir();

        // Create the cache directory if it doesn't exist
        cache.mkdirs();
        return cache.getAbsolutePath();
    }

    private String getTempFilePath() {
        return getTempDirectoryPath() + "/cpcp_capture_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".jpg";
    }

    /**
     * Builds the JPEG callback for one accepted capture.
     *
     * <p>The callback is created per capture and closes over that capture's identity rather than
     * reading shared CameraActivity state. A callback belonging to an aborted capture therefore
     * loses its claim outright and cannot be mistaken for a newer capture that happens to be in
     * flight on the same camera (issue #424 corrective pass).
     */
    private PictureCallback createJpegPictureCallback(final PendingCapture capture) {
        return new PictureCallback() {
            public void onPictureTaken(byte[] data, Camera pictureCamera) {
                Log.d(TAG, "CameraPreview jpegPictureCallback for capture " + capture.token + " (session " + capture.sessionId + ")");

                // Claim this specific capture. One aborted by pause, stop, a camera switch or the
                // loss of the preview output has already been settled, and a callback from a
                // replaced camera owns nothing: either way this must do nothing at all - no image
                // processing, no preview restart, no second outcome.
                if (!claimCapture(capture, pictureCamera)) {
                    Log.w(TAG, "ignoring a picture callback whose capture is no longer active");
                    return;
                }

                String result = null;
                String error = null;

                try {
                    if (!disableExifHeaderStripping) {
                        Matrix matrix = new Matrix();
                        if (capture.cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            matrix.preScale(1.0f, -1.0f);
                        }

                        ExifInterface exifInterface = new ExifInterface(new ByteArrayInputStream(data));
                        int rotation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                        int rotationInDegrees = exifToDegrees(rotation);

                        if (rotation != 0f) {
                            matrix.preRotate(rotationInDegrees);
                        }

                        // Check if matrix has changed. In that case, apply matrix and override data
                        if (!matrix.isIdentity()) {
                            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                            bitmap = applyMatrix(bitmap, matrix);

                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            bitmap.compress(CompressFormat.JPEG, capture.quality, outputStream);
                            data = outputStream.toByteArray();
                        }
                    }

                    if (!storeToFile) {
                        result = Base64.encodeToString(data, Base64.NO_WRAP);
                    } else {
                        String path = getTempFilePath();
                        FileOutputStream out = new FileOutputStream(path);
                        try {
                            out.write(data);
                        } finally {
                            out.close();
                        }
                        result = path;
                    }
                    Log.d(TAG, "CameraPreview pictureTakenHandler called back");
                } catch (OutOfMemoryError e) {
                    // most likely failed to allocate memory for rotateBitmap
                    Log.e(TAG, "CameraPreview OutOfMemoryError");
                    // failed to allocate memory
                    error = "Picture too large (memory)";
                } catch (IOException e) {
                    Log.e(TAG, "CameraPreview IOException", e);
                    error = "IO Error when extracting exif";
                } catch (Exception e) {
                    // Previously logged only, which left the Capacitor call pending forever.
                    Log.e(TAG, "CameraPreview onPictureTaken general exception", e);
                    error = "failed to process the captured picture: " + CaptureCoordinator.describe(e);
                }

                // Camera1 stops the preview when a picture is taken. The restart is attempted on
                // the camera this capture belongs to, and a synchronous failure decides the
                // capture's outcome instead of being reported to an already-settled start call.
                String restartError = mPreview != null ? mPreview.restartPreviewAfterCapture(capture.camera) : null;

                CameraPreviewListener listener = eventListener;
                if (listener == null) {
                    return;
                }

                if (error != null && restartError != null) {
                    // Only the primary error is reported; the secondary one is logged concisely.
                    Log.e(TAG, "the preview restart also failed after a failed capture: " + restartError);
                }

                String failure = CaptureCoordinator.resolveCaptureFailure(error, restartError);
                if (failure != null) {
                    listener.onPictureTakenError(failure);
                } else {
                    listener.onPictureTaken(result);
                }
            }
        };
    }

    /**
     * Claims {@code capture} on behalf of the JPEG callback that was created for it.
     *
     * @return true only when this exact capture is still the active one and this call just ended
     *         it, which makes an aborted, duplicate or foreign callback a no-op
     */
    private boolean claimCapture(PendingCapture capture, Camera pictureCamera) {
        if (pictureCamera != null && capture.camera != pictureCamera) {
            Log.w(
                TAG,
                "picture callback from a camera that does not own capture " + capture.token + " (session " + capture.sessionId + ")"
            );
            return false;
        }
        return captureCoordinator.finishCapture(capture.token);
    }

    private Camera.Size getOptimalPictureSize(
        final int width,
        final int height,
        final Camera.Size previewSize,
        final List<Camera.Size> supportedSizes
    ) {
        /*
      get the supportedPictureSize that:
      - matches exactly width and height
      - has the closest aspect ratio to the preview aspect ratio
      - has picture.width and picture.height closest to width and height
      - has the highest supported picture width and height up to 2 Megapixel if width == 0 || height == 0
    */
        Camera.Size size = mCamera.new Size(width, height);

        // convert to landscape if necessary
        if (size.width < size.height) {
            int temp = size.width;
            size.width = size.height;
            size.height = temp;
        }

        Camera.Size requestedSize = mCamera.new Size(size.width, size.height);

        double previewAspectRatio = (double) previewSize.width / (double) previewSize.height;

        if (previewAspectRatio < 1.0) {
            // reset ratio to landscape
            previewAspectRatio = 1.0 / previewAspectRatio;
        }

        Log.d(TAG, "CameraPreview previewAspectRatio " + previewAspectRatio);

        double aspectTolerance = 0.1;
        double bestDifference = Double.MAX_VALUE;

        for (int i = 0; i < supportedSizes.size(); i++) {
            Camera.Size supportedSize = supportedSizes.get(i);

            // Perfect match
            if (supportedSize.equals(requestedSize)) {
                Log.d(TAG, "CameraPreview optimalPictureSize " + supportedSize.width + 'x' + supportedSize.height);
                return supportedSize;
            }

            double difference = Math.abs(previewAspectRatio - ((double) supportedSize.width / (double) supportedSize.height));

            if (difference < bestDifference - aspectTolerance) {
                // better aspectRatio found
                if ((width != 0 && height != 0) || (supportedSize.width * supportedSize.height < 2048 * 1024)) {
                    size.width = supportedSize.width;
                    size.height = supportedSize.height;
                    bestDifference = difference;
                }
            } else if (difference < bestDifference + aspectTolerance) {
                // same aspectRatio found (within tolerance)
                if (width == 0 || height == 0) {
                    // set highest supported resolution below 2 Megapixel
                    if ((size.width < supportedSize.width) && (supportedSize.width * supportedSize.height < 2048 * 1024)) {
                        size.width = supportedSize.width;
                        size.height = supportedSize.height;
                    }
                } else {
                    // check if this pictureSize closer to requested width and height
                    if (
                        Math.abs(width * height - supportedSize.width * supportedSize.height) <
                        Math.abs(width * height - size.width * size.height)
                    ) {
                        size.width = supportedSize.width;
                        size.height = supportedSize.height;
                    }
                }
            }
        }
        Log.d(TAG, "CameraPreview optimalPictureSize " + size.width + 'x' + size.height);
        return size;
    }

    static byte[] rotateNV21(final byte[] yuv, final int width, final int height, final int rotation) {
        if (rotation == 0) return yuv;
        if (rotation % 90 != 0 || rotation < 0 || rotation > 270) {
            throw new IllegalArgumentException("0 <= rotation < 360, rotation % 90 == 0");
        }

        final byte[] output = new byte[yuv.length];
        final int frameSize = width * height;
        final boolean swap = rotation % 180 != 0;
        final boolean xflip = rotation % 270 != 0;
        final boolean yflip = rotation >= 180;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                final int yIn = j * width + i;
                final int uIn = frameSize + (j >> 1) * width + (i & ~1);
                final int vIn = uIn + 1;

                final int wOut = swap ? height : width;
                final int hOut = swap ? width : height;
                final int iSwapped = swap ? j : i;
                final int jSwapped = swap ? i : j;
                final int iOut = xflip ? wOut - iSwapped - 1 : iSwapped;
                final int jOut = yflip ? hOut - jSwapped - 1 : jSwapped;

                final int yOut = jOut * wOut + iOut;
                final int uOut = frameSize + (jOut >> 1) * wOut + (iOut & ~1);
                final int vOut = uOut + 1;

                output[yOut] = (byte) (0xff & yuv[yIn]);
                output[uOut] = (byte) (0xff & yuv[uIn]);
                output[vOut] = (byte) (0xff & yuv[vIn]);
            }
        }
        return output;
    }

    public void setOpacity(final float opacity) {
        Log.d(TAG, "set opacity:" + opacity);
        this.opacity = opacity;
        if (mPreview != null) {
            mPreview.setOpacity(opacity);
        }
    }

    public void takeSnapshot(final int quality) {
        final Camera snapshotCamera = mCamera;
        final CameraPreviewListener snapshotListener = eventListener;

        if (snapshotCamera == null || !isPreviewReady()) {
            if (snapshotListener != null) {
                snapshotListener.onSnapshotTakenError(CaptureCoordinator.ERROR_PREVIEW_NOT_READY);
            }
            return;
        }
        if (snapshotListener == null) {
            return;
        }

        try {
            snapshotCamera.setPreviewCallback(
                new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] bytes, Camera camera) {
                        try {
                            Camera.Parameters parameters = camera.getParameters();
                            Camera.Size size = parameters.getPreviewSize();
                            int orientation = mPreview.getDisplayOrientation();
                            if (mPreview.getCameraFacing() == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                bytes = rotateNV21(bytes, size.width, size.height, (360 - orientation) % 360);
                            } else {
                                bytes = rotateNV21(bytes, size.width, size.height, orientation);
                            }
                            // switch width/height when rotating 90/270 deg
                            Rect rect =
                                orientation == 90 || orientation == 270
                                    ? new Rect(0, 0, size.height, size.width)
                                    : new Rect(0, 0, size.width, size.height);
                            YuvImage yuvImage = new YuvImage(bytes, parameters.getPreviewFormat(), rect.width(), rect.height(), null);
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            yuvImage.compressToJpeg(rect, quality, byteArrayOutputStream);
                            byte[] data = byteArrayOutputStream.toByteArray();
                            byteArrayOutputStream.close();
                            eventListener.onSnapshotTaken(Base64.encodeToString(data, Base64.NO_WRAP));
                        } catch (IOException e) {
                            Log.e(TAG, "CameraPreview IOException", e);
                            eventListener.onSnapshotTakenError("IO Error");
                        } catch (Exception e) {
                            Log.e(TAG, "CameraPreview snapshot exception", e);
                            eventListener.onSnapshotTakenError("failed to take snapshot: " + CaptureCoordinator.describe(e));
                        } finally {
                            try {
                                snapshotCamera.setPreviewCallback(null);
                            } catch (Exception e) {
                                Log.w(TAG, "failed to clear the snapshot callback: " + CaptureCoordinator.describe(e));
                            }
                        }
                    }
                }
            );
        } catch (Exception exception) {
            snapshotListener.onSnapshotTakenError("failed to start the snapshot: " + CaptureCoordinator.describe(exception));
        }
    }

    public void takePicture(final int width, final int height, final int quality) {
        Log.d(TAG, "CameraPreview takePicture width: " + width + ", height: " + height + ", quality: " + quality);

        // Camera1 must be driven from the looper that opened it. The previous raw Thread let a
        // RuntimeException from takePicture() reach the default handler and kill the process.
        // Accepting the capture on that same looper also means acceptance can never interleave
        // with a lifecycle abort, so the capture identity below is always consistent.
        runOnCameraThread(() -> takePictureOnCameraThread(width, height, quality));
    }

    private void takePictureOnCameraThread(final int width, final int height, final int quality) {
        final Camera camera = mCamera;
        final boolean hasCamera = mPreview != null && camera != null;

        final long token = captureCoordinator.beginCapture(hasCamera, isPreviewReady(), previewResumed, this::reportCaptureError);
        if (token == CaptureCoordinator.NO_CAPTURE) {
            return;
        }

        final PendingCapture capture = new PendingCapture(token, mPreview.getSessionId(), camera, cameraCurrentlyLocked, quality);

        captureCoordinator.performCapture(token, () -> configureAndTakePicture(capture, width, height), this::reportCaptureError);
    }

    /**
     * Camera1 capture setup and trigger. Called on the camera looper inside
     * {@link CaptureCoordinator#performCapture(long, CaptureCoordinator.CaptureAction, CaptureCoordinator.ErrorReporter)},
     * which converts any exception into a single error callback (issue #424).
     */
    private void configureAndTakePicture(final PendingCapture capture, final int width, final int height) {
        final Camera camera = capture.camera;
        if (camera == null || camera != mCamera) {
            throw new IllegalStateException("camera was released before the capture started");
        }

        Camera.Parameters params = camera.getParameters();

        Camera.Size size = getOptimalPictureSize(width, height, params.getPreviewSize(), params.getSupportedPictureSizes());
        params.setPictureSize(size.width, size.height);

        if (capture.cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT && !storeToFile) {
            // The image will be recompressed in the callback
            params.setJpegQuality(99);
        } else {
            params.setJpegQuality(capture.quality);
        }

        if (capture.cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT && disableExifHeaderStripping) {
            Activity activity = getActivity();
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 180;
                    break;
                case Surface.ROTATION_180:
                    degrees = 270;
                    break;
                case Surface.ROTATION_270:
                    degrees = 0;
                    break;
            }
            int orientation;
            Camera.CameraInfo info = new Camera.CameraInfo();
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                orientation = (info.orientation + degrees) % 360;
                if (degrees != 0) {
                    orientation = (360 - orientation) % 360;
                }
            } else {
                orientation = (info.orientation - degrees + 360) % 360;
            }
            params.setRotation(orientation);
        } else {
            params.setRotation(mPreview.getDisplayOrientation());
        }

        camera.setParameters(params);
        camera.takePicture(shutterCallback, null, createJpegPictureCallback(capture));
    }

    public void startRecord(
        final String filePath,
        final String camera,
        final int width,
        final int height,
        final int quality,
        final boolean withFlash,
        final int maxDuration
    ) {
        Log.d(TAG, "CameraPreview startRecord camera: " + camera + " width: " + width + ", height: " + height + ", quality: " + quality);
        Activity activity = getActivity();
        muteStream(true, activity);
        if (this.mRecordingState == RecordingState.STARTED) {
            Log.d(TAG, "Already Recording");
            return;
        }

        this.recordFilePath = filePath;
        int mOrientationHint = calculateOrientationHint();
        int videoWidth = 0; //set whatever
        int videoHeight = 0; //set whatever

        Camera.Parameters cameraParams = mCamera.getParameters();
        if (withFlash) {
            cameraParams.setFlashMode(withFlash ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(cameraParams);
            mCamera.startPreview();
        }

        mCamera.unlock();
        mRecorder = new MediaRecorder();

        try {
            mRecorder.setCamera(mCamera);

            CamcorderProfile profile;
            if (CamcorderProfile.hasProfile(defaultCameraId, CamcorderProfile.QUALITY_HIGH)) {
                profile = CamcorderProfile.get(defaultCameraId, CamcorderProfile.QUALITY_HIGH);
            } else {
                if (CamcorderProfile.hasProfile(defaultCameraId, CamcorderProfile.QUALITY_480P)) {
                    profile = CamcorderProfile.get(defaultCameraId, CamcorderProfile.QUALITY_480P);
                } else {
                    if (CamcorderProfile.hasProfile(defaultCameraId, CamcorderProfile.QUALITY_720P)) {
                        profile = CamcorderProfile.get(defaultCameraId, CamcorderProfile.QUALITY_720P);
                    } else {
                        if (CamcorderProfile.hasProfile(defaultCameraId, CamcorderProfile.QUALITY_1080P)) {
                            profile = CamcorderProfile.get(defaultCameraId, CamcorderProfile.QUALITY_1080P);
                        } else {
                            profile = CamcorderProfile.get(defaultCameraId, CamcorderProfile.QUALITY_LOW);
                        }
                    }
                }
            }

            mRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mRecorder.setProfile(profile);
            mRecorder.setOutputFile(filePath);
            mRecorder.setOrientationHint(mOrientationHint);
            mRecorder.setMaxDuration(maxDuration);

            mRecorder.prepare();
            Log.d(TAG, "Starting recording");
            mRecorder.start();
            eventListener.onStartRecordVideo();
        } catch (IOException e) {
            eventListener.onStartRecordVideoError(e.getMessage());
        }
    }

    public int calculateOrientationHint() {
        DisplayMetrics dm = new DisplayMetrics();
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(defaultCameraId, info);
        int cameraRotationOffset = info.orientation;
        Activity activity = getActivity();

        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int currentScreenRotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        int degrees = 0;
        switch (currentScreenRotation) {
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

        int orientation;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            orientation = (cameraRotationOffset + degrees) % 360;
            if (degrees != 0) {
                orientation = (360 - orientation) % 360;
            }
        } else {
            orientation = (cameraRotationOffset - degrees + 360) % 360;
        }
        Log.w(TAG, "************orientationHint ***********= " + orientation);

        return orientation;
    }

    public void stopRecord() {
        Log.d(TAG, "stopRecord");

        try {
            mRecorder.stop();
            mRecorder.reset(); // clear recorder configuration
            mRecorder.release(); // release the recorder object
            mRecorder = null;
            mCamera.lock();
            Camera.Parameters cameraParams = mCamera.getParameters();
            cameraParams.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(cameraParams);
            mCamera.startPreview();
            eventListener.onStopRecordVideo(this.recordFilePath);
        } catch (Exception e) {
            eventListener.onStopRecordVideoError(e.getMessage());
        }
    }

    public void muteStream(boolean mute, Activity activity) {
        AudioManager audioManager = ((AudioManager) activity.getApplicationContext().getSystemService(Context.AUDIO_SERVICE));
        int direction = mute ? audioManager.ADJUST_MUTE : audioManager.ADJUST_UNMUTE;
    }

    public void setFocusArea(final int pointX, final int pointY, final Camera.AutoFocusCallback callback) {
        if (mCamera != null) {
            mCamera.cancelAutoFocus();

            Camera.Parameters parameters = mCamera.getParameters();

            Rect focusRect = calculateTapArea(pointX, pointY, 1f);
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            parameters.setFocusAreas(Arrays.asList(new Camera.Area(focusRect, 1000)));

            if (parameters.getMaxNumMeteringAreas() > 0) {
                Rect meteringRect = calculateTapArea(pointX, pointY, 1.5f);
                parameters.setMeteringAreas(Arrays.asList(new Camera.Area(meteringRect, 1000)));
            }

            try {
                setCameraParameters(parameters);
                mCamera.autoFocus(callback);
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
                callback.onAutoFocus(false, this.mCamera);
            }
        }
    }

    private Rect calculateTapArea(float x, float y, float coefficient) {
        if (x < 100) {
            x = 100;
        }
        if (x > width - 100) {
            x = width - 100;
        }
        if (y < 100) {
            y = 100;
        }
        if (y > height - 100) {
            y = height - 100;
        }
        return new Rect(
            Math.round(((x - 100) * 2000) / width - 1000),
            Math.round(((y - 100) * 2000) / height - 1000),
            Math.round(((x + 100) * 2000) / width - 1000),
            Math.round(((y + 100) * 2000) / height - 1000)
        );
    }

    /**
     * Determine the space between the first two fingers
     */
    private static float getFingerSpacing(MotionEvent event) {
        // ...
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }
}
