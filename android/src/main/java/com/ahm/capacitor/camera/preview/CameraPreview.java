package com.ahm.capacitor.camera.preview;

import android.Manifest;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.io.File;

@NativePlugin(
        permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        },
        requestCodes = {
                CameraPreview.REQUEST_CAMERA_PERMISSION
        }
)
public class CameraPreview extends Plugin implements CameraActivity.CameraPreviewListener {
    private static String VIDEO_FILE_PATH = "";
    private static String VIDEO_FILE_EXTENSION = ".mp4";
    static final int REQUEST_CAMERA_PERMISSION = 1234;

    private CameraActivity fragment;
    private int containerViewId = 20;

    @PluginMethod()
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", value);
        call.success(ret);
    }

    @PluginMethod()
    public void start(PluginCall call) {
        saveCall(call);

        if (hasRequiredPermissions()) {
            startCamera(call);
        } else {
            pluginRequestPermissions(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, REQUEST_CAMERA_PERMISSION);
        }
    }

    @PluginMethod
    public void flip(PluginCall call) {
        try {
            fragment.switchCamera();
            call.resolve();
        } catch (Exception e) {
            call.reject("failed to flip camera");
        }
    }

    @PluginMethod()
    public void capture(PluginCall call) {
        if(this.hasCamera(call) == false){
            call.error("Camera is not running");
            return;
        }

        saveCall(call);

        Integer quality = call.getInt("quality", 85);
        // Image Dimensions - Optional
        Integer width = call.getInt("width", 0);
        Integer height = call.getInt("height", 0);
        fragment.takePicture(width, height, quality);
    }

    @PluginMethod()
    public void stop(final PluginCall call) {
        bridge.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                FrameLayout containerView = getBridge().getActivity().findViewById(containerViewId);

                if (containerView != null) {
                    ((ViewGroup)getBridge().getWebView().getParent()).removeView(containerView);
                    getBridge().getWebView().setBackgroundColor(Color.WHITE);
                    FragmentManager fragmentManager = getActivity().getFragmentManager();
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                    fragmentTransaction.remove(fragment);
                    fragmentTransaction.commit();
                    fragment = null;

                    call.success();
                } else {
                    call.reject("camera already stopped");
                }
            }
        });
    }

    @PluginMethod()
    public void getSupportedFlashModes(PluginCall call) {
        if(this.hasCamera(call) == false){
            call.error("Camera is not running");
            return;
        }

        Camera camera = fragment.getCamera();
        Camera.Parameters params = camera.getParameters();
        List<String> supportedFlashModes;
        supportedFlashModes = params.getSupportedFlashModes();
        JSONArray jsonFlashModes = new JSONArray();

        if (supportedFlashModes != null) {
            for (int i=0; i<supportedFlashModes.size(); i++) {
                jsonFlashModes.put(new String(supportedFlashModes.get(i)));
            }
        }

        JSObject jsObject = new JSObject();
        jsObject.put("result", jsonFlashModes);
        call.success(jsObject);

    }

    @PluginMethod()
    public void setFlashMode(PluginCall call) {
        if(this.hasCamera(call) == false){
            call.error("Camera is not running");
            return;
        }

        String flashMode = call.getString("flashMode");
        if(flashMode == null || flashMode.isEmpty() == true) {
            call.error("flashMode required parameter is missing");
            return;
        }

        Camera camera = fragment.getCamera();
        Camera.Parameters params = camera.getParameters();

        List<String> supportedFlashModes;
        supportedFlashModes = camera.getParameters().getSupportedFlashModes();
        if (supportedFlashModes.indexOf(flashMode) > -1) {
            params.setFlashMode(flashMode);
        } else {
            call.error("Flash mode not recognised: " + flashMode);
            return;
        }

        fragment.setCameraParameters(params);

        call.success();
    }

    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            boolean permissionsGranted = true;
            for (int grantResult: grantResults) {
                if (grantResult != 0) {
                    permissionsGranted = false;
                }
            }

            PluginCall savedCall = getSavedCall();
            if (permissionsGranted) {
                startCamera(savedCall);
            } else {
                savedCall.reject("permission failed");
            }
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
        final Boolean disableExifHeaderStripping = call.getBoolean("disableExifHeaderStripping", true);

        fragment = new CameraActivity();
        fragment.setEventListener(this);
        fragment.defaultCamera = position;
        fragment.tapToTakePicture = false;
        fragment.dragEnabled = false;
        fragment.tapToFocus = true;
        fragment.disableExifHeaderStripping = disableExifHeaderStripping;
        fragment.storeToFile = storeToFile;
        fragment.toBack = toBack;

        bridge.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                DisplayMetrics metrics = getBridge().getActivity().getResources().getDisplayMetrics();
                // offset
                int computedX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, metrics);
                int computedY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, y, metrics);

                // size
                int computedWidth;
                int computedHeight;
                int computedPaddingBottom;

                if(paddingBottom != 0) {
                    computedPaddingBottom = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, paddingBottom, metrics);
                } else {
                    computedPaddingBottom = 0;
                }

                if(width != 0) {
                    computedWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, metrics);
                } else {
                    Display defaultDisplay = getBridge().getActivity().getWindowManager().getDefaultDisplay();
                    final Point size = new Point();
                    defaultDisplay.getSize(size);

                    computedWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, size.x, metrics);
                }

                if(height != 0) {
                    computedHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, metrics) - computedPaddingBottom;
                } else {
                    Display defaultDisplay = getBridge().getActivity().getWindowManager().getDefaultDisplay();
                    final Point size = new Point();
                    defaultDisplay.getSize(size);

                    computedHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, size.y, metrics) - computedPaddingBottom;
                }

                fragment.setRect(computedX, computedY, computedWidth, computedHeight);

                FrameLayout containerView = getBridge().getActivity().findViewById(containerViewId);
                if(containerView == null){
                    containerView = new FrameLayout(getActivity().getApplicationContext());
                    containerView.setId(containerViewId);

                    getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
                    ((ViewGroup)getBridge().getWebView().getParent()).addView(containerView);
                    if(toBack == true) {
                        getBridge().getWebView().getParent().bringChildToFront(getBridge().getWebView());
                    }

                    FragmentManager fragmentManager = getBridge().getActivity().getFragmentManager();
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                    fragmentTransaction.add(containerView.getId(), fragment);
                    fragmentTransaction.commit();

                    call.success();
                } else {
                    call.reject("camera already started");
                }
            }
        });
    }


    @Override
    protected void handleOnResume() {
        super.handleOnResume();
    }

    @Override
    public void onPictureTaken(String originalPicture) {
        JSObject jsObject = new JSObject();
        jsObject.put("value", originalPicture);
        getSavedCall().success(jsObject);
    }

    @Override
    public void onPictureTakenError(String message) {
        getSavedCall().reject(message);
    }

    @Override
    public void onSnapshotTaken(String originalPicture) {
        JSONArray data = new JSONArray();
        data.put(originalPicture);

        PluginCall call = getSavedCall();

        JSObject jsObject = new JSObject();
        jsObject.put("result", data);
        call.success(jsObject);
    }

    @Override
    public void onSnapshotTakenError(String message) {
        getSavedCall().reject(message);
    }

    @Override
    public void onFocusSet(int pointX, int pointY) {

    }

    @Override
    public void onFocusSetError(String message) {

    }

    @Override
    public void onBackButton() {

    }

    @Override
    public void onCameraStarted() {
        PluginCall pluginCall = getSavedCall();
        System.out.println("camera started");
        pluginCall.success();
    }

    @Override
    public void onStartRecordVideo() {

    }
    @Override
    public void onStartRecordVideoError(String message) {
        getSavedCall().reject(message);
    }
    @Override
    public void onStopRecordVideo(String file) {
        PluginCall pluginCall = getSavedCall();
        JSObject jsObject = new JSObject();
        jsObject.put("videoFilePath", file);
        pluginCall.success(jsObject);
    }
    @Override
    public void onStopRecordVideoError(String error) {
        getSavedCall().reject(error);
    }

    private boolean hasView(PluginCall call) {
        if(fragment == null) {
            return false;
        }

        return true;
    }

    private boolean hasCamera(PluginCall call) {
        if(this.hasView(call) == false){
            return false;
        }

        if(fragment.getCamera() == null) {
            return false;
        }

        return true;
    }

    @PluginMethod()
    public void startRecordVideo(final PluginCall call) {
        if(this.hasCamera(call) == false){
            call.error("Camera is not running");
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

        bridge.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // fragment.startRecord(getFilePath(filename), position, width, height, quality, withFlash);
                fragment.startRecord(getFilePath(filename), position, width, height, 70, withFlash, maxDuration);
            }
        });

        call.success();
    }

    @PluginMethod()
    public void stopRecordVideo(PluginCall call) {
       if(this.hasCamera(call) == false){
            call.error("Camera is not running");
            return;
        }

        saveCall(call);

        // bridge.getActivity().runOnUiThread(new Runnable() {
        //     @Override
        //     public void run() {
        //         fragment.stopRecord();
        //     }
        // });

        fragment.stopRecord();
        // call.success();
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
}
