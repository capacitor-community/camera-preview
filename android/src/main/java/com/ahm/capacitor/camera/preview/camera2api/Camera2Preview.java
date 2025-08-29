package com.ahm.capacitor.camera.preview.camera2api;

import android.annotation.SuppressLint;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Size;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.ahm.capacitor.camera.preview.CameraPreview;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.PluginCall;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Set;

public class Camera2Preview implements Camera2Activity.CameraPreviewListener {

    private CameraPreview plugin;

    private static String VIDEO_FILE_PATH = "";
    private static String VIDEO_FILE_EXTENSION = ".mp4";

    private String captureCallbackId = "";
    private String snapshotCallbackId = "";
    private String recordCallbackId = "";
    private String cameraStartCallbackId = "";

    // keep track of previously specified orientation to support locking orientation:
    private int previousOrientationRequest = -1;

    private Camera2Activity fragment;

    private int containerViewId = 20;
    
    public Camera2Preview(CameraPreview plugin) {
        this.plugin = plugin;
    }
    
    private String getLogTag() {
        return "Camera2Preview";
    }

    public void start(PluginCall call) {
        startCamera(call);
    }

    public void flip(PluginCall call) {
        try {
            plugin.getBridge().saveCall(call);
            cameraStartCallbackId = call.getCallbackId();
            fragment.switchCamera();
            call.setKeepAlive(true);
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Camera flip exception: " + e);
            call.reject("failed to flip camera: " + e.getMessage());
        }
    }

    public void getCameraCharacteristics(PluginCall call) {
        try {
            // if device is running Android P or later, list available cameras and their focal lengths
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                CameraManager manager = (CameraManager) this.plugin.getBridge().getContext().getSystemService(Context.CAMERA_SERVICE);
                try {
                    JSONArray logicalCameras = new JSONArray();
                    String[] cameraIdList = manager.getCameraIdList();
                    for (String id : cameraIdList) {
                        /*
                         * Logical camera details
                         */
                        JSObject logicalCamera = new JSObject();
                        logicalCamera.put("LOGICAL_ID", id);
                        JSONArray physicalCameras = new JSONArray();

                        CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);

                        // INFO_SUPPORTED_HARDWARE_LEVEL
                        Integer supportLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                        if(supportLevel != null) logicalCamera.put("INFO_SUPPORTED_HARDWARE_LEVEL", supportLevel);

                        // LENS_FACING
                        Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                        if(lensFacing != null) logicalCamera.put("LENS_FACING", lensFacing);

                        // SENSOR_INFO_PHYSICAL_SIZE
                        SizeF sensorInfoPhysicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                        if(sensorInfoPhysicalSize != null) {
                            logicalCamera.put("SENSOR_INFO_PHYSICAL_SIZE_WIDTH", sensorInfoPhysicalSize.getWidth());
                            logicalCamera.put("SENSOR_INFO_PHYSICAL_SIZE_HEIGHT", sensorInfoPhysicalSize.getHeight());
                        }

                        // SENSOR_INFO_PIXEL_ARRAY_SIZE
                        Size sensorInfoPixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                        if(sensorInfoPixelSize != null) {
                            logicalCamera.put("SENSOR_INFO_PIXEL_ARRAY_SIZE_WIDTH", sensorInfoPixelSize.getWidth());
                            logicalCamera.put("SENSOR_INFO_PIXEL_ARRAY_SIZE_HEIGHT", sensorInfoPixelSize.getHeight());
                        }

                        // LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                        JSONArray focalLengthsArray = new JSONArray();
                        for (int focusId=0; focusId<focalLengths.length; focusId++) {
                            JSONObject focalLengthsData = new JSONObject();
                            focalLengthsData.put("FOCAL_LENGTH", new Double(focalLengths[focusId]));
                            focalLengthsArray.put(focalLengthsData);
                        }
                        logicalCamera.put("LENS_INFO_AVAILABLE_FOCAL_LENGTHS", focalLengthsArray);

                        /*
                         * Physical camera details
                         */
                        Set<String> physicalCameraIds = characteristics.getPhysicalCameraIds();
                        for (String physicalId : physicalCameraIds) {
                            JSObject physicalCamera = new JSObject();
                            physicalCamera.put("PHYSICAL_ID", physicalId);
                            
                            CameraCharacteristics physicalCharacteristics = manager.getCameraCharacteristics(physicalId);

                            float[] lensFocalLengths = physicalCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                            if (lensFocalLengths != null && focalLengths.length > 0) {
                                float focalLength = lensFocalLengths[0];
                                physicalCamera.put("LENS_INFO_AVAILABLE_FOCAL_LENGTHS", focalLength);
                            }

                            StreamConfigurationMap map = physicalCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                            if (map != null) {
                                int[] outputFormats = map.getOutputFormats();
                                JSONArray formats = new JSONArray();
                                for (int format : outputFormats) {
                                    formats.put(format);
                                }
                                physicalCamera.put("SCALER_STREAM_CONFIGURATION_MAP", formats);
                            }

                            Size[] outputSizes = map.getOutputSizes(256);
                            if(outputSizes != null && outputSizes.length > 0) {
                                JSONArray sizes = new JSONArray();
                                for (Size size : outputSizes) {
                                    JSONObject sizeObject = new JSONObject();
                                    sizeObject.put("WIDTH", size.getWidth());
                                    sizeObject.put("HEIGHT", size.getHeight());
                                    sizes.put(sizeObject);
                                }
                                physicalCamera.put("OUTPUT_SIZES", sizes);
                            }

                            Size[] inputSizes = map.getInputSizes(256);
                            if(inputSizes != null && inputSizes.length > 0) {
                                JSONArray sizes = new JSONArray();
                                for (Size size : inputSizes) {
                                    JSONObject sizeObject = new JSONObject();
                                    sizeObject.put("WIDTH", size.getWidth());
                                    sizeObject.put("HEIGHT", size.getHeight());
                                    sizes.put(sizeObject);
                                }
                                physicalCamera.put("INPUT_SIZES", sizes);
                            }

                            // get the list of available capabilities
                            int[] capabilities = physicalCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                            if(capabilities != null && capabilities.length > 0) {
                                JSONArray capabilitiesJson = new JSONArray();
                                for (int capability : capabilities) {
                                    String capabilityName;
                                    switch(capability){
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_RAW";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME";
                                            break;
                                        case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA:
                                            capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA";
                                            break;
                                        default:
                                            capabilityName = String.valueOf(capability);
                                    }

                                    capabilitiesJson.put(capabilityName);
                                }
                                physicalCamera.put("REQUEST_AVAILABLE_CAPABILITIES", capabilitiesJson);
                            }

                            int[] controlModes = physicalCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_MODES);
                            if(controlModes != null && controlModes.length > 0) {
                                JSONArray controlModesArray = new JSONArray();
                                for (int mode : controlModes) {
                                    controlModesArray.put(mode);
                                }
                                physicalCamera.put("CONTROL_AVAILABLE_MODES", controlModesArray);
                            }

                            int[] effects = physicalCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS);
                            if(effects != null && effects.length > 0) {
                                JSONArray effectsArray = new JSONArray();
                                for (int effect : effects) {
                                    effectsArray.put(effect);
                                }
                                physicalCamera.put("CONTROL_AVAILABLE_EFFECTS", effectsArray);
                            }

                            int[] sceneModes = physicalCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
                            if(sceneModes != null && sceneModes.length > 0) {
                                JSONArray sceneModesArray = new JSONArray();
                                for (int mode : sceneModes) {
                                    sceneModesArray.put(mode);
                                }
                                physicalCamera.put("CONTROL_AVAILABLE_SCENE_MODES", sceneModesArray);
                            }

                            int[] antiBandingModes = physicalCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES);
                            if(antiBandingModes != null && antiBandingModes.length > 0) {
                                JSONArray antiBandingModesArray = new JSONArray();
                                for (int mode : antiBandingModes) {
                                    antiBandingModesArray.put(mode);
                                }
                                physicalCamera.put("CONTROL_AE_AVAILABLE_ANTIBANDING_MODES", antiBandingModesArray);
                            }

                            int[] autoExposureModes = physicalCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
                            if(autoExposureModes != null && autoExposureModes.length > 0) {
                                JSONArray autoExposureModesArray = new JSONArray();
                                for (int mode : autoExposureModes) {
                                    autoExposureModesArray.put(mode);
                                }
                                physicalCamera.put("CONTROL_AE_AVAILABLE_MODES", autoExposureModesArray);
                            }

                            int[] autoFocusModes = physicalCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                            if(autoFocusModes != null && autoFocusModes.length > 0) {
                                JSONArray autoFocusModesArray = new JSONArray();
                                for (int mode : autoFocusModes) {
                                    autoFocusModesArray.put(mode);
                                }
                                physicalCamera.put("CONTROL_AF_AVAILABLE_MODES", autoFocusModesArray);
                            }

                            int[] autoWhiteBalanceModes = physicalCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
                            if(autoWhiteBalanceModes != null && autoWhiteBalanceModes.length > 0) {
                                JSONArray autoWhiteBalanceModesArray = new JSONArray();
                                for (int mode : autoWhiteBalanceModes) {
                                    autoWhiteBalanceModesArray.put(mode);
                                }
                                physicalCamera.put("CONTROL_AWB_AVAILABLE_MODES", autoWhiteBalanceModesArray);
                            }

                            int[] colorCorrectionAberrationModes = physicalCharacteristics.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES);
                            if(colorCorrectionAberrationModes != null && colorCorrectionAberrationModes.length > 0) {
                                JSONArray colorCorrectionAberrationModesArray = new JSONArray();
                                for (int mode : colorCorrectionAberrationModes) {
                                    colorCorrectionAberrationModesArray.put(mode);
                                }
                                physicalCamera.put("COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES", colorCorrectionAberrationModesArray);
                            }

                            int[] edgeModes = physicalCharacteristics.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES);
                            if(edgeModes != null && edgeModes.length > 0) {
                                JSONArray edgeModesArray = new JSONArray();
                                for (int mode : edgeModes) {
                                    edgeModesArray.put(mode);
                                }
                                physicalCamera.put("EDGE_AVAILABLE_EDGE_MODES", edgeModesArray);
                            }

                            int[] hotPixelModes = physicalCharacteristics.get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES);
                            if(hotPixelModes != null && hotPixelModes.length > 0) {
                                JSONArray hotPixelModesArray = new JSONArray();
                                for (int mode : hotPixelModes) {
                                    hotPixelModesArray.put(mode);
                                }
                                physicalCamera.put("HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES", hotPixelModesArray);
                            }

                            int[] lensShadingModes = physicalCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
                            if(lensShadingModes != null && lensShadingModes.length > 0) {
                                JSONArray lensShadingModesArray = new JSONArray();
                                for (int mode : lensShadingModes) {
                                    lensShadingModesArray.put(mode);
                                }
                                physicalCamera.put("LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION", lensShadingModesArray);
                            }

                            int[] noiseReductionModes = physicalCharacteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);
                            if(noiseReductionModes != null && noiseReductionModes.length > 0) {
                                JSONArray noiseReductionModesArray = new JSONArray();
                                for (int mode : noiseReductionModes) {
                                    noiseReductionModesArray.put(mode);
                                }
                                physicalCamera.put("NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES", noiseReductionModesArray);
                            }

                            int[] tonemapModes = physicalCharacteristics.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES);
                            if(tonemapModes != null && tonemapModes.length > 0) {
                                JSONArray tonemapModesArray = new JSONArray();
                                for (int mode : tonemapModes) {
                                    tonemapModesArray.put(mode);
                                }
                                physicalCamera.put("TONEMAP_AVAILABLE_TONE_MAP_MODES", tonemapModesArray);
                            }
                            physicalCameras.put(physicalCamera);
                        }
                        if(physicalCameras.length() > 0){
                            logicalCamera.put("PHYSICAL_CAMERAS", physicalCameras);
                        }
                        logicalCameras.put(logicalCamera);
                    }
                    JSObject result = new JSObject();
                    result.put("LOGICAL_CAMERAS", logicalCameras);
                    call.resolve(result);
                } catch (Exception e) {
                    call.reject("Exception retrieving camera characteristics: " + e);
                }
            }else{
                call.reject("This feature is only available on Android P or later.");
            }
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Exception retrieving camera characteristics: " + e);
            call.reject("failed to retrieve camera characteristics: " + e.getMessage());
        }
    }

    public void setOpacity(PluginCall call) {
        try{
            if (this.hasCamera(call) == false) {
                call.reject("Camera is not running");
                return;
            }

            plugin.getBridge().saveCall(call);
            Float opacity = call.getFloat("opacity", 1F);
            fragment.setOpacity(opacity);
        }catch (Exception e) {
            Logger.debug(getLogTag(), "Set camera opacity exception: " + e);
            call.reject("failed to set camera opacity: " + e.getMessage());
        }
    }

    public void setZoom(PluginCall call) {
        try {
            if (this.hasCamera(call) == false) {
                call.reject("Camera is not running");
                return;
            }
            
            float zoom = call.getFloat("zoom", 1F);
            if(fragment.isZoomSupported()) {
                fragment.setCurrentZoomLevel(zoom);
                call.resolve();
            } else {
                call.reject("Zoom not supported");
            }
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Set camera zoom exception: " + e);
            call.reject("failed to zoom camera: " + e.getMessage());
        }
    }

    public void getZoom(PluginCall call) {
        try {
            if (this.hasCamera(call) == false) {
                call.reject("Camera is not running");
                return;
            }

            if(fragment.isZoomSupported()) {
                float currentZoom = fragment.getCurrentZoomLevel();
                JSObject jsObject = new JSObject();
                jsObject.put("value", currentZoom);
                call.resolve(jsObject);
            } else {
                call.reject("Zoom not supported");
            }
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Get camera zoom exception: " + e);
            call.reject("failed to get camera zoom: " + e.getMessage());
        }
    }

    public void getMaxZoom(PluginCall call) {
        try {
            if (this.hasCamera(call) == false) {
                call.reject("Camera is not running");
                return;
            }

            if(fragment.isZoomSupported()) {
                float maxZoom = fragment.getMaxZoomLevel();
                JSObject jsObject = new JSObject();
                jsObject.put("value", maxZoom);
                call.resolve(jsObject);
            } else {
                call.reject("Zoom not supported");
            }
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Get max camera zoom exception: " + e);
            call.reject("failed to get max camera zoom: " + e.getMessage());
        }
    }

    public void getMaxZoomLimit(PluginCall call) {
        try {
            float maxZoomLimit = fragment.maxZoomLimit;
            String value = maxZoomLimit == fragment.NO_MAX_ZOOM_LIMIT ? null : String.valueOf(maxZoomLimit);
            JSObject jsObject = new JSObject();
            jsObject.put("value", value);
            call.resolve(jsObject);
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Get max  zoom limit exception: " + e);
            call.reject("failed to get max zoom limit: " + e.getMessage());
        }
    }

    public void setMaxZoomLimit(PluginCall call) {
        try {
            float maxZoomLimit = call.getFloat("maxZoomLimit", fragment.NO_MAX_ZOOM_LIMIT);
            fragment.maxZoomLimit = maxZoomLimit;
            call.resolve();
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Set max zoom limit exception: " + e);
            call.reject("failed to set max zoom limit: " + e.getMessage());
        }
    }

    public void capture(PluginCall call) {
        try{
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
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Capture exception: " + e);
            call.reject("failed to capture image: " + e.getMessage());
        }
    }

    public void captureSample(PluginCall call) {
        try{
            if (this.hasCamera(call) == false) {
                call.reject("Camera is not running");
                return;
            }
            plugin.getBridge().saveCall(call);
            snapshotCallbackId = call.getCallbackId();

            Integer quality = call.getInt("quality", 85);
            fragment.takeSnapshot(quality);
        } catch (Exception e) {
            Logger.debug(getLogTag(), "Capture sample exception: " + e);
            call.reject("failed to capture sample: " + e.getMessage());
        }
    }

    public void stop(final PluginCall call) {
        try{
            plugin.getBridge()
                    .getActivity()
                    .runOnUiThread(
                            new Runnable() {
                                @SuppressLint("WrongConstant")
                                @Override
                                public void run() {
                                    try{
                                        FrameLayout containerView = plugin.getBridge().getActivity().findViewById(containerViewId);

                                        // allow orientation changes after closing camera:
                                        if(previousOrientationRequest != -1 && !fragment.lockOrientation){
                                            plugin.getBridge().getActivity().setRequestedOrientation(previousOrientationRequest);
                                        }


                                        if (containerView != null) {
                                            ((ViewGroup) plugin.getBridge().getWebView().getParent()).removeView(containerView);
                                            plugin.getBridge().getWebView().setBackgroundColor(Color.WHITE);
                                            FragmentManager fragmentManager = plugin.getActivity().getFragmentManager();
                                            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                                            fragmentTransaction.remove(fragment);
                                            fragmentTransaction.commit();
                                            fragment = null;

                                            call.resolve();
                                        } else {
                                            call.reject("camera already stopped");
                                        }
                                    }catch (Exception e) {
                                        Logger.debug(getLogTag(), "Stop camera exception: " + e);
                                        call.reject("failed to stop camera");
                                    }
                                }
                            }
                    );
        }catch (Exception e) {
            Logger.debug(getLogTag(), "Stop camera exception: " + e);
            call.reject("failed to stop camera: " + e.getMessage());
        }
    }

    public void getSupportedFlashModes(PluginCall call) {
        try{
            if (this.hasCamera(call) == false) {
                call.reject("Camera is not running");
                return;
            }

            String[] supportedFlashModes = fragment.getSupportedFlashModes();
            JSONArray jsonFlashModes = new JSONArray();

            if (supportedFlashModes != null) {
                for (String supportedFlashMode : supportedFlashModes) {
                    jsonFlashModes.put(supportedFlashMode);
                }
            }

            JSObject jsObject = new JSObject();
            jsObject.put("result", jsonFlashModes);
            call.resolve(jsObject);
        }catch (Exception e) {
            Logger.debug(getLogTag(), "Get supported flash modes exception: " + e);
            call.reject("failed to get supported flash modes: " + e.getMessage());
        }
    }

    public void setFlashMode(PluginCall call) {
        try{
            if (this.hasCamera(call) == false) {
                call.reject("Camera is not running");
                return;
            }

            String flashMode = call.getString("flashMode");
            if (flashMode == null || flashMode.isEmpty() == true) {
                call.reject("flashMode required parameter is missing");
                return;
            }

            String[] supportedFlashModes = fragment.getSupportedFlashModes();

            if (supportedFlashModes != null && supportedFlashModes.length > 0) {
                boolean isSupported = false;
                for (String supportedFlashMode : supportedFlashModes) {
                    if (supportedFlashMode.equals(flashMode)) {
                        isSupported = true;
                        break;
                    }
                }
                if (!isSupported) {
                    call.reject("Flash mode not supported: " + flashMode);
                    return;
                }
            }
            fragment.setFlashMode(flashMode);
            call.resolve();
        }catch (Exception e) {
            Logger.debug(getLogTag(), "Set flash mode exception: " + e);
            call.reject("failed to set flash mode: " + e.getMessage());
        }
    }

    public void startRecordVideo(final PluginCall call) {
        try{
            if (this.hasCamera(call) == false) {
                call.reject("Camera is not running");
                return;
            }
            final String filename = "videoTmp";
            VIDEO_FILE_PATH = plugin.getActivity().getCacheDir().toString() + "/";

            final String position = call.getString("position", "front");
            final Integer width = call.getInt("width", 0);
            final Integer height = call.getInt("height", 0);
            final Boolean withFlash = call.getBoolean("withFlash", false);
            final Integer maxDuration = call.getInt("maxDuration", 0);
            // final Integer quality = call.getInt("quality", 0);
            plugin.getBridge().saveCall(call);
            call.setKeepAlive(true);
            recordCallbackId = call.getCallbackId();

            plugin.getBridge()
                    .getActivity()
                    .runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    // fragment.startRecord(getFilePath(filename), position, width, height, quality, withFlash);
                                    try{
                                        fragment.startRecord(getFilePath(filename), position, width, height, 70, withFlash, maxDuration);
                                        call.resolve();
                                    } catch (Exception e) {
                                        Logger.debug(getLogTag(), "Start record video exception: " + e);
                                        call.reject("failed to start record video");
                                    }
                                }
                            }
                    );
        }catch (Exception e) {
            Logger.debug(getLogTag(), "Start record video exception: " + e);
            call.reject("failed to start record video: " + e.getMessage());
            }
    }

    public void stopRecordVideo(PluginCall call) {
        try{
            if (this.hasCamera(call) == false) {
                call.reject("Camera is not running");
                return;
            }

            System.out.println("stopRecordVideo - Callbackid=" + call.getCallbackId());

            plugin.getBridge().saveCall(call);
            recordCallbackId = call.getCallbackId();
            fragment.stopRecord();
            call.resolve();
        }catch (Exception e) {
            Logger.debug(getLogTag(), "Stop record video exception: " + e);
            call.reject("failed to stop record video: " + e.getMessage());
        }
    }

    public void handleCameraPermissionResult(PluginCall call) {
        startCamera(call);
    }


    public void startCamera(final PluginCall call) {
        try{
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
            final Boolean cropToPreview = call.getBoolean("cropToPreview", true);
            final Boolean disableExifHeaderStripping = call.getBoolean("disableExifHeaderStripping", true);
            final Boolean lockOrientation = call.getBoolean("lockAndroidOrientation", false);
            previousOrientationRequest = plugin.getBridge().getActivity().getRequestedOrientation();

            fragment = new Camera2Activity();
            fragment.setEventListener(this);
            fragment.camera2Preview = this;
            fragment.bridge = plugin.getBridge();
            fragment.activity = plugin.getActivity();
            fragment.context = plugin.getContext();
            fragment.position = position;
            fragment.tapToTakePicture = false;
            fragment.dragEnabled = false;
            fragment.tapToFocus = true;
            fragment.disableExifHeaderStripping = disableExifHeaderStripping;
            fragment.storeToFile = storeToFile;
            fragment.toBack = toBack;
            fragment.enableOpacity = enableOpacity;
            fragment.enableZoom = enableZoom;
            fragment.cropToPreview = cropToPreview;
            final float maxZoomLimit = call.getFloat("maxZoomLimit", fragment.NO_MAX_ZOOM_LIMIT);
            fragment.maxZoomLimit = maxZoomLimit;
            fragment.lockOrientation = lockOrientation;

            // lock orientation if specified in options:
            if (lockOrientation) {
                plugin.getBridge().getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            }


            plugin.getBridge()
                    .getActivity()
                    .runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try{
                                        DisplayMetrics metrics = plugin.getBridge().getActivity().getResources().getDisplayMetrics();


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
                                            // otherwise the plugin start method might resolve/return before the camera is actually set in CameraActivity
                                            // onResume method and the next subsequent plugin
                                            // method invocations (for example, getSupportedFlashModes) might fails with "Camera is not running" error
                                            // because camera is not available yet and hasCamera method will return false
                                            // Please also see https://developer.android.com/reference/android/hardware/Camera.html#open%28int%29
                                            plugin.getBridge().saveCall(call);
                                            cameraStartCallbackId = call.getCallbackId();
                                        } else {
                                            call.reject("camera already started");
                                        }
                                    }catch (Exception e) {
                                        Logger.debug(getLogTag(), "Start camera exception: " + e);
                                        call.reject("failed to start camera");
                                    }
                                }
                            }
                    );
        }catch (Exception e) {
            Logger.debug(getLogTag(), "Start camera exception: " + e);
            call.reject("failed to start camera");
        }
    }

    @Override
    public void onCameraStartedError(String message) {
        // Handle the error, e.g., reject a plugin call or log the error
        Logger.error(getLogTag(), "Camera start error: " + message, null);
        if (cameraStartCallbackId != null && !cameraStartCallbackId.isEmpty()) {
            PluginCall call = plugin.getBridge().getSavedCall(cameraStartCallbackId);
            if (call != null) {
                call.reject("Camera start error: " + message);
            }
            cameraStartCallbackId = "";
        }
    }

    public void handleOnConfigurationChanged(Configuration newConfig) {
        try{
            if(fragment == null || fragment.lockOrientation) {
                return;
            }
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                fragment.onOrientationChange("landscape");
            } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                fragment.onOrientationChange("portrait");
            }else{
                fragment.onOrientationChange("unknown");
            }
        }catch (Exception e) {
            Logger.debug(getLogTag(), "Handle on configuration changed exception: " + e);
        }
    }


    @Override
    public void onPictureTaken(String originalPicture) {
        try{
            JSObject jsObject = new JSObject();
            jsObject.put("value", originalPicture);
            plugin.getBridge().getSavedCall(captureCallbackId).resolve(jsObject);
        }catch (Exception e) {
            Logger.debug(getLogTag(), "On picture taken exception: " + e);
            if(!captureCallbackId.isEmpty() && plugin.getBridge().getSavedCall(captureCallbackId) != null){
                plugin.getBridge().getSavedCall(captureCallbackId).reject("failed to capture image");
            }else{
                Logger.debug(getLogTag(), "Capture callback id is empty");
            }
        }

    }

    @Override
    public void onPictureTakenError(String message) {
        try{
            plugin.getBridge().getSavedCall(captureCallbackId).reject(message);
        }catch (Exception e) {
            Logger.debug(getLogTag(), "On picture taken error exception: " + e);
            if (!captureCallbackId.isEmpty() && plugin.getBridge().getSavedCall(captureCallbackId) != null) {
                plugin.getBridge().getSavedCall(captureCallbackId).reject("failed to capture image");
            } else {
                Logger.debug(getLogTag(), "Capture callback id is empty");
            }
        }
    }

    @Override
    public void onSnapshotTaken(String originalPicture) {
        try{
            JSObject jsObject = new JSObject();
            jsObject.put("value", originalPicture);
            plugin.getBridge().getSavedCall(snapshotCallbackId).resolve(jsObject);
        }catch (Exception e) {
            Logger.debug(getLogTag(), "On snapshot taken exception: " + e);
            if (!snapshotCallbackId.isEmpty() && plugin.getBridge().getSavedCall(snapshotCallbackId) != null) {
                plugin.getBridge().getSavedCall(snapshotCallbackId).reject("failed to capture sample");
            } else {
                Logger.debug(getLogTag(), "Snapshot callback id is empty");
            }
        }
    }

    @Override
    public void onSnapshotTakenError(String message) {
        try{
            plugin.getBridge().getSavedCall(snapshotCallbackId).reject(message);
        }catch (Exception e) {
            Logger.debug(getLogTag(), "On snapshot taken error exception: " + e);
            if (!snapshotCallbackId.isEmpty() && plugin.getBridge().getSavedCall(snapshotCallbackId) != null) {
                plugin.getBridge().getSavedCall(snapshotCallbackId).reject("failed to capture sample");
            } else {
                Logger.debug(getLogTag(), "Snapshot callback id is empty");
            }
        }
    }

    @Override
    public void onFocusSet(int pointX, int pointY) {}

    @Override
    public void onFocusSetError(String message) {}

    @Override
    public void onBackButton() {}

    @Override
    public void onCameraStarted() {
        try{
            if(cameraStartCallbackId.isEmpty()) {
                return;
            }
            PluginCall pluginCall = plugin.getBridge().getSavedCall(cameraStartCallbackId);
            pluginCall.resolve();
            plugin.getBridge().releaseCall(pluginCall);
            cameraStartCallbackId = "";
        }catch (Exception e) {
            Logger.debug(getLogTag(), "On camera started exception: " + e);
            if(!cameraStartCallbackId.isEmpty() && plugin.getBridge().getSavedCall(cameraStartCallbackId) != null){
                plugin.getBridge().getSavedCall(cameraStartCallbackId).reject("failed to start camera");
            }else{
                Logger.debug(getLogTag(), "Camera start callback id is empty");
            }
        }
    }

    @Override
    public void onStartRecordVideo() {}

    @Override
    public void onStartRecordVideoError(String message) {
        try{
            plugin.getBridge().getSavedCall(recordCallbackId).reject(message);
        }catch (Exception e) {
            Logger.debug(getLogTag(), "On start record video error exception: " + e);
            if (!recordCallbackId.isEmpty() && plugin.getBridge().getSavedCall(recordCallbackId) != null) {
                plugin.getBridge().getSavedCall(recordCallbackId).reject("failed to start record video");
            } else {
                Logger.debug(getLogTag(), "Record callback id is empty");
            }
        }
    }

    @Override
    public void onStopRecordVideo(String file) {
        try{
            PluginCall pluginCall = plugin.getBridge().getSavedCall(recordCallbackId);
            JSObject jsObject = new JSObject();
            jsObject.put("videoFilePath", file);
            pluginCall.resolve(jsObject);
        }catch (Exception e) {
            Logger.debug(getLogTag(), "On stop record video exception: " + e);
            if (!recordCallbackId.isEmpty() && plugin.getBridge().getSavedCall(recordCallbackId) != null) {
                plugin.getBridge().getSavedCall(recordCallbackId).reject("failed to stop record video");
            } else {
                Logger.debug(getLogTag(), "Record callback id is empty");
            }
        }
    }

    @Override
    public void onStopRecordVideoError(String error) {
        try{
            plugin.getBridge().getSavedCall(recordCallbackId).reject(error);
        }catch (Exception e) {
            Logger.debug(getLogTag(), "On stop record video error exception: " + e);
            if (!recordCallbackId.isEmpty() && plugin.getBridge().getSavedCall(recordCallbackId) != null) {
                plugin.getBridge().getSavedCall(recordCallbackId).reject("failed to stop record video");
            } else {
                Logger.debug(getLogTag(), "Record callback id is empty");
            }
        }
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
                        try{
                            if ((null != fragment) && (fragment.toBack == true)) {
                                fragment.frameContainerLayout.dispatchTouchEvent(event);
                            }
                        }catch (Exception e) {
                            Logger.debug(getLogTag(), "Broadcast touch event exception: " + e);
                        }
                        return false;
                    }
                }
            );
    }
}
