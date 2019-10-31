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
        fragment.switchCamera();
    }

    @PluginMethod()
    public void capture(PluginCall call) {
        saveCall(call);

        fragment.takePicture(0, 0, 85);
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

        fragment = new CameraActivity();
        fragment.setEventListener(this);
        fragment.defaultCamera = position;
        fragment.tapToTakePicture = false;
        fragment.dragEnabled = false;
        fragment.tapToFocus = true;
        fragment.disableExifHeaderStripping = true;
        fragment.storeToFile = false;
        fragment.toBack = true;

        bridge.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Display defaultDisplay = getBridge().getActivity().getWindowManager().getDefaultDisplay();
                final Point size = new Point();
                defaultDisplay.getSize(size);



                DisplayMetrics metrics = getBridge().getActivity().getResources().getDisplayMetrics();
                // offset
                int computedX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 0, metrics);
                int computedY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 0, metrics);

                // size
                int computedWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, size.x, metrics);
                int computedHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, size.y, metrics);

                fragment.setRect(computedX, computedY, computedWidth, computedHeight);

                FrameLayout containerView = getBridge().getActivity().findViewById(containerViewId);
                if(containerView == null){
                    containerView = new FrameLayout(getActivity().getApplicationContext());
                    containerView.setId(containerViewId);

                    getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
                    ((ViewGroup)getBridge().getWebView().getParent()).addView(containerView);
                    getBridge().getWebView().getParent().bringChildToFront(getBridge().getWebView());


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
}
