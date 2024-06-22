package com.ahm.capacitor.camera.preview;

import static androidx.core.math.MathUtils.clamp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import android.app.Fragment;

import com.getcapacitor.Bridge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CameraActivity extends Fragment {

    public interface CameraPreviewListener {
        void onPictureTaken(String originalPicture);

        void onPictureTakenError(String message);

        void onSnapshotTaken(String originalPicture);

        void onSnapshotTakenError(String message);

        void onFocusSet(int pointX, int pointY);

        void onFocusSetError(String message);

        void onBackButton();

        void onCameraStarted();

        void onStartRecordVideo();

        void onStartRecordVideoError(String message);

        void onStopRecordVideo(String file);

        void onStopRecordVideoError(String error);
    }

    /**
     * Private properties
     */
    private static final String TAG = "CameraPreviewActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private static final float PINCH_ZOOM_SENSITIVITY = 0.01f;
    private static final long PINCH_ZOOM_DEBOUNCE_DELAY_MS = 10; // milliseconds


    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;


    private CameraActivity.CameraPreviewListener eventListener;

    private Size[] mSupportedPreviewSizes;
    private CameraCharacteristics mCameraCharacteristics;
    Size mPreviewSize;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private enum RecordingState {
        INITIALIZING,
        STARTED,
        STOPPED
    }

    private final CameraActivity.RecordingState mRecordingState = CameraActivity.RecordingState.INITIALIZING;
    private MediaRecorder mRecorder = null;
    private String recordFilePath;

    private String cameraId;

    /**
     * Public properties
     */
    public Bridge bridge;
    public Activity activity;
    public Context context;
    public FrameLayout mainLayout;
    public FrameLayout frameContainerLayout;

    public String position = "back";
    public boolean tapToTakePicture;
    public boolean dragEnabled;
    public boolean tapToFocus;
    public boolean disableExifHeaderStripping;
    public boolean storeToFile;
    public boolean toBack;
    public boolean enableOpacity = false;
    public boolean enableZoom = false;
    public boolean cropToPreview = true; // whether to crop captured image to preview size

    public int width;
    public int height;
    public int x;
    public int y;


    /**
     * Public methods
     */
    public void setEventListener(CameraActivity.CameraPreviewListener listener) {
        eventListener = listener;
    }

    public void setRect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public CameraDevice getCamera() {
        return cameraDevice;
    }

    public CameraCharacteristics getCameraCharacteristics() {
        return mCameraCharacteristics;
    }

    public void switchCamera() {
        closeCamera();
        position = position.equals("front") ? "back" : "front";
        logMessage("switchCamera to: " + position);
        openCamera();
    }

    public void setOpacity(final float opacity) {
        logMessage("setOpacity: " + opacity);
        if (enableOpacity && textureView != null) {
            textureView.setAlpha(opacity);
        }
    }

    public void takePicture(final int width, final int height, final int quality) throws Exception {
        if (cameraDevice == null) {
            return;
        }

        int imageWidth = width;
        int imageHeight = height;

        if(cropToPreview){
            imageWidth = mPreviewSize.getWidth();
            imageHeight = mPreviewSize.getHeight();
        }else{
            Size[] jpegSizes = null;
            if (mCameraCharacteristics != null) {
                jpegSizes = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(android.graphics.ImageFormat.JPEG);
            }
            if (jpegSizes != null && jpegSizes.length > 0) {
                imageWidth = jpegSizes[0].getWidth();
                imageHeight = jpegSizes[0].getHeight();
            }
        }
        logMessage("takePicture: " + imageWidth + ", " + imageHeight + ", " + quality);

        final ImageReader reader = ImageReader.newInstance(imageWidth, imageHeight, android.graphics.ImageFormat.JPEG, 1);
        List<Surface> outputSurfaces = new ArrayList<>(2);
        outputSurfaces.add(reader.getSurface());
        outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

        final CaptureRequest.Builder stillCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        stillCaptureRequestBuilder.addTarget(reader.getSurface());
        stillCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        if (!disableExifHeaderStripping) {
            int deviceOrientation = activity.getWindowManager().getDefaultDisplay().getRotation();
            stillCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(deviceOrientation));
        }else{
            stillCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);
        }

        Rect zoomRect = getZoomRect(getCurrentZoomLevel());
        if (zoomRect != null) {
            stillCaptureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
        }
        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                try (Image image = reader.acquireLatestImage()) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    if (!storeToFile) {
                        String encodedImage = Base64.encodeToString(bytes, Base64.NO_WRAP);
                        eventListener.onPictureTaken(encodedImage);
                    } else {
                        String path = getTempFilePath();
                        save(bytes, path, quality);
                        eventListener.onPictureTaken(path);
                    }
                } catch (Exception e) {
                    eventListener.onPictureTakenError(e.getMessage());
                    logException(e);
                }
            }

            private void save(byte[] bytes, String filePath, int quality) throws IOException {
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                try (OutputStream output = new FileOutputStream(filePath)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
                }
            }
        };
        reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
        CameraCaptureSession.CaptureCallback stillCaptureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                try {
                    startPreview();
                } catch (Exception e) {
                    eventListener.onPictureTakenError(e.getMessage());
                    logException(e);
                }
            }
        };
        cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession stillCaptureSession) {
                try {
                    stillCaptureSession.capture(stillCaptureRequestBuilder.build(), stillCaptureListener, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    eventListener.onPictureTakenError(e.getMessage());
                    logException(e);
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                eventListener.onPictureTakenError("Configuration failed");
            }
        }, mBackgroundHandler);
    }

    public void takeSnapshot(final int quality) throws Exception {
        if (cameraDevice == null) {
            return;
        }
        logMessage("takeSnapshot");
        final ImageReader reader = ImageReader.newInstance(width, height, android.graphics.ImageFormat.JPEG, 1);
        List<Surface> outputSurfaces = new ArrayList<>(2);
        outputSurfaces.add(reader.getSurface());
        outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
        final CaptureRequest.Builder stillCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        stillCaptureRequestBuilder.addTarget(reader.getSurface());
        stillCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        // Orientation
        if (!disableExifHeaderStripping) {
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            stillCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
        }else{
            stillCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);
        }
        Rect zoomRect = getZoomRect(getCurrentZoomLevel());
        if (zoomRect != null) {
            stillCaptureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
        }
        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                try (Image image = reader.acquireLatestImage()) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    // Compress the image using the quality parameter
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                    byte[] compressedBytes = baos.toByteArray();

                    String encodedImage = Base64.encodeToString(compressedBytes, Base64.NO_WRAP);
                    eventListener.onSnapshotTaken(encodedImage);
                } catch (Exception e) {
                    eventListener.onSnapshotTakenError(e.getMessage());
                    logException(e);
                }
            }
        };
        reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
        CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                try {
                    startPreview();
                } catch (Exception e) {
                    eventListener.onSnapshotTakenError(e.getMessage());
                    logException(e);
                }
            }
        };
        cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                try {
                    session.capture(stillCaptureRequestBuilder.build(), captureListener, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    eventListener.onSnapshotTakenError(e.getMessage());
                    logException(e);
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            }
        }, mBackgroundHandler);

    }

    public void startRecord(
            final String filePath,
            final String camera,
            final int width,
            final int height,
            final int quality,
            final boolean withFlash,
            final int maxDuration
    ) throws Exception {

        logMessage("CameraPreview startRecord camera: " + camera + " width: " + width + ", height: " + height + ", quality: " + quality);
        muteStream(true, activity);
        if (this.mRecordingState == RecordingState.STARTED) {
            logError("Recording already started");
            return;
        }
        this.recordFilePath = filePath;

        if (withFlash) {
            // Turn on the flash
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setTorchMode(cameraId, true);
            } else { // for old devices
                @SuppressWarnings("deprecation")
                Camera oldCamera = Camera.open();
                Camera.Parameters parameters = oldCamera.getParameters();
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                oldCamera.setParameters(parameters);
                oldCamera.startPreview();
            }
        }

        mRecorder = new MediaRecorder();

        try {
            CamcorderProfile profile;
            if (CamcorderProfile.hasProfile(getCameraToUse(), CamcorderProfile.QUALITY_HIGH)) {
                profile = CamcorderProfile.get(getCameraToUse(), CamcorderProfile.QUALITY_HIGH);
            } else {
                if (CamcorderProfile.hasProfile(getCameraToUse(), CamcorderProfile.QUALITY_480P)) {
                    profile = CamcorderProfile.get(getCameraToUse(), CamcorderProfile.QUALITY_480P);
                } else {
                    if (CamcorderProfile.hasProfile(getCameraToUse(), CamcorderProfile.QUALITY_720P)) {
                        profile = CamcorderProfile.get(getCameraToUse(), CamcorderProfile.QUALITY_720P);
                    } else {
                        if (CamcorderProfile.hasProfile(getCameraToUse(), CamcorderProfile.QUALITY_1080P)) {
                            profile = CamcorderProfile.get(getCameraToUse(), CamcorderProfile.QUALITY_1080P);
                        } else {
                            profile = CamcorderProfile.get(getCameraToUse(), CamcorderProfile.QUALITY_LOW);
                        }
                    }
                }
            }

            mRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mRecorder.setProfile(profile);
            mRecorder.setOutputFile(filePath);
            mRecorder.setOrientationHint(getOrientationHint());
            mRecorder.setMaxDuration(maxDuration);

            List<Surface> surfaces = new ArrayList<>();

            Surface recorderSurface = mRecorder.getSurface();
            surfaces.add(recorderSurface);

            surfaces.add(new Surface(textureView.getSurfaceTexture()));

            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        // Build the capture request, and start the session
                        CaptureRequest.Builder recordCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        recordCaptureRequestBuilder.addTarget(recorderSurface);
                        Rect zoomRect = getZoomRect(getCurrentZoomLevel());
                        if (zoomRect != null) {
                            recordCaptureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
                        }

                        session.setRepeatingRequest(recordCaptureRequestBuilder.build(), null, null);
                        mRecorder.prepare();
                        logMessage("Starting recording");
                        mRecorder.start();
                    } catch (Exception e) {
                        logException(e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    // Handle configuration failure
                }
            }, null);

            eventListener.onStartRecordVideo();
        } catch (Exception e) {
            eventListener.onStartRecordVideoError(e.getMessage());
        }

    }

    public void muteStream(boolean mute, Activity activity) {
        AudioManager audioManager = ((AudioManager) activity.getApplicationContext().getSystemService(Context.AUDIO_SERVICE));
        int direction;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            direction = mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE;
        } else {
            direction = mute ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE;
        }
        audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, direction, 0);
    }

    public void stopRecord() {
        logMessage("stopRecord");

        try {
            mRecorder.stop();
            mRecorder.reset(); // clear recorder configuration
            mRecorder.release(); // release the recorder object
            mRecorder = null;
            startPreview();
            eventListener.onStopRecordVideo(this.recordFilePath);
        } catch (Exception e) {
            eventListener.onStopRecordVideoError(e.getMessage());
        }
    }

    public boolean isZoomSupported() {
        if (mCameraCharacteristics == null) return false;
        return mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) > 1;
    }


    public float getMaxZoomLevel() {
        Float maxZoom = null;
        if(mCameraCharacteristics != null){
            maxZoom = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        }
        return (maxZoom != null) ? maxZoom : 1.0f;
    }

    public float getMinZoomLevel() {
        return 1.0f; // The minimum zoom level is always 1.0 (no zoom)
    }

    public void setCurrentZoomLevel(float zoomLevel) throws Exception {
        if (mCameraCharacteristics == null) return;
        logMessage("setCurrentZoomLevel to: " + zoomLevel);
        logMessage("currentZoomLevel (before setCurrentZoomLevel): " + getCurrentZoomLevel());

        float maxZoom = getMaxZoomLevel();
        logMessage("maxZoom: " + maxZoom);
        float minZoom = getMinZoomLevel();
        logMessage("minZoom: " + minZoom);

        float newLevel = Math.max(minZoom, Math.min(zoomLevel, maxZoom));
        logMessage("newLevel: " + newLevel);

        Rect zoomRect = getZoomRect(newLevel);
        if (zoomRect != null) {
            previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, null);
        }
    }


    public float getCurrentZoomLevel() {
        if(mCameraCharacteristics != null){
            Rect activeArraySize = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if (activeArraySize != null) {
                Rect currentCropRegion = previewRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION);
                if (currentCropRegion != null) {
                    return (float) activeArraySize.width() / currentCropRegion.width();
                }
            }
        }
        return getMinZoomLevel(); // Default to minimum zoom if current crop region is unavailable
    }

    // get supported flash modes
    public String[] getSupportedFlashModes() {
        if (mCameraCharacteristics != null) {
            int[] flashModes = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
            String[] flashModesStr = new String[flashModes.length];
            for (int i = 0; i < flashModes.length; i++) {
                flashModesStr[i] = flashModes[i] + "";
            }
            return flashModesStr;
        }
        return new String[0];
    }

    public void setFlashMode(String flashMode) {
        if (mCameraCharacteristics != null) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, Integer.parseInt(flashMode));
            logMessage("setFlashMode: " + flashMode);
        }
    }

    public void onOrientationChange(String orientation) {
        try {
            logMessage("onOrientationChanged: " + orientation);
//            Log.d(TAG, "device orientation: " + getDeviceOrientation());
//            Log.d(TAG, "sensor orientation: " + getSensorOrientation());
            configureTransform(textureView.getWidth(), textureView.getHeight());
        } catch (Exception e) {
            logException("onOrientationChanged error", e);
        }
    }

    /**
     * Internal methods and listeners
     */
    private Rect getZoomRect(float zoomLevel){
        if(mCameraCharacteristics == null) return null;

        Rect activeArraySize = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (activeArraySize == null) return null;


        int cropWidth = (int) (activeArraySize.width() / zoomLevel);
        int cropHeight = (int) (activeArraySize.height() / zoomLevel);
        int cropLeft = (activeArraySize.width() - cropWidth) / 2;
        int cropTop = (activeArraySize.height() - cropHeight) / 2;
        return new Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight);
    }

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            closeCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            closeCamera();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        String appResourcesPackage = activity.getPackageName();

        // Inflate the layout for this fragment
        View view = inflater.inflate(getResources().getIdentifier("camera_activity", "layout", appResourcesPackage), container, false);

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);
        frameContainerLayout =
                view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));
        frameContainerLayout.setLayoutParams(layoutParams);


        mainLayout = view.findViewById(getResources().getIdentifier("video_view", "id", appResourcesPackage));
        mainLayout.setLayoutParams(
                new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        );
        mainLayout.setEnabled(false);

        // create texture view and add it to mainLayout
        textureView = new TextureView(getActivity());
        mainLayout.addView(textureView);
        textureView.setSurfaceTextureListener(textureListener);
        textureView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (enableZoom) {
            setupTouchAndBackButton();
        }

        return view;
    }

    private void setupTouchAndBackButton() {
        final GestureDetector gestureDetector = new GestureDetector(activity.getApplicationContext(), new TapGestureDetector());

        activity
                .runOnUiThread(
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
                                                try {
                                                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) frameContainerLayout.getLayoutParams();

                                                    boolean isSingleTapTouch = gestureDetector.onTouchEvent(event);
                                                    int action = event.getAction();
                                                    int eventCount = event.getPointerCount();
//                                                    Log.d(TAG, "onTouch event, action, count: " + event + ", " + action + ", " + eventCount);
                                                    if (eventCount > 1) {
                                                        // handle multi-touch events
                                                        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_2_DOWN) {
                                                            mDist = getFingerSpacing(event);
//                                                            Log.d(TAG, "onTouch start: mDist=" + mDist);
                                                        } else if (action == MotionEvent.ACTION_MOVE && isZoomSupported()) {
                                                            handlePinchZoom(event);
                                                        }
                                                    } else {
                                                        if (action != MotionEvent.ACTION_MOVE && isSingleTapTouch) {
                                                            if (tapToTakePicture && tapToFocus) {
                                                                int tapX = (int) event.getX(0);
                                                                int tapY = (int) event.getY(0);
                                                                setFocusArea(
                                                                        tapX,
                                                                        tapY,
                                                                        new CameraCaptureSession.CaptureCallback() {
                                                                            @Override
                                                                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                                                                super.onCaptureCompleted(session, request, result);
                                                                                try {
                                                                                    eventListener.onFocusSet(tapX, tapY);
                                                                                    takePicture(0, 0, 85);
                                                                                } catch (Exception e) {
                                                                                    eventListener.onFocusSetError(e.getMessage());
                                                                                    logException(e);
                                                                                }
                                                                            }
                                                                        }
                                                                );
                                                            } else if (tapToTakePicture) {
                                                                takePicture(0, 0, 85);
                                                            } else if (tapToFocus) {
                                                                int tapX = (int) event.getX(0);
                                                                int tapY = (int) event.getY(0);

                                                                CameraCaptureSession.CaptureCallback captureCallback =  new CameraCaptureSession.CaptureCallback() {
                                                                    @Override
                                                                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                                                        super.onCaptureCompleted(session, request, result);
                                                                        try {
                                                                            logMessage("onTouch:" + " setFocusArea() succeeded");
                                                                            eventListener.onFocusSet(tapX, tapY);
                                                                        } catch (Exception e) {
                                                                            eventListener.onFocusSetError(e.getMessage());
                                                                            logException(e);
                                                                        }
                                                                    }
                                                                };

                                                                setFocusArea(
                                                                        tapX,
                                                                        tapY,
                                                                        captureCallback
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

                                                                        mPosX += (int) dx;
                                                                        mPosY += (int) dy;

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
                                                } catch (Exception e) {
                                                    logException("onTouch error: ", e);
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
                            private long lastZoomTime = 0;

                            private void handlePinchZoom(MotionEvent event){
                                if (cameraDevice == null) return;
                                try {
                                    float newDist = getFingerSpacing(event);

                                    float maxZoom = getMaxZoomLevel();
                                    float minZoom = getMinZoomLevel();

                                    float currentZoomLevel = getCurrentZoomLevel();

                                    float newZoomLevel = currentZoomLevel;

                                    if (newDist > mDist) {
                                        // Zoom in
                                        newZoomLevel = Math.min(currentZoomLevel + ((newDist - mDist) * PINCH_ZOOM_SENSITIVITY), maxZoom);
                                    } else if (newDist < mDist) {
                                        // Zoom out
                                        newZoomLevel = Math.max(currentZoomLevel - ((mDist - newDist) * PINCH_ZOOM_SENSITIVITY), minZoom);
                                    }
                                    mDist = newDist;

                                    long currentTime = System.currentTimeMillis();
                                    if (newZoomLevel != currentZoomLevel && (currentTime - lastZoomTime) > PINCH_ZOOM_DEBOUNCE_DELAY_MS) {
                                        setCurrentZoomLevel(newZoomLevel);
                                        lastZoomTime = currentTime;
                                    }
                                } catch (Exception e) {
                                    logException(e);
                                }
                            }
                        }
                );
    }

    private Handler focusHandler = null;
    private final int MAX_FOCUS_RETRY_COUNT = 3;
    private final int FOCUS_RETRY_INTERVAL_MS = 100;
    private int focusRetryCount = 0;

    public void setFocusArea(final int pointX, final int pointY, final CameraCaptureSession.CaptureCallback callback) throws Exception {
        if (cameraDevice == null) return;

        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        captureSession.capture(previewRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                triggerAutofocus(pointX, pointY, callback);
            }
        }, mBackgroundHandler);
    }

    private void triggerAutofocus(final int pointX, final int pointY, final CameraCaptureSession.CaptureCallback callback) {
        try {
            // Calculate focus and metering areas
            Rect focusRect = calculateTapArea(pointX, pointY, 1f);
            Rect meteringRect = calculateTapArea(pointX, pointY, 1.5f);

            if (focusRect == null || meteringRect == null) {
                logError("Invalid focus or metering area dimensions");
                return;
            }

            // Set AF, AE, and AWB regions
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{new MeteringRectangle(focusRect, 1000)});
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{new MeteringRectangle(meteringRect, 1000)});
            previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_REGIONS, new MeteringRectangle[]{new MeteringRectangle(meteringRect, 1000)});

            // Set AF mode to auto
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);

            // Start autofocus
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            captureSession.capture(previewRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    clearFocusRetry();
                    focusRetryCount = 0;
                    handleFocusResult(result, pointX, pointY, callback);
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            logException(e);
        }
    }


    private void clearFocusRetry() {
        if(focusHandler != null) focusHandler.removeCallbacksAndMessages(null);
    }

    private void handleFocusResult(TotalCaptureResult result, final int pointX, final int pointY, final CameraCaptureSession.CaptureCallback callback) {
        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
        if (afState == null) return;
        switch (afState) {
            case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
            case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                // Focus is complete, reset AF trigger
                clearFocusRetry();
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                try {
                    captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, mBackgroundHandler);
                    callback.onCaptureCompleted(captureSession, previewRequestBuilder.build(), result);
                } catch (CameraAccessException e) {
                    logException(e);
                }
                break;

            case CaptureResult.CONTROL_AF_STATE_INACTIVE:
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                // Retry autofocus if necessary
                clearFocusRetry();
                focusRetryCount++;
                if (focusRetryCount >= MAX_FOCUS_RETRY_COUNT) {
                    Log.d(TAG,"Max focus retry count reached");
                    return;
                }
                focusHandler = new Handler(Looper.getMainLooper());
                focusHandler.postDelayed(() -> {
                    try {
                        triggerAutofocus(pointX, pointY, callback);
                    } catch (Exception e) {
                        logException(e);
                    }
                }, FOCUS_RETRY_INTERVAL_MS);
                break;

            case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
                // Autofocus is still in progress, wait and check again
                clearFocusRetry();
                focusRetryCount++;
                if (focusRetryCount >= MAX_FOCUS_RETRY_COUNT) {
                    Log.d(TAG,"Max focus retry count reached");
                    return;
                }
                focusHandler = new Handler(Looper.getMainLooper());
                focusHandler.postDelayed(() -> {
                    try {
                        handleFocusResult(result, pointX, pointY, callback);
                    } catch (Exception e) {
                        logException(e);
                    }
                }, FOCUS_RETRY_INTERVAL_MS);
                break;
        }
    }

    private Rect calculateTapArea(float x, float y, float coefficient) {
        int areaSize = Math.round(200 * coefficient);

        int left = clamp((int) x - areaSize / 2, 0, textureView.getWidth() - areaSize);
        int top = clamp((int) y - areaSize / 2, 0, textureView.getHeight() - areaSize);

        int right = left + areaSize;
        int bottom = top + areaSize;

        if (left >= right || top >= bottom) {
            logError("Calculated tap area has invalid dimensions");
            return null; // Return null for invalid dimensions
        }

        return new Rect(left, top, right, bottom);
    }

    /**
     * Determine the space between the first two fingers
     */
    private static float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = findCameraIdForPosition();
            mCameraCharacteristics = manager.getCameraCharacteristics(cameraId);
            mSupportedPreviewSizes = getSupportedPreviewSizes(cameraId);
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            logException(e);
        }
    }

    private String findCameraIdForPosition() throws CameraAccessException {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (position.equals("front") && cOrientation == CameraCharacteristics.LENS_FACING_FRONT) {
                return cameraId;
            } else if (position.equals("back") && cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                return cameraId;
            }
        }
        return "0";
    }

    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();

            // Get display metrics
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            float density = displayMetrics.density; // DPR

            int desiredWidthPx = (int) (textureView.getWidth() * density);
            int desiredHeightPx = (int) (textureView.getHeight() * density);

            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, desiredWidthPx, desiredHeightPx);
            
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            configureTransform(textureView.getWidth(), textureView.getHeight());
            Surface surface = new Surface(texture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (cameraDevice == null) {
                        return;
                    }
                    captureSession = cameraCaptureSession;
                    eventListener.onCameraStarted();
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    logException(new Exception("Camera preview configuration failed"));
                }
            }, null);
        } catch (CameraAccessException e) {
            logException(e);
        }
    }

    private void updatePreview() {
        if (cameraDevice == null) {
            logException(new Exception("updatePreview error, return"));
        }
        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            logException(e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                // Permission denied
                activity.finish();
            }
        }
    }

    private Size getOptimalPreviewSize(Size[] sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        int sensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (sensorOrientation == 90 || sensorOrientation == 270) {
            targetRatio = (double) h / w;
        }

        if (sizes == null) {
            return null;
        }

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.getWidth() / size.getHeight();
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.getHeight() - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.getHeight() - targetHeight);
                }
            }
        }

//        Log.d(TAG, "optimal preview size: w: " + optimalSize.getWidth() + " h: " + optimalSize.getHeight());
        return optimalSize;
    }

    private int getOrientationHint() {
        int deviceRotation = getDeviceOrientation();
        int sensorOrientation = getSensorOrientation();
        return (sensorOrientation - deviceRotation + 360) % 360;
    }

    private int getDeviceOrientation() {
        WindowManager windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        int rotation = windowManager.getDefaultDisplay().getRotation();

        int deviceRotation = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                deviceRotation = 0;
                break;
            case Surface.ROTATION_90:
                deviceRotation = 90;
                break;
            case Surface.ROTATION_180:
                deviceRotation = 180;
                break;
            case Surface.ROTATION_270:
                deviceRotation = 270;
                break;
        }
        return deviceRotation;
    }

    private int getSensorOrientation() {
        if (mCameraCharacteristics == null) return -1;
        return mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (cameraDevice == null || !textureView.isAvailable() || mPreviewSize == null) {
            return;
        }

        Matrix matrix = new Matrix();

        int sensorRotation = getSensorOrientation();
        int deviceRotation = getDeviceOrientation();

        int centerX = viewWidth / 2;
        int centerY = viewHeight / 2;

        // Set the rotation transformation
        if (getDeviceOrientation() == getSensorOrientation() || Math.abs(getDeviceOrientation() - getSensorOrientation()) == 180 || getDeviceOrientation() == 180) {
            matrix.postRotate(-sensorRotation, centerX, centerY);
        } else {
            matrix.postRotate(sensorRotation, centerX, centerY);
        }


        // Calculate aspect ratio scaling
        float previewAspectRatio = (float) mPreviewSize.getWidth() / mPreviewSize.getHeight();
        float viewAspectRatio = (float) viewWidth / viewHeight;
        float scaleX = 1.0f;
        float scaleY = 1.0f;

        if (previewAspectRatio > viewAspectRatio) {
            scaleX = previewAspectRatio / viewAspectRatio;
        } else {
            scaleY = viewAspectRatio / previewAspectRatio;
        }

        // Apply the scale transformation
        matrix.postScale(scaleX, scaleY, centerX, centerY);

        // Undo the rotation transformation
        if (getDeviceOrientation() != getSensorOrientation()) {
            if (Math.abs(getDeviceOrientation() - getSensorOrientation()) != 180) {
                matrix.postRotate(-sensorRotation, centerX, centerY);
            } else {
                matrix.postRotate(sensorRotation - deviceRotation, centerX, centerY);
            }

        }

        // Apply the transformation
        textureView.setTransform(matrix);
    }

    public Size[] getSupportedPreviewSizes(String cameraId) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map != null) {
                return map.getOutputSizes(SurfaceTexture.class);
            } else {
                // Handle the case where map is null
                return new Size[0];
            }
        } catch (CameraAccessException e) {
            logException(e);
            // Handle the exception
            return new Size[0];
        }
    }


    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();

        super.onPause();
    }


    @Override
    public void onResume() {
        super.onResume();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
        startBackgroundThread();

    }

    private void closeCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
            mCameraCharacteristics = null;
            mSupportedPreviewSizes = null;
            mPreviewSize = null;
        }
    }

    private String getTempDirectoryPath() {
        File cache = null;

        // Use internal storage
        cache = activity.getCacheDir();

        // Create the cache directory if it doesn't exist
        cache.mkdirs();
        return cache.getAbsolutePath();
    }

    private String getTempFilePath() {
        return getTempDirectoryPath() + "/cpcp_capture_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".jpg";
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            logException(e);
        }
    }

    private void startPreview() throws Exception {
        if (cameraDevice == null || !textureView.isAvailable() || mPreviewSize == null) {
            return;
        }
        SurfaceTexture texture = textureView.getSurfaceTexture();
        assert texture != null;
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);
        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        previewRequestBuilder.addTarget(surface);
        cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                if (cameraDevice == null) {
                    return;
                }
                captureSession = cameraCaptureSession;
                updatePreview();
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                logException(new Exception("Configuration change"));
            }
        }, null);
    }

    private int getCameraToUse() {
        if (cameraId != null) {
            return Integer.parseInt(cameraId);
        }
        return 0;
    }

    private void logException(Exception e) {
        logError(e.getMessage());
    }

    private void logException(String message, Exception e) {
        logError(message + ": " + e.getMessage());
    }

    private void logError(String message) {
        Log.e(TAG, message);
        if (bridge != null) {
            bridge.logToJs(TAG + ": " + message, "error");
        }
    }

    private void logMessage(String message) {
        Log.d(TAG, message);
        if (bridge != null) {
            bridge.logToJs(TAG + ": " + message, "debug");
        }
    }
}
