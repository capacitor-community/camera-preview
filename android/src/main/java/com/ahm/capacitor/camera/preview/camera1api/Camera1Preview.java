package com.ahm.capacitor.camera.preview.camera1api;

import static android.Manifest.permission.CAMERA;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.ahm.capacitor.camera.preview.CameraPreview;
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

public class Camera1Preview implements Camera1Activity.CameraPreviewListener {

    private CameraPreview plugin;
    static final String CAMERA_PERMISSION_ALIAS = "camera";

    private static String VIDEO_FILE_PATH = "";
    private static String VIDEO_FILE_EXTENSION = ".mp4";

    private String captureCallbackId = "";
    private String snapshotCallbackId = "";
    private String recordCallbackId = "";
    private String cameraStartCallbackId = "";

    // keep track of previously specified orientation to support locking orientation:
    private int previousOrientationRequest = ActivityInfo. SCREEN_ORIENTATION_UNSPECIFIED;

    private Camera1Activity fragment;
    private int containerViewId = 20;

    public Camera1Preview(CameraPreview plugin) {
        this.plugin = plugin;
    }

    private String getLogTag() {
        return "Camera1Preview";
    }

    public void start(PluginCall call) {
        startCamera(call);
    }

    public void flip(PluginCall call) {
        try {
            fragment.switchCamera();
            call.resolve();
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Camera flip exception: " + e);
            call.reject("failed to flip camera");
        }
    }

    public void setOpacity(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }

        plugin.getBridge().saveCall(call);
        Float opacity = call.getFloat("opacity", 1F);
        fragment.setOpacity(opacity);
    }

    public void setZoom(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }

        try {
            int zoom = call.getInt("zoom", 0);
            Camera camera = fragment.getCamera();
            Camera.Parameters params = camera.getParameters();
            if(params.isZoomSupported()) {
                params.setZoom(zoom);
                fragment.setCameraParameters(params);
                call.resolve();
            } else {
                call.reject("Zoom not supported");
            }
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Set camera zoom exception: " + e);
            call.reject("failed to zoom camera");
        }
    }

    public void getZoom(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }

        try {
            Camera camera = fragment.getCamera();
            Camera.Parameters params = camera.getParameters();
            if(params.isZoomSupported()) {
                int currentZoom = params.getZoom();
                JSObject jsObject = new JSObject();
                jsObject.put("value", currentZoom);
                call.resolve(jsObject);
            } else {
                call.reject("Zoom not supported");
            }
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Get camera zoom exception: " + e);
            call.reject("failed to get camera zoom");
        }
    }

    public void getMaxZoom(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }

        try {
            Camera camera = fragment.getCamera();
            Camera.Parameters params = camera.getParameters();
            if(params.isZoomSupported()) {
                int maxZoom = params.getMaxZoom();
                JSObject jsObject = new JSObject();
                jsObject.put("value", maxZoom);
                call.resolve(jsObject);
            } else {
                call.reject("Zoom not supported");
            }
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Get max camera zoom exception: " + e);
            call.reject("failed to get max camera zoom");
        }
    }

    public void capture(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }
        plugin.getBridge().saveCall(call);
        captureCallbackId = call.getCallbackId();

        Integer quality = call.getInt("quality", 85);
        // Image Dimensions - Optional
        Integer width = call.getInt("width", 0);
        Integer height = call.getInt("height", 0);
        fragment.takePicture(width, height, quality);
    }

    public void captureSample(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }
        plugin.getBridge().saveCall(call);
        snapshotCallbackId = call.getCallbackId();

        Integer quality = call.getInt("quality", 85);
        fragment.takeSnapshot(quality);
    }

    public void stop(final PluginCall call) {
        plugin.getBridge()
            .getActivity()
            .runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        FrameLayout containerView = plugin.getBridge().getActivity().findViewById(containerViewId);

                        // allow orientation changes after closing camera:
                        plugin.getBridge().getActivity().setRequestedOrientation(previousOrientationRequest);

                        if (containerView != null) {
                            ((ViewGroup) plugin.getBridge().getWebView().getParent()).removeView(containerView);
                            plugin.getBridge().getWebView().setBackgroundColor(Color.WHITE);
                            FragmentManager fragmentManager =  plugin.getActivity().getFragmentManager();
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

    public void startRecordVideo(final PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }
        final String filename = "videoTmp";
        VIDEO_FILE_PATH =  plugin.getActivity().getCacheDir().toString() + "/";

        final String position = call.getString("position", "front");
        final Integer width = call.getInt("width", 0);
        final Integer height = call.getInt("height", 0);
        final Boolean withFlash = call.getBoolean("withFlash", false);
        final Integer maxDuration = call.getInt("maxDuration", 0);
        // final Integer quality = call.getInt("quality", 0);
        plugin.getBridge().saveCall(call);
        recordCallbackId = call.getCallbackId();

        plugin.getBridge()
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

    public void stopRecordVideo(PluginCall call) {
        if (this.hasCamera(call) == false) {
            call.reject("Camera is not running");
            return;
        }

        System.out.println("stopRecordVideo - Callbackid=" + call.getCallbackId());

        plugin.getBridge().saveCall(call);
        recordCallbackId = call.getCallbackId();

        // plugin.getBridge().getActivity().runOnUiThread(new Runnable() {
        //     @Override
        //     public void run() {
        //         fragment.stopRecord();
        //     }
        // });

        fragment.stopRecord();
        // call.resolve();
    }

    public void handleCameraPermissionResult(PluginCall call) {
        startCamera(call);
    }

    public void startCamera(final PluginCall call) {
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
        previousOrientationRequest = plugin.getBridge().getActivity().getRequestedOrientation();

        fragment = new Camera1Activity();
        fragment.setEventListener(this);
        fragment.defaultCamera = position;
        fragment.tapToTakePicture = false;
        fragment.dragEnabled = false;
        fragment.tapToFocus = true;
        fragment.disableExifHeaderStripping = disableExifHeaderStripping;
        fragment.storeToFile = storeToFile;
        fragment.toBack = toBack;
        fragment.enableOpacity = enableOpacity;
        fragment.enableZoom = enableZoom;

        plugin.getBridge()
            .getActivity()
            .runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        DisplayMetrics metrics = plugin.getBridge().getActivity().getResources().getDisplayMetrics();
                        // lock orientation if specified in options:
                        if (lockOrientation) {
                            plugin.getBridge().getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
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
                            Display defaultDisplay = plugin.getBridge().getActivity().getWindowManager().getDefaultDisplay();
                            final Point size = new Point();
                            defaultDisplay.getSize(size);

                            computedWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, size.x, metrics);
                        }

                        if (height != 0) {
                            computedHeight =
                                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, metrics) - computedPaddingBottom;
                        } else {
                            Display defaultDisplay = plugin.getBridge().getActivity().getWindowManager().getDefaultDisplay();
                            final Point size = new Point();
                            defaultDisplay.getSize(size);

                            computedHeight =
                                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, size.y, metrics) - computedPaddingBottom;
                        }

                        fragment.setRect(computedX, computedY, computedWidth, computedHeight);

                        FrameLayout containerView = plugin.getBridge().getActivity().findViewById(containerViewId);
                        if (containerView == null) {
                            containerView = new FrameLayout(plugin.getActivity().getApplicationContext());
                            containerView.setId(containerViewId);

                            plugin.getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
                            ((ViewGroup) plugin.getBridge().getWebView().getParent()).addView(containerView);
                            if (toBack == true) {
                                plugin.getBridge().getWebView().getParent().bringChildToFront(plugin.getBridge().getWebView());
                                setupBroadcast();
                            }

                            FragmentManager fragmentManager = plugin.getBridge().getActivity().getFragmentManager();
                            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                            fragmentTransaction.add(containerView.getId(), fragment);
                            fragmentTransaction.commit();

                            // NOTE: we don't return invoke call.resolve here because it must be invoked in onCameraStarted
                            // otherwise the plugin start method might resolve/return before the camera is actually set in Camera1Activity
                            // onResume method (see this line mCamera = Camera.open(defaultCameraId);) and the next subsequent plugin
                            // method invocations (for example, getSupportedFlashModes) might fails with "Camera is not running" error
                            // because camera is not available yet and hasCamera method will return false
                            // Please also see https://developer.android.com/reference/android/hardware/Camera.html#open%28int%29
                            plugin.getBridge().saveCall(call);
                            cameraStartCallbackId = call.getCallbackId();
                        } else {
                            call.reject("camera already started");
                        }
                    }
                }
            );
    }

    @Override
    public void onPictureTaken(String originalPicture) {
        JSObject jsObject = new JSObject();
        jsObject.put("value", originalPicture);
        plugin.getBridge().getSavedCall(captureCallbackId).resolve(jsObject);
    }

    @Override
    public void onPictureTakenError(String message) {
        plugin.getBridge().getSavedCall(captureCallbackId).reject(message);
    }

    @Override
    public void onSnapshotTaken(String originalPicture) {
        JSObject jsObject = new JSObject();
        jsObject.put("value", originalPicture);
        plugin.getBridge().getSavedCall(snapshotCallbackId).resolve(jsObject);
    }

    @Override
    public void onSnapshotTakenError(String message) {
        plugin.getBridge().getSavedCall(snapshotCallbackId).reject(message);
    }

    @Override
    public void onFocusSet(int pointX, int pointY) {}

    @Override
    public void onFocusSetError(String message) {}

    @Override
    public void onBackButton() {}

    @Override
    public void onCameraStarted() {
        PluginCall pluginCall = plugin.getBridge().getSavedCall(cameraStartCallbackId);
        pluginCall.resolve();
        plugin.getBridge().releaseCall(pluginCall);
    }

    @Override
    public void onStartRecordVideo() {}

    @Override
    public void onStartRecordVideoError(String message) {
        plugin.getBridge().getSavedCall(recordCallbackId).reject(message);
    }

    @Override
    public void onStopRecordVideo(String file) {
        PluginCall pluginCall = plugin.getBridge().getSavedCall(recordCallbackId);
        JSObject jsObject = new JSObject();
        jsObject.put("videoFilePath", file);
        pluginCall.resolve(jsObject);
    }

    @Override
    public void onStopRecordVideoError(String error) {
        plugin.getBridge().getSavedCall(recordCallbackId).reject(error);
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

        plugin.getBridge().getWebView().setClickable(true);
        plugin.getBridge()
            .getWebView()
            .setOnTouchListener(
                new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if ((null != fragment) && (fragment.toBack == true)) {
                            fragment.frameContainerLayout.dispatchTouchEvent(event);
                        }
                        return false;
                    }
                }
            );
    }
}
