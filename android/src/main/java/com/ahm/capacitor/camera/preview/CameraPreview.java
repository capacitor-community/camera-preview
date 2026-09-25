package com.ahm.capacitor.camera.preview;

import static android.Manifest.permission.CAMERA;

import android.annotation.SuppressLint;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;
import java.util.List;
import org.json.JSONArray;

@CapacitorPlugin(name = "CameraPreview", permissions = { @Permission(strings = { CAMERA }, alias = CameraPreview.CAMERA_PERMISSION_ALIAS) })
public class CameraPreview extends Plugin implements CameraActivity.CameraPreviewListener {

    static final String CAMERA_PERMISSION_ALIAS = "camera";

    private static String VIDEO_FILE_PATH = "";
    private static String VIDEO_FILE_EXTENSION = ".mp4";

    // Pending Capacitor calls. Each slot is claimed atomically so a second request can never
    // overwrite a pending callback id, and is emptied by whoever settles the call, so every call
    // resolves or rejects exactly once (issue #424).
    private final PendingCallSlot captureCallbackId = new PendingCallSlot("capture");
    private final PendingCallSlot snapshotCallbackId = new PendingCallSlot("captureSample");
    private final PendingCallSlot cameraStartCallbackId = new PendingCallSlot("start");
    private final PendingCallSlot cameraFlipCallbackId = new PendingCallSlot("flip");

    private String recordCallbackId = "";

    // keep track of previously specified orientation to support locking orientation:
    private int previousOrientationRequest = -1;

    private CameraActivity fragment;
    private int containerViewId = 20;

    @PluginMethod
    public void start(PluginCall call) {
        if (PermissionState.GRANTED.equals(getPermissionState(CAMERA_PERMISSION_ALIAS))) {
            startCamera(call);
        } else {
            requestPermissionForAlias(CAMERA_PERMISSION_ALIAS, call, "handleCameraPermissionResult");
        }
    }

    @PluginMethod
    public void flip(final PluginCall call) {
        // Camera1 is owned by the looper it was opened on; the switch and its admission check must
        // both run there.
        bridge
            .getActivity()
            .runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        CameraActivity activeFragment = fragment;
                        if (activeFragment == null) {
                            call.reject("Camera is not running");
                            return;
                        }

                        String rejection = activeFragment.checkCanFlip();
                        if (rejection != null) {
                            call.reject(rejection);
                            return;
                        }
                        if (!cameraFlipCallbackId.claim(call.getCallbackId())) {
                            call.reject(PreviewOperationRouter.ERROR_FLIP_IN_PROGRESS);
                            return;
                        }

                        // Saved before the switch starts, because switchCamera() can fail
                        // synchronously (Camera.open) and must be able to reject this call.
                        // NOTE: flip() stays pending until the switched-in camera's preview
                        // produces its first frame, reported through onCameraFlipped(). Resolving
                        // when switchCamera() returned reported success for a camera that may
                        // never have opened or previewed (issue #424).
                        bridge.saveCall(call);

                        try {
                            activeFragment.switchCamera();
                        } catch (Exception e) {
                            Logger.debug(getLogTag(), "Camera flip exception: " + e);
                            // Releases the flip session first, so a throw part-way through the
                            // switch cannot leave every later flip blocked as "already in
                            // progress". Whichever of the two settles first wins; the other one
                            // finds the slot empty and does nothing.
                            activeFragment.discardPendingFlip("failed to flip camera");
                            rejectPendingCall(cameraFlipCallbackId, "failed to flip camera");
                        }
                    }
                }
            );
    }

    @PluginMethod
    public void setOpacity(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }

        bridge.saveCall(call);
        Float opacity = call.getFloat("opacity", 1F);
        fragment.setOpacity(opacity);
    }

    @PluginMethod
    public void capture(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }
        // A live Camera object is not readiness: until the preview has delivered a frame,
        // Camera.takePicture() throws (issue #424).
        if (!fragment.isPreviewReady()) {
            call.reject("Camera preview is not ready");
            return;
        }
        if (!captureCallbackId.claim(call.getCallbackId())) {
            call.reject("A capture is already in progress");
            return;
        }
        bridge.saveCall(call);

        Integer quality = call.getInt("quality", 85);
        // Image Dimensions - Optional
        Integer width = call.getInt("width", 0);
        Integer height = call.getInt("height", 0);
        fragment.takePicture(width, height, quality);
    }

    @PluginMethod
    public void captureSample(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }
        if (!snapshotCallbackId.claim(call.getCallbackId())) {
            call.reject("A capture sample is already in progress");
            return;
        }
        bridge.saveCall(call);

        Integer quality = call.getInt("quality", 85);
        fragment.takeSnapshot(quality);
    }

    @SuppressLint("WrongConstant")
    @PluginMethod
    public void stop(final PluginCall call) {
        bridge
            .getActivity()
            .runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        FrameLayout containerView = getBridge().getActivity().findViewById(containerViewId);

                        // allow orientation changes after closing camera:
                        getBridge().getActivity().setRequestedOrientation(previousOrientationRequest);
                        getBridge().getWebView().setOnTouchListener(null);

                        // Settle anything still in flight so no JavaScript promise hangs forever.
                        rejectPendingCall(cameraStartCallbackId, "camera stopped before the preview was ready");
                        rejectPendingCall(cameraFlipCallbackId, "camera stopped before the camera flip completed");
                        rejectPendingCall(captureCallbackId, "camera stopped before the capture completed");
                        rejectPendingCall(snapshotCallbackId, "camera stopped before the capture sample completed");

                        if (containerView != null) {
                            ((ViewGroup) getBridge().getWebView().getParent()).removeView(containerView);
                            getBridge().getWebView().setBackgroundColor(Color.WHITE);
                            FragmentManager fragmentManager = getActivity().getFragmentManager();
                            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                            if (fragment != null) {
                                fragmentTransaction.remove(fragment);
                            }
                            // This runnable is posted to the UI thread, so it can land after the
                            // host activity has saved its state — commit() then throws
                            // IllegalStateException on the UI thread, where the caller cannot
                            // catch it. The transaction only removes the preview fragment, so
                            // there is no state worth preserving across a process death.
                            fragmentTransaction.commitAllowingStateLoss();
                            fragment = null;

                            call.resolve();
                        } else {
                            call.reject("camera already stopped");
                        }
                    }
                }
            );
    }

    @PluginMethod
    public void getSupportedFlashModes(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }

        Camera camera = fragment.getCamera();
        Camera.Parameters params = camera.getParameters();
        List<String> supportedFlashModes;
        supportedFlashModes = params.getSupportedFlashModes();
        JSONArray jsonFlashModes = new JSONArray();

        if (supportedFlashModes != null) {
            for (int i = 0; i < supportedFlashModes.size(); i++) {
                jsonFlashModes.put(new String(supportedFlashModes.get(i)));
            }
        }

        JSObject jsObject = new JSObject();
        jsObject.put("result", jsonFlashModes);
        call.resolve(jsObject);
    }

    @PluginMethod
    public void setFlashMode(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }

        String flashMode = call.getString("flashMode");
        if (flashMode == null || flashMode.isEmpty() == true) {
            call.reject("flashMode required parameter is missing");
            return;
        }

        Camera camera = fragment.getCamera();
        Camera.Parameters params = camera.getParameters();

        List<String> supportedFlashModes;
        supportedFlashModes = camera.getParameters().getSupportedFlashModes();
        if (supportedFlashModes.indexOf(flashMode) > -1) {
            params.setFlashMode(flashMode);
        } else {
            call.reject("Flash mode not recognised: " + flashMode);
            return;
        }

        fragment.setCameraParameters(params);

        call.resolve();
    }

    @PluginMethod
    public void startRecordVideo(final PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }
        final String filename = "videoTmp";
        VIDEO_FILE_PATH = getActivity().getCacheDir().toString() + "/";

        final String position = call.getString("position", "front");
        final Integer width = call.getInt("width", 0);
        final Integer height = call.getInt("height", 0);
        final Boolean withFlash = call.getBoolean("withFlash", false);
        final Integer maxDuration = call.getInt("maxDuration", 0);
        // final Integer quality = call.getInt("quality", 0);
        bridge.saveCall(call);
        recordCallbackId = call.getCallbackId();

        bridge
            .getActivity()
            .runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        // fragment.startRecord(getFilePath(filename), position, width, height, quality, withFlash);
                        fragment.startRecord(getFilePath(filename), position, width, height, 70, withFlash, maxDuration);
                    }
                }
            );

        call.resolve();
    }

    @PluginMethod
    public void stopRecordVideo(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }

        System.out.println("stopRecordVideo - Callbackid=" + call.getCallbackId());

        bridge.saveCall(call);
        recordCallbackId = call.getCallbackId();

        // bridge.getActivity().runOnUiThread(new Runnable() {
        //     @Override
        //     public void run() {
        //         fragment.stopRecord();
        //     }
        // });

        fragment.stopRecord();
        // call.resolve();
    }

    @PluginMethod
    public void isCameraStarted(PluginCall call) {
        // Reports preview readiness (a first frame was observed), not merely that a Camera object
        // exists (issue #424).
        boolean isCameraStarted = fragment != null && fragment.isPreviewReady();
        JSObject ret = new JSObject();
        ret.put("value", isCameraStarted);
        call.resolve(ret);
    }

    @PermissionCallback
    private void handleCameraPermissionResult(PluginCall call) {
        if (PermissionState.GRANTED.equals(getPermissionState(CAMERA_PERMISSION_ALIAS))) {
            startCamera(call);
        } else {
            Logger.debug(getLogTag(), "User denied camera permission: " + getPermissionState(CAMERA_PERMISSION_ALIAS).toString());
            call.reject("Permission failed: user denied access to camera.");
        }
    }

    private void startCamera(final PluginCall call) {
        String position = call.getString("position");

        if (position == null || position.isEmpty() || "rear".equals(position)) {
            position = "back";
        } else {
            position = "front";
        }

        final Integer x = call.getInt("x", 0);
        final Integer y = call.getInt("y", 0);
        final Integer width = call.getInt("width", 0);
        final Integer height = call.getInt("height", 0);
        final Integer paddingBottom = call.getInt("paddingBottom", 0);
        final Boolean toBack = call.getBoolean("toBack", false);
        final Boolean storeToFile = call.getBoolean("storeToFile", false);
        final Boolean enableOpacity = call.getBoolean("enableOpacity", false);
        final Boolean enableZoom = call.getBoolean("enableZoom", false);
        final Boolean disableExifHeaderStripping = call.getBoolean("disableExifHeaderStripping", true);
        final Boolean lockOrientation = call.getBoolean("lockAndroidOrientation", false);
        final String cameraPosition = position;
        previousOrientationRequest = getBridge().getActivity().getRequestedOrientation();

        // The fragment is created on the UI thread together with the container view, so a second
        // start() can never replace the fragment of a session it is about to reject.
        bridge
            .getActivity()
            .runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if (fragment != null || getBridge().getActivity().findViewById(containerViewId) != null) {
                            call.reject("camera already started");
                            return;
                        }
                        if (cameraStartCallbackId.isPending()) {
                            call.reject("camera start already in progress");
                            return;
                        }

                        CameraActivity cameraActivity = new CameraActivity();
                        cameraActivity.setEventListener(CameraPreview.this);
                        cameraActivity.defaultCamera = cameraPosition;
                        cameraActivity.tapToTakePicture = false;
                        cameraActivity.dragEnabled = false;
                        cameraActivity.tapToFocus = true;
                        cameraActivity.disableExifHeaderStripping = disableExifHeaderStripping;
                        cameraActivity.storeToFile = storeToFile;
                        cameraActivity.toBack = toBack;
                        cameraActivity.enableOpacity = enableOpacity;
                        cameraActivity.enableZoom = enableZoom;

                        DisplayMetrics metrics = getBridge().getActivity().getResources().getDisplayMetrics();
                        // lock orientation if specified in options:
                        if (lockOrientation) {
                            getBridge().getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
                        }

                        // offset
                        int computedX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, metrics);
                        int computedY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, y, metrics);

                        // size
                        Integer computedWidth = null;
                        Integer computedHeight = null;
                        int computedPaddingBottom = 0;

                        if (paddingBottom != 0) {
                            computedPaddingBottom = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, paddingBottom, metrics);
                        }

                        if (width != 0) {
                            computedWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, metrics);
                        }

                        if (height != 0) {
                            computedHeight =
                                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, metrics) - computedPaddingBottom;
                        }

                        cameraActivity.setRect(computedX, computedY, computedWidth, computedHeight);

                        FrameLayout containerView = new FrameLayout(getActivity().getApplicationContext());
                        containerView.setId(containerViewId);

                        getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
                        ((ViewGroup) getBridge().getWebView().getParent()).addView(containerView);
                        if (toBack == true) {
                            getBridge().getWebView().getParent().bringChildToFront(getBridge().getWebView());
                        }

                        fragment = cameraActivity;

                        FragmentManager fragmentManager = getBridge().getActivity().getFragmentManager();
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                        fragmentTransaction.add(containerView.getId(), fragment);
                        fragmentTransaction.commit();

                        // NOTE: we don't invoke call.resolve here because it must be invoked in onCameraStarted,
                        // which now fires only once the native preview has delivered its first frame. Resolving
                        // earlier reported a preview that was not usable yet and let capture() reach a camera
                        // that had never started previewing (issue #424).
                        bridge.saveCall(call);
                        cameraStartCallbackId.claim(call.getCallbackId());
                    }
                }
            );
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
    }

    @Override
    public void onPictureTaken(String originalPicture) {
        JSObject jsObject = new JSObject();
        jsObject.put("value", originalPicture);
        resolvePendingCall(captureCallbackId, jsObject, "onPictureTaken");
    }

    @Override
    public void onPictureTakenError(String message) {
        rejectPendingCall(captureCallbackId, message);
    }

    @Override
    public void onSnapshotTaken(String originalPicture) {
        JSObject jsObject = new JSObject();
        jsObject.put("value", originalPicture);
        resolvePendingCall(snapshotCallbackId, jsObject, "onSnapshotTaken");
    }

    @Override
    public void onSnapshotTakenError(String message) {
        rejectPendingCall(snapshotCallbackId, message);
    }

    @Override
    public void onFocusSet(int pointX, int pointY) {}

    @Override
    public void onFocusSetError(String message) {}

    @Override
    public void onBackButton() {}

    @Override
    public void onCameraStarted() {
        if (fragment != null && fragment.toBack) {
            setupBroadcast();
        }

        // Reached only once the native preview delivered its first frame (issue #424).
        PluginCall pluginCall = consumePendingCall(cameraStartCallbackId);
        if (pluginCall == null) {
            Logger.debug(getLogTag(), "camera preview became ready with no pending start call");
            return;
        }
        pluginCall.resolve();
        bridge.releaseCall(pluginCall);
    }

    @Override
    public void onCameraFlipped() {
        // Reached only once the switched-in camera's preview delivered its first frame.
        PluginCall pluginCall = consumePendingCall(cameraFlipCallbackId);
        if (pluginCall == null) {
            Logger.debug(getLogTag(), "camera flip completed with no pending flip call");
            return;
        }
        pluginCall.resolve();
        bridge.releaseCall(pluginCall);
    }

    @Override
    public void onCameraFlipError(String message) {
        PluginCall pluginCall = consumePendingCall(cameraFlipCallbackId);
        if (pluginCall == null) {
            Logger.debug(getLogTag(), "camera flip failed with no pending flip call: " + message);
            return;
        }
        pluginCall.reject(message);
        bridge.releaseCall(pluginCall);
    }

    @Override
    public void onCameraStartError(String message) {
        PluginCall pluginCall = consumePendingCall(cameraStartCallbackId);
        if (pluginCall == null) {
            Logger.warn(getLogTag(), "camera preview startup failed with no pending start call: " + message);
            return;
        }
        pluginCall.reject(message);
        bridge.releaseCall(pluginCall);
    }

    @Override
    public void onStartRecordVideo() {}

    @Override
    public void onStartRecordVideoError(String message) {
        bridge.getSavedCall(recordCallbackId).reject(message);
    }

    @Override
    public void onStopRecordVideo(String file) {
        PluginCall pluginCall = bridge.getSavedCall(recordCallbackId);
        JSObject jsObject = new JSObject();
        jsObject.put("videoFilePath", file);
        pluginCall.resolve(jsObject);
    }

    @Override
    public void onStopRecordVideoError(String error) {
        bridge.getSavedCall(recordCallbackId).reject(error);
    }

    /**
     * Claims the callback id held by {@code slot} and clears it, so at most one caller can settle
     * the pending call.
     *
     * @return the saved call, or null when nothing was pending
     */
    private PluginCall consumePendingCall(PendingCallSlot slot) {
        String callbackId = slot.consume();
        if (callbackId == null) {
            return null;
        }
        return bridge.getSavedCall(callbackId);
    }

    private void resolvePendingCall(PendingCallSlot slot, JSObject data, String source) {
        PluginCall call = consumePendingCall(slot);
        if (call == null) {
            Logger.debug(getLogTag(), source + " with no pending call; ignoring");
            return;
        }
        call.resolve(data);
        bridge.releaseCall(call);
    }

    private void rejectPendingCall(PendingCallSlot slot, String message) {
        PluginCall call = consumePendingCall(slot);
        if (call == null) {
            Logger.debug(getLogTag(), "no pending call to reject with: " + message);
            return;
        }
        call.reject(message);
        bridge.releaseCall(call);
    }

    private boolean hasView(PluginCall call) {
        if (fragment == null) {
            return false;
        }

        return true;
    }

    private boolean hasCamera(PluginCall call) {
        if (this.hasView(call) == false) {
            return false;
        }

        if (fragment.getCamera() == null) {
            return false;
        }

        return true;
    }

    private String getFilePath(String filename) {
        String fileName = filename;

        int i = 1;

        while (new File(VIDEO_FILE_PATH + fileName + VIDEO_FILE_EXTENSION).exists()) {
            // Add number suffix if file exists
            fileName = filename + '_' + i;
            i++;
        }

        return VIDEO_FILE_PATH + fileName + VIDEO_FILE_EXTENSION;
    }

    private void setupBroadcast() {
        /** When touch event is triggered, relay it to camera view if needed so it can support pinch zoom */

        getBridge().getWebView().setClickable(true);
        getBridge()
            .getWebView()
            .setOnTouchListener(
                new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (fragment != null && fragment.toBack && fragment.frameContainerLayout != null) {
                            fragment.frameContainerLayout.dispatchTouchEvent(event);
                        }
                        return false;
                    }
                }
            );
    }
}
