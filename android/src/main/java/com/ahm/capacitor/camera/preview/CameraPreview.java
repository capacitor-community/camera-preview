package com.ahm.capacitor.camera.preview;

import static android.Manifest.permission.CAMERA;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

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

@CapacitorPlugin(name = "CameraPreview", permissions = { @Permission(strings = { CAMERA }, alias = CameraPreview.CAMERA_PERMISSION_ALIAS) })
public class CameraPreview extends Plugin implements KNewCameraActivity.CameraPreviewListener {

    static final String CAMERA_PERMISSION_ALIAS = "camera";

//    private static String VIDEO_FILE_PATH = "";
//    private static String VIDEO_FILE_EXTENSION = ".mp4";

    private String captureCallbackId = "";
//    private String snapshotCallbackId = "";
//    private String recordCallbackId = "";
    private String cameraStartCallbackId = "";

    // keep track of previously specified orientation to support locking orientation:
    private int previousOrientationRequest = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

    private KNewCameraActivity fragment;
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
    public void flip(PluginCall call) {
        try {
            fragment.switchCamera();
            call.resolve();
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Camera flip exception: " + e);
            call.reject("failed to flip camera");
        }
    }

//    @PluginMethod
//    public void setOpacity(PluginCall call) {
//        if (!this.hasCamera(call)) {
//            call.error("Camera is not running");
//            return;
//        }
//
//        bridge.saveCall(call);
//        Float opacity = call.getFloat("opacity", 1F);
//        fragment.setOpacity(opacity);
//    }

    @PluginMethod
    public void capture(PluginCall call) {
        if (!this.hasCamera(call)) {
            call.reject("Camera is not running");
            return;
        }
        bridge.saveCall(call);
        captureCallbackId = call.getCallbackId();

//        Integer quality = call.getInt("quality", 85);
//        Integer width = call.getInt("width", 0);
//        Integer height = call.getInt("height", 0);
        fragment.takePicture();
    }

//    @PluginMethod
//    public void captureSample(PluginCall call) {
//        if (!this.hasCamera(call)) {
//            call.reject("Camera is not running");
//            return;
//        }
//        bridge.saveCall(call);
//        snapshotCallbackId = call.getCallbackId();
//
//        Integer quality = call.getInt("quality", 85);
//        fragment.takeSnapshot(quality);
//    }

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

                        if (containerView != null) {
                            ((ViewGroup) getBridge().getWebView().getParent()).removeView(containerView);
                            getBridge().getWebView().setBackgroundColor(Color.WHITE);
                            FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                            fragmentTransaction.remove(fragment);
                            fragmentTransaction.commit();
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
        if (!this.hasCamera(call)) {
            call.reject("Camera is not running");
            return;
        }

        List<String> jsonFlashModes = fragment.getSupportedFlashModes(getContext());

        JSObject jsObject = new JSObject();
        jsObject.put("result", jsonFlashModes);
        call.resolve(jsObject);
    }

    @PluginMethod
    public void setFlashMode(PluginCall call) {
        if (!this.hasCamera(call)) {
            call.reject("Camera is not running");
            return;
        }

        String flashMode = call.getString("flashMode");
        if (flashMode == null || flashMode.isEmpty()) {
            call.reject("flashMode required parameter is missing");
            return;
        }

        fragment.turnOnFlash();
//        Camera camera = fragment.getCamera();
//        Camera.Parameters params = camera.getParameters();
//
//        List<String> supportedFlashModes;
//        supportedFlashModes = camera.getParameters().getSupportedFlashModes();
//        if (supportedFlashModes.contains(flashMode)) {
//            params.setFlashMode(flashMode);
//        } else {
//            call.reject("Flash mode not recognised: " + flashMode);
//            return;
//        }
//
//        fragment.setCameraParameters(params);

        call.resolve();
    }

//    @PluginMethod
//    public void startRecordVideo(final PluginCall call) {
//        if (!this.hasCamera(call)) {
//            call.reject("Camera is not running");
//            return;
//        }
//        final String filename = "videoTmp";
//        VIDEO_FILE_PATH = getActivity().getCacheDir().toString() + "/";
//
//        final String position = call.getString("position", "front");
//        final Integer width = call.getInt("width", 0);
//        final Integer height = call.getInt("height", 0);
//        final Boolean withFlash = call.getBoolean("withFlash", false);
//        final Integer maxDuration = call.getInt("maxDuration", 0);
//        // final Integer quality = call.getInt("quality", 0);
//        bridge.saveCall(call);
//        recordCallbackId = call.getCallbackId();
//
//        bridge
//            .getActivity()
//            .runOnUiThread(
//                new Runnable() {
//                    @Override
//                    public void run() {
//                        // fragment.startRecord(getFilePath(filename), position, width, height, quality, withFlash);
//                        fragment.startRecord(getFilePath(filename), position, width, height, 70, withFlash, maxDuration);
//                    }
//                }
//            );
//
//        call.resolve();
//    }

//    @PluginMethod
//    public void stopRecordVideo(PluginCall call) {
//        if (!this.hasCamera(call)) {
//            call.reject("Camera is not running");
//            return;
//        }
//
//        System.out.println("stopRecordVideo - Callbackid=" + call.getCallbackId());
//
//        bridge.saveCall(call);
//        recordCallbackId = call.getCallbackId();
//
//        // bridge.getActivity().runOnUiThread(new Runnable() {
//        //     @Override
//        //     public void run() {
//        //         fragment.stopRecord();
//        //     }
//        // });
//
//        fragment.stopRecord();
//        // call.resolve();
//    }

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
        previousOrientationRequest = getBridge().getActivity().getRequestedOrientation();

        fragment = new KNewCameraActivity();
        fragment.setEventListener(this);
        fragment.setDefaultCamera(position.equals("front") ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA);
//        fragment.tapToTakePicture = false;
//        fragment.dragEnabled = false;
//        fragment.tapToFocus = true;
//        fragment.disableExifHeaderStripping = disableExifHeaderStripping;
//        fragment.storeToFile = storeToFile;
        fragment.setToBack(toBack);
//        fragment.enableOpacity = enableOpacity;
//        fragment.enableZoom = enableZoom;

        bridge
            .getActivity()
            .runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        DisplayMetrics metrics = getBridge().getActivity().getResources().getDisplayMetrics();
                        // lock orientation if specified in options:
                        if (lockOrientation) {
                            getBridge().getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
                        }

                        // offset
                        int computedX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, metrics);
                        int computedY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, y, metrics);

                        // size
                        int computedWidth;
                        int computedHeight;
                        int computedPaddingBottom;

                        if (paddingBottom != 0) {
                            computedPaddingBottom = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, paddingBottom, metrics);
                        } else {
                            computedPaddingBottom = 0;
                        }

                        if (width != 0) {
                            computedWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, metrics);
                        } else {
                            Display defaultDisplay = getBridge().getActivity().getWindowManager().getDefaultDisplay();
                            final Point size = new Point();
                            defaultDisplay.getSize(size);

                            computedWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, size.x, metrics);
                        }

                        if (height != 0) {
                            computedHeight =
                                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, metrics) - computedPaddingBottom;
                        } else {
                            Display defaultDisplay = getBridge().getActivity().getWindowManager().getDefaultDisplay();
                            final Point size = new Point();
                            defaultDisplay.getSize(size);

                            computedHeight =
                                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, size.y, metrics) - computedPaddingBottom;
                        }

                        fragment.setRect(computedX, computedY, computedWidth, computedHeight);

                        FrameLayout containerView = getBridge().getActivity().findViewById(containerViewId);
                        if (containerView == null) {
                            containerView = new FrameLayout(getActivity().getApplicationContext());
                            containerView.setId(containerViewId);

                            getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
                            ((ViewGroup) getBridge().getWebView().getParent()).addView(containerView);
                            if (toBack == true) {
                                getBridge().getWebView().getParent().bringChildToFront(getBridge().getWebView());
                                setupBroadcast();
                            }

                            FragmentManager fragmentManager = getBridge().getActivity().getSupportFragmentManager();
                            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                            fragmentTransaction.add(containerView.getId(), fragment);
                            fragmentTransaction.commit();

                            // NOTE: we don't return invoke call.resolve here because it must be invoked in onCameraStarted
                            // otherwise the plugin start method might resolve/return before the camera is actually set in CameraActivity
                            // onResume method (see this line mCamera = Camera.open(defaultCameraId);) and the next subsequent plugin
                            // method invocations (for example, getSupportedFlashModes) might fails with "Camera is not running" error
                            // because camera is not available yet and hasCamera method will return false
                            // Please also see https://developer.android.com/reference/android/hardware/Camera.html#open%28int%29
                            bridge.saveCall(call);
                            cameraStartCallbackId = call.getCallbackId();
                        } else {
                            call.reject("camera already started");
                        }
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
        bridge.getSavedCall(captureCallbackId).resolve(jsObject);
    }

    @Override
    public void onPictureTakenError(String message) {
        bridge.getSavedCall(captureCallbackId).reject(message);
    }

//    @Override
//    public void onSnapshotTaken(String originalPicture) {
//        JSObject jsObject = new JSObject();
//        jsObject.put("value", originalPicture);
//        bridge.getSavedCall(snapshotCallbackId).resolve(jsObject);
//    }
//
//    @Override
//    public void onSnapshotTakenError(String message) {
//        bridge.getSavedCall(snapshotCallbackId).reject(message);
//    }

//    @Override
//    public void onFocusSet(int pointX, int pointY) {}
//
//    @Override
//    public void onFocusSetError(String message) {}

//    @Override
//    public void onBackButton() {}

    @Override
    public void onCameraStarted() {
        PluginCall pluginCall = bridge.getSavedCall(cameraStartCallbackId);
        pluginCall.resolve();
        bridge.releaseCall(pluginCall);
    }

    @Override
    public void onCameraDetected(@NonNull String rotation, Rect bounds) {
        JSObject jsObject = new JSObject();
        jsObject.put("rotation", rotation);
        if (bounds != null) {
            jsObject.put("x", bounds.left);
            jsObject.put("y", bounds.top);
            jsObject.put("width", bounds.right - bounds.left);
            jsObject.put("height", bounds.bottom - bounds.top);
        } else {
            jsObject.put("x", 0);
            jsObject.put("y", 0);
            jsObject.put("width", 0);
            jsObject.put("height", 0);
        }
        notifyListeners("cameraDetected", jsObject);
    }

//    @Override
//    public void onStartRecordVideo() {}
//
//    @Override
//    public void onStartRecordVideoError(String message) {
//        bridge.getSavedCall(recordCallbackId).reject(message);
//    }

//    @Override
//    public void onStopRecordVideo(String file) {
//        PluginCall pluginCall = bridge.getSavedCall(recordCallbackId);
//        JSObject jsObject = new JSObject();
//        jsObject.put("videoFilePath", file);
//        pluginCall.resolve(jsObject);
//    }

//    @Override
//    public void onStopRecordVideoError(String error) {
//        bridge.getSavedCall(recordCallbackId).reject(error);
//    }

    private boolean hasView(PluginCall call) {
        return fragment != null;
    }

    private boolean hasCamera(PluginCall call) {
        if (!this.hasView(call)) {
            return false;
        }

        return fragment.hasCamera();
    }

//    private String getFilePath(String filename) {
//        String fileName = filename;
//
//        int i = 1;
//
//        while (new File(VIDEO_FILE_PATH + fileName + VIDEO_FILE_EXTENSION).exists()) {
//            // Add number suffix if file exists
//            fileName = filename + '_' + i;
//            i++;
//        }
//
//        return VIDEO_FILE_PATH + fileName + VIDEO_FILE_EXTENSION;
//    }

    private void setupBroadcast() {
        /** When touch event is triggered, relay it to camera view if needed so it can support pinch zoom */

        getBridge().getWebView().setClickable(true);
        getBridge()
            .getWebView()
            .setOnTouchListener(
                new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if ((null != fragment) && (fragment.getToBack() == true)) {
//                            fragment.frameContainerLayout.dispatchTouchEvent(event);
                        }
                        return false;
                    }
                }
            );
    }
}
