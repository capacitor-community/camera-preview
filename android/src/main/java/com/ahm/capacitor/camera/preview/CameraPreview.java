package com.ahm.capacitor.camera.preview;

import static android.Manifest.permission.CAMERA;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;

import com.ahm.capacitor.camera.preview.camera1api.Camera1Preview;
import com.ahm.capacitor.camera.preview.camera2api.Camera2Preview;
import com.getcapacitor.Logger;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;


@CapacitorPlugin(name = "CameraPreview", permissions = { @Permission(strings = { CAMERA }, alias = CameraPreview.CAMERA_PERMISSION_ALIAS) })
public class CameraPreview extends Plugin  {

    static final String CAMERA_PERMISSION_ALIAS = "camera";

    static final int CAMERA_1_API = 1;
    static final int CAMERA_2_API = 2;

    private int preferredApi = CAMERA_2_API;

    private Camera1Preview camera1Api;
    private Camera2Preview camera2Api;

    @Override
    public void load() {
        camera1Api = new Camera1Preview(this);
        camera2Api = new Camera2Preview(this);
    }

    @PluginMethod
    public void setApi(PluginCall call) {
        try{
            int api = call.getInt("api", CAMERA_2_API);
            if(api != CAMERA_1_API && api != CAMERA_2_API){
                call.reject("Invalid API value. Use 1 for Camera1 API or 2 for Camera2 API.");
                return;
            }
            preferredApi = api;
            Logger.debug(getLogTag(), "Preferred camera API set to: " + (preferredApi == CAMERA_1_API ? "Camera1 API" : "Camera2 API"));
            call.resolve();
        } catch (Exception e) {
            Logger.error(getLogTag(), "Set API exception", e);
            call.reject("failed to set API: " + e.getMessage());
        }
    }

    @PluginMethod
    public void start(PluginCall call) {
        try{
            if (PermissionState.GRANTED.equals(getPermissionState(CAMERA_PERMISSION_ALIAS))) {
                if(shouldUseCamera2Api()){
                    camera2Api.startCamera(call);
                }else{
                    camera1Api.startCamera(call);
                }
            } else {
                requestPermissionForAlias(CAMERA_PERMISSION_ALIAS, call, "handleCameraPermissionResult");
            }
        }catch (Exception e) {
            Logger.debug(getLogTag(), "Start camera exception: " + e);
            call.reject("failed to start camera: " + e.getMessage());
        }
    }

    @PluginMethod
    public void flip(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.flip(call);
        }else{
            camera1Api.flip(call);
        }
    }

    @PluginMethod
    public void getCameraCharacteristics(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.getCameraCharacteristics(call);
        }else{
            call.reject("getCameraCharacteristics is not supported on Camera1 API");
        }
    }

    @PluginMethod
    public void setOpacity(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.setOpacity(call);
        }else{
            camera1Api.setOpacity(call);
        }
    }

    @PluginMethod
    public void setZoom(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.setZoom(call);
        }else{
            camera1Api.setZoom(call);
        }
    }

    @PluginMethod
    public void getZoom(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.getZoom(call);
        }else{
            camera1Api.getZoom(call);
        }
    }

    @PluginMethod
    public void getMaxZoom(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.getMaxZoom(call);
        }else{
            camera1Api.getMaxZoom(call);
        }
    }

    @PluginMethod
    public void getMaxZoomLimit(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.getMaxZoomLimit(call);
        }else{
            call.reject("getMaxZoomLimit is not supported on Camera1 API");
        }
    }

    @PluginMethod
    public void setMaxZoomLimit(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.setMaxZoomLimit(call);
        }else{
            call.reject("setMaxZoomLimit is not supported on Camera1 API");
        }
    }

    @PluginMethod
    public void capture(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.capture(call);
        }else{
            camera1Api.capture(call);
        }
    }

    @PluginMethod
    public void captureSample(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.captureSample(call);
        }else{
            camera1Api.captureSample(call);
        }
    }

    @PluginMethod
    public void stop(final PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.stop(call);
        }else{
            camera1Api.stop(call);
        }
    }

    @PluginMethod
    public void getSupportedFlashModes(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.getSupportedFlashModes(call);
        }else{
            camera1Api.getSupportedFlashModes(call);
        }
    }

    @PluginMethod
    public void setFlashMode(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.setFlashMode(call);
        }else{
            camera1Api.setFlashMode(call);
        }
    }

    @PluginMethod
    public void startRecordVideo(final PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.startRecordVideo(call);
        }else{
            camera1Api.startRecordVideo(call);
        }
    }

    @PluginMethod
    public void stopRecordVideo(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.stopRecordVideo(call);
        }else{
            camera1Api.stopRecordVideo(call);
        }
    }

    @PermissionCallback
    private void handleCameraPermissionResult(PluginCall call) {
        if(shouldUseCamera2Api()){
            camera2Api.handleCameraPermissionResult(call);
        }else{
            camera1Api.handleCameraPermissionResult(call);
        }
    }


    @Override
    protected void handleOnResume() {
        super.handleOnResume();
    }


    @Override
    protected void handleOnConfigurationChanged(Configuration newConfig) {
        super.handleOnConfigurationChanged(newConfig);

        if(shouldUseCamera2Api()){
            camera2Api.handleOnConfigurationChanged(newConfig);
        }
        // Camera1 API does not handle configuration changes
    }

    private boolean shouldUseCamera2Api() {
        if(preferredApi == CAMERA_1_API){
            return false;
        }
        return hasCamera2ApiAvailability();
    }

    private boolean hasCamera2ApiAvailability() {
        int supportLevel = getCamera2SupportLevel();
            return supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED ||
                   supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3);
    }

    @PluginMethod
    public void getCamera2SupportLevel(PluginCall call) {
        try{
            int supportLevel = getCamera2SupportLevel();
            call.resolve(  com.getcapacitor.JSObject.fromJSONObject(  new org.json.JSONObject()
                    .put("level", supportLevel)
                    .put("name", getNameForLevel(supportLevel))
            ));
        } catch (Exception e) {
            Logger.error(getLogTag(), "Get Camera2 support level exception", e);
            call.reject("failed to get Camera2 support level: " + e.getMessage());
        }

    }

    private int getCamera2SupportLevel() {
        int supportLevel = -1;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                CameraManager manager = (CameraManager) this.bridge.getContext().getSystemService(Context.CAMERA_SERVICE);
                if (manager == null || manager.getCameraIdList() == null || manager.getCameraIdList().length == 0) {
                    Logger.warn(getLogTag(), "Camera2 API is not available on this device. No camera found.");
                    return supportLevel;
                }
                for (String cameraId : manager.getCameraIdList()) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                    int level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    Logger.debug(getLogTag(), "Camera ID: " + cameraId + ", Camera2Api Level: " + getNameForLevel(level));

                    if(level > supportLevel) {
                        supportLevel = level;
                    }
                }
            } else {
                Logger.warn(getLogTag(), "Camera2 API is not available on this Android version. Minimum required version is Android P (API 28).");
            }
            Logger.debug(getLogTag(), "Camera2 API support level: " + getNameForLevel(supportLevel));
        } catch (Exception e) {
            Logger.error(getLogTag(), "Error checking Camera2 API availability", e);
        }
        return supportLevel;
    }

    private String getNameForLevel(int level) {
        return CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY == level ?
                "LEGACY" :
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED == level ?
                        "LIMITED" :
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL == level ?
                                "FULL" :
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 == level ?
                                        "LEVEL_3" :
                                        "UNKNOWN";
    }
}
