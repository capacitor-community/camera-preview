package com.ahm.capacitor.camera.preview.camera2api;

import static androidx.core.math.MathUtils.clamp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
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
import com.ahm.capacitor.camera.preview.TapGestureDetector;
import com.getcapacitor.Bridge;
import com.getcapacitor.Logger;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Camera2Activity extends Fragment {

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

        void onCameraStartedError(String message);
    }

    /**
     * Private properties
     */
    private static final String TAG = "CameraPreviewActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private static final float PINCH_ZOOM_SENSITIVITY = 0.01f;
    private static final long PINCH_ZOOM_DEBOUNCE_DELAY_MS = 10; // milliseconds

    private static final int DEFAULT_PICTURE_QUALITY = 85;
    private static final double ASPECT_TOLERANCE = 0.1;
    private static final int TAP_AREA_BASE = 200;
    private static final float FOCUS_AREA_COEFFICIENT = 1.0f;
    private static final float METERING_AREA_COEFFICIENT = 1.5f;
    private static final int METERING_WEIGHT = 1000;

    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;

    private Camera2Activity.CameraPreviewListener eventListener;

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

    // Track teardown to avoid restarting preview while the fragment is stopping.
    private volatile boolean isClosing = false;

    private enum RecordingState {
        INITIALIZING,
        STARTED,
        STOPPED
    }

    private final Camera2Activity.RecordingState mRecordingState = Camera2Activity.RecordingState.INITIALIZING;
    private MediaRecorder mRecorder = null;
    private String recordFilePath;

    private String cameraId;

    private static final int MAX_CONFIGURE_SESSION_RETRIES = 2;
    private static final int CONFIGURE_SESSION_RETRY_DELAY_MS = 200;
    private int configureSessionRetryCount = 0;
    private final Handler configureSessionRetryHandler = new Handler(Looper.getMainLooper());

    // Timeout for camera opening
    private static final int CAMERA_OPEN_TIMEOUT_MS = 3000;
    private Handler cameraOpenTimeoutHandler = new Handler(Looper.getMainLooper());
    private boolean cameraOpened = false;

    /**
     * Public properties
     */
    public Camera2Preview camera2Preview;
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
    public boolean lockOrientation = false;

    public int width;
    public int height;
    public int x;
    public int y;

    public final float NO_MAX_ZOOM_LIMIT = -1f;
    public float maxZoomLimit = NO_MAX_ZOOM_LIMIT;

    /**
     * Public methods
     */
    public void setEventListener(Camera2Activity.CameraPreviewListener listener) {
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

        // Ensure any existing preview session is closed before creating a still-capture session.
        // This avoids abandoned surfaces / broken pipe errors on some devices when switching sessions.
        closeCaptureSession();

        int imageWidth = width;
        int imageHeight = height;

        if (cropToPreview) {
            if (mPreviewSize == null) {
                if (eventListener != null) {
                    eventListener.onPictureTakenError("Preview size not ready");
                }
                return;
            }
            imageWidth = mPreviewSize.getWidth();
            imageHeight = mPreviewSize.getHeight();
        } else {
            Size[] jpegSizes = null;
            if (mCameraCharacteristics != null) {
                jpegSizes = mCameraCharacteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(android.graphics.ImageFormat.JPEG);
            }
            if (jpegSizes != null && jpegSizes.length > 0) {
                imageWidth = jpegSizes[0].getWidth();
                imageHeight = jpegSizes[0].getHeight();
            }
        }
        logMessage("takePicture: " + imageWidth + ", " + imageHeight + ", " + quality);

        // Use >1 maxImages to avoid buffer starvation on slower devices.
        final ImageReader reader = ImageReader.newInstance(imageWidth, imageHeight, android.graphics.ImageFormat.JPEG, 2);
        List<Surface> outputSurfaces = new ArrayList<>(2);
        outputSurfaces.add(reader.getSurface());
        outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

        final CaptureRequest.Builder stillCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        stillCaptureRequestBuilder.addTarget(reader.getSurface());
        stillCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        // Apply requested JPEG quality in the camera pipeline to avoid extra re-encoding work.
        stillCaptureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) quality);

        // Always request the correct orientation from the camera pipeline to reduce post-processing cost.
        int deviceOrientation = activity.getWindowManager().getDefaultDisplay().getRotation();
        stillCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(deviceOrientation));

        Rect zoomRect = getZoomRect(getCurrentZoomLevel());
        if (zoomRect != null) {
            stillCaptureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
        }
        final CameraCaptureSession[] stillCaptureSessionRef = new CameraCaptureSession[1];
        final boolean[] imageHandled = new boolean[] { false };

        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                if (imageHandled[0]) {
                    // Drain any extra image to avoid ImageReader buffer exhaustion warnings.
                    logMessage("Extra image received; draining to avoid buffer exhaustion");
                    try (Image extra = reader.acquireLatestImage()) {
                        // no-op
                    } catch (Exception ignored) {}
                    return;
                }
                imageHandled[0] = true;

                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) {
                        throw new Exception("No image available from ImageReader");
                    }
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    if (!storeToFile) {
                        String encodedImage = Base64.encodeToString(bytes, Base64.NO_WRAP);
                        eventListener.onPictureTaken(encodedImage);
                    } else {
                        String path = getTempFilePath();
                        if (disableExifHeaderStripping) {
                            // Preserve EXIF and avoid expensive decode/rotate/re-encode when EXIF stripping is disabled.
                            saveRaw(bytes, path);
                        } else {
                            save(bytes, path, quality);
                        }
                        eventListener.onPictureTaken(path);
                    }
                } catch (Exception e) {
                    eventListener.onPictureTakenError(e.getMessage());
                    logException(e);
                } finally {
                    try {
                        if (image != null) image.close();
                    } catch (Exception ignored) {}
                    try {
                        reader.setOnImageAvailableListener(null, null);
                        reader.close();
                    } catch (Exception ignored) {}
                    try {
                        if (stillCaptureSessionRef[0] != null) {
                            stillCaptureSessionRef[0].close();
                        }
                    } catch (Exception ignored) {}
                    try {
                        if (!isClosing) {
                            startPreview();
                        } else {
                            logMessage("Skip preview restart because camera is closing");
                        }
                    } catch (Exception e) {
                        eventListener.onPictureTakenError(e.getMessage());
                        logException(e);
                    }
                }
            }

            private void save(byte[] bytes, String filePath, int quality) throws Exception {
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                // Determine the correct rotation
                int deviceOrientation = activity.getWindowManager().getDefaultDisplay().getRotation();
                int rotation = ORIENTATIONS.get(deviceOrientation);

                // Rotate the bitmap
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                // Save the rotated bitmap to a file
                try (OutputStream output = new FileOutputStream(filePath)) {
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
                }
            }

            private void saveRaw(byte[] bytes, String filePath) throws Exception {
                try (OutputStream output = new FileOutputStream(filePath)) {
                    output.write(bytes);
                }
            }
        };
        // Ensure we have a valid handler for image callbacks; fall back to main looper handler to avoid listener never firing
        reader.setOnImageAvailableListener(readerListener, getEffectiveHandler());
        cameraDevice.createCaptureSession(
            outputSurfaces,
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession stillCaptureSession) {
                    try {
                        // reset retry counter on success to avoid stale state leading to hangs
                        configureSessionRetryCount = 0;
                        stillCaptureSessionRef[0] = stillCaptureSession;
                        stillCaptureSession.capture(stillCaptureRequestBuilder.build(), null, mBackgroundHandler);
                    } catch (Exception e) {
                        eventListener.onPictureTakenError(e.getMessage());
                        logException(e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    handleConfigureFailedWithRetry(
                        () -> {
                            try {
                                logMessage("Retrying configuration after failure");
                                cameraDevice.createCaptureSession(outputSurfaces, this, mBackgroundHandler);
                            } catch (Exception e) {
                                eventListener.onPictureTakenError(e.getMessage());
                                logException(e);
                            }
                        },
                        () -> {
                            try {
                                reader.setOnImageAvailableListener(null, null);
                            } catch (Exception ignored) {}
                            try {
                                reader.close();
                            } catch (Exception ignored) {}
                        },
                        () -> {
                            try {
                                if (eventListener != null) eventListener.onPictureTakenError("Configuration failed");
                            } catch (Exception ignored) {}
                        }
                    );
                }
            },
            mBackgroundHandler
        );
    }

    public void takeSnapshot(final int quality) throws Exception {
        if (cameraDevice == null) {
            return;
        }
        logMessage("takeSnapshot");

        // Ensure any existing preview session is closed before creating a still-capture session.
        closeCaptureSession();

        if (width <= 0 || height <= 0) {
            if (eventListener != null) {
                eventListener.onSnapshotTakenError("Snapshot size not ready");
            }
            return;
        }

        final ImageReader reader = ImageReader.newInstance(width, height, android.graphics.ImageFormat.JPEG, 2);
        List<Surface> outputSurfaces = new ArrayList<>(2);
        outputSurfaces.add(reader.getSurface());
        outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
        final CaptureRequest.Builder stillCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        stillCaptureRequestBuilder.addTarget(reader.getSurface());
        stillCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        stillCaptureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) quality);
        // Orientation
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        stillCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
        Rect zoomRect = getZoomRect(getCurrentZoomLevel());
        if (zoomRect != null) {
            stillCaptureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
        }
        final CameraCaptureSession[] stillCaptureSessionRef = new CameraCaptureSession[1];
        final boolean[] imageHandled = new boolean[] { false };

        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                if (imageHandled[0]) {
                    // Drain any extra image to avoid ImageReader buffer exhaustion warnings.
                    logMessage("Extra snapshot image received; draining to avoid buffer exhaustion");
                    try (Image extra = reader.acquireLatestImage()) {
                        // no-op
                    } catch (Exception ignored) {}
                    return;
                }
                imageHandled[0] = true;

                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) {
                        throw new Exception("No image available from ImageReader");
                    }
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
                } finally {
                    try {
                        if (image != null) image.close();
                    } catch (Exception ignored) {}
                    try {
                        reader.setOnImageAvailableListener(null, null);
                        reader.close();
                    } catch (Exception ignored) {}
                    try {
                        if (stillCaptureSessionRef[0] != null) {
                            stillCaptureSessionRef[0].close();
                        }
                    } catch (Exception ignored) {}
                    try {
                        if (!isClosing) {
                            startPreview();
                        } else {
                            logMessage("Skip preview restart because camera is closing");
                        }
                    } catch (Exception e) {
                        eventListener.onSnapshotTakenError(e.getMessage());
                        logException(e);
                    }
                }
            }
        };
        // Use effective handler to avoid case where background thread hasn't been started yet
        reader.setOnImageAvailableListener(readerListener, getEffectiveHandler());
        cameraDevice.createCaptureSession(
            outputSurfaces,
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        // reset retry counter on success
                        configureSessionRetryCount = 0;
                        stillCaptureSessionRef[0] = session;
                        session.capture(stillCaptureRequestBuilder.build(), null, mBackgroundHandler);
                    } catch (Exception e) {
                        eventListener.onSnapshotTakenError(e.getMessage());
                        logException(e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    handleConfigureFailedWithRetry(
                        () -> {
                            try {
                                logMessage("Retrying configuration after failure");
                                cameraDevice.createCaptureSession(outputSurfaces, this, mBackgroundHandler);
                            } catch (Exception e) {
                                eventListener.onSnapshotTakenError(e.getMessage());
                                logException(e);
                            }
                        },
                        () -> {
                            try {
                                reader.setOnImageAvailableListener(null, null);
                            } catch (Exception ignored) {}
                            try {
                                reader.close();
                            } catch (Exception ignored) {}
                        },
                        () -> {
                            try {
                                if (eventListener != null) eventListener.onSnapshotTakenError("Configuration failed");
                            } catch (Exception ignored) {}
                        }
                    );
                }
            },
            mBackgroundHandler
        );
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

            cameraDevice.createCaptureSession(
                surfaces,
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        captureSession = session;
                        try {
                            // reset retry counter on success
                            configureSessionRetryCount = 0;
                            // Build the capture request, and start the session
                            CaptureRequest.Builder recordCaptureRequestBuilder = cameraDevice.createCaptureRequest(
                                CameraDevice.TEMPLATE_RECORD
                            );
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
                        handleConfigureFailedWithRetry(
                            () -> {
                                try {
                                    logMessage("Retrying configuration after failure");
                                    cameraDevice.createCaptureSession(surfaces, this, mBackgroundHandler);
                                } catch (Exception e) {
                                    eventListener.onStartRecordVideoError(e.getMessage());
                                    logException(e);
                                }
                            },
                            "Configuration failed"
                        );
                    }
                },
                null
            );

            eventListener.onStartRecordVideo();
        } catch (Exception e) {
            eventListener.onStartRecordVideoError(e.getMessage());
        }
    }

    // Ensure we always have a valid handler when a background handler hasn't been started yet
    private Handler getEffectiveHandler() {
        if (mBackgroundHandler != null) return mBackgroundHandler;
        return new Handler(Looper.getMainLooper());
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
        if (mCameraCharacteristics != null) {
            maxZoom = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        }
        return (maxZoom != null) ? maxZoom : 1.0f;
    }

    public float getMinZoomLevel() {
        return 1.0f; // The minimum zoom level is always 1.0 (no zoom)
    }

    public void setCurrentZoomLevel(float zoomLevel) throws Exception {
        if (maxZoomLimit != NO_MAX_ZOOM_LIMIT && zoomLevel > maxZoomLimit) {
            logMessage("Zoom level exceeds max zoom limit: " + maxZoomLimit);
            zoomLevel = maxZoomLimit;
        }

        if (mCameraCharacteristics == null || previewRequestBuilder == null || captureSession == null) {
            // Preview/session may not be ready yet; ignore zoom until it is.
            logMessage("Zoom ignored because preview is not ready");
            return;
        }
        logMessage("setCurrentZoomLevel to: " + zoomLevel);
        logMessage("currentZoomLevel (before setCurrentZoomLevel): " + getCurrentZoomLevel());

        float maxZoom = getMaxZoomLevel();
        logMessage("maxZoom: " + maxZoom);
        float minZoom = getMinZoomLevel();
        logMessage("minZoom: " + minZoom);

        float newLevel = Math.max(minZoom, Math.min(zoomLevel, maxZoom));
        logMessage("newLevel: " + newLevel);

        String eventData = "{ \"level\": " + newLevel + " }";
        bridge.triggerWindowJSEvent("CameraPreview.zoomLevelChanged", eventData);

        Rect zoomRect = getZoomRect(newLevel);
        if (zoomRect != null) {
            previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, null);
        }
    }

    public float getCurrentZoomLevel() {
        if (mCameraCharacteristics != null) {
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

    private int getCameraToUse() {
        if (cameraId != null) {
            try {
                return Integer.parseInt(cameraId);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
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
        if (mCameraCharacteristics != null && previewRequestBuilder != null) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, Integer.parseInt(flashMode));
            logMessage("setFlashMode: " + flashMode);
        }
    }

    public void onOrientationChange(String orientation) {
        try {
            logMessage("onOrientationChanged: " + orientation);
            Logger.verbose("device orientation: " + getDeviceOrientation());
            Logger.verbose("sensor orientation: " + getSensorOrientation());
            configureTransform(textureView.getWidth(), textureView.getHeight());
        } catch (Exception e) {
            logException("onOrientationChanged error", e);
        }
    }

    /**
     * Internal methods and listeners
     */
    private Rect getZoomRect(float zoomLevel) {
        if (mCameraCharacteristics == null) return null;

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
            try {
                openCamera();
            } catch (Exception e) {
                logException(e);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            try {
                configureTransform(width, height);
            } catch (Exception e) {
                logException(e);
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            try {
                cameraOpened = true;
                cameraOpenTimeoutHandler.removeCallbacksAndMessages(null);
                cameraDevice = camera;
                createCameraPreview();
            } catch (Exception e) {
                logException(e);
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            try {
                closeCamera();
            } catch (Exception e) {
                logException(e);
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            try {
                closeCamera();
                // Notify listener that camera start failed to avoid hanging saved calls
                if (eventListener != null) {
                    try {
                        eventListener.onCameraStartedError("Camera device error: " + error);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                logException(e);
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = null;
        try {
            String appResourcesPackage = getActivity().getPackageName();

            // Inflate the layout for this fragment
            view = inflater.inflate(getResources().getIdentifier("camera_activity", "layout", appResourcesPackage), container, false);

            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
            layoutParams.setMargins(x, y, 0, 0);
            frameContainerLayout = view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));
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
        } catch (Exception e) {
            logException(e);
        }

        return view;
    }

    private void setupTouchAndBackButton() {
        final GestureDetector gestureDetector = new GestureDetector(activity.getApplicationContext(), new TapGestureDetector());

        activity.runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    try {
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
                                        FrameLayout.LayoutParams layoutParams =
                                            (FrameLayout.LayoutParams) frameContainerLayout.getLayoutParams();

                                        boolean isSingleTapTouch = gestureDetector.onTouchEvent(event);
                                        int action = event.getAction();
                                        int eventCount = event.getPointerCount();
                                        Logger.verbose("onTouch event, action, count: " + event + ", " + action + ", " + eventCount);
                                        if (eventCount > 1) {
                                            // handle multi-touch events
                                            if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_2_DOWN) {
                                                mDist = getFingerSpacing(event);
                                                Logger.verbose("onTouch start: mDist=" + mDist);
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
                                                            public void onCaptureCompleted(
                                                                @NonNull CameraCaptureSession session,
                                                                @NonNull CaptureRequest request,
                                                                @NonNull TotalCaptureResult result
                                                            ) {
                                                                super.onCaptureCompleted(session, request, result);
                                                                try {
                                                                    eventListener.onFocusSet(tapX, tapY);
                                                                    takePicture(0, 0, DEFAULT_PICTURE_QUALITY);
                                                                } catch (Exception e) {
                                                                    eventListener.onFocusSetError(e.getMessage());
                                                                    logException(e);
                                                                }
                                                            }
                                                        }
                                                    );
                                                } else if (tapToTakePicture) {
                                                    takePicture(0, 0, DEFAULT_PICTURE_QUALITY);
                                                } else if (tapToFocus) {
                                                    int tapX = (int) event.getX(0);
                                                    int tapY = (int) event.getY(0);

                                                    CameraCaptureSession.CaptureCallback captureCallback =
                                                        new CameraCaptureSession.CaptureCallback() {
                                                            @Override
                                                            public void onCaptureCompleted(
                                                                @NonNull CameraCaptureSession session,
                                                                @NonNull CaptureRequest request,
                                                                @NonNull TotalCaptureResult result
                                                            ) {
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

                                                    setFocusArea(tapX, tapY, captureCallback);
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
                    } catch (Exception e) {
                        logException(e);
                    }
                }

                private float mDist = 0F;
                private long lastZoomTime = 0;

                private void handlePinchZoom(MotionEvent event) {
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
        captureSession.capture(
            previewRequestBuilder.build(),
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(
                    @NonNull CameraCaptureSession session,
                    @NonNull CaptureRequest request,
                    @NonNull TotalCaptureResult result
                ) {
                    try {
                        triggerAutofocus(pointX, pointY, callback);
                    } catch (Exception e) {
                        logException(e);
                    }
                }
            },
            mBackgroundHandler
        );
    }

    private void triggerAutofocus(final int pointX, final int pointY, final CameraCaptureSession.CaptureCallback callback) {
        try {
            // Calculate focus and metering areas
            Rect focusRect = calculateTapArea(pointX, pointY, FOCUS_AREA_COEFFICIENT);
            Rect meteringRect = calculateTapArea(pointX, pointY, METERING_AREA_COEFFICIENT);

            if (focusRect == null || meteringRect == null) {
                logError("Invalid focus or metering area dimensions");
                return;
            }

            // Set AF, AE, and AWB regions
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_REGIONS,
                new MeteringRectangle[] { new MeteringRectangle(focusRect, METERING_WEIGHT) }
            );
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AE_REGIONS,
                new MeteringRectangle[] { new MeteringRectangle(meteringRect, METERING_WEIGHT) }
            );
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AWB_REGIONS,
                new MeteringRectangle[] { new MeteringRectangle(meteringRect, METERING_WEIGHT) }
            );

            // Set AF mode to auto
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);

            // Start autofocus
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            captureSession.capture(
                previewRequestBuilder.build(),
                new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull TotalCaptureResult result
                    ) {
                        clearFocusRetry();
                        focusRetryCount = 0;
                        handleFocusResult(result, pointX, pointY, callback);
                    }
                },
                mBackgroundHandler
            );
        } catch (Exception e) {
            logException(e);
        }
    }

    private void clearFocusRetry() {
        if (focusHandler != null) focusHandler.removeCallbacksAndMessages(null);
    }

    private void handleFocusResult(
        TotalCaptureResult result,
        final int pointX,
        final int pointY,
        final CameraCaptureSession.CaptureCallback callback
    ) {
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
                } catch (Exception e) {
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
                    Logger.debug("Max focus retry count reached");
                    return;
                }
                focusHandler = new Handler(Looper.getMainLooper());
                focusHandler.postDelayed(
                    () -> {
                        try {
                            triggerAutofocus(pointX, pointY, callback);
                        } catch (Exception e) {
                            logException(e);
                        }
                    },
                    FOCUS_RETRY_INTERVAL_MS
                );
                break;
            case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
                // Autofocus is still in progress, wait and check again
                clearFocusRetry();
                focusRetryCount++;
                if (focusRetryCount >= MAX_FOCUS_RETRY_COUNT) {
                    Logger.debug("Max focus retry count reached");
                    return;
                }
                focusHandler = new Handler(Looper.getMainLooper());
                focusHandler.postDelayed(
                    () -> {
                        try {
                            handleFocusResult(result, pointX, pointY, callback);
                        } catch (Exception e) {
                            logException(e);
                        }
                    },
                    FOCUS_RETRY_INTERVAL_MS
                );
                break;
        }
    }

    private Rect calculateTapArea(float x, float y, float coefficient) {
        int areaSize = Math.round(TAP_AREA_BASE * coefficient);

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
        // Ensure background thread/handler available before opening camera so callbacks that rely on it won't hang
        if (mBackgroundHandler == null || mBackgroundThread == null) {
            startBackgroundThread();
        }
        isClosing = false;
        cameraOpened = false;
        cameraOpenTimeoutHandler.postDelayed(
            () -> {
                if (!cameraOpened) {
                    logError("Camera open timed out");
                    if (eventListener != null) eventListener.onCameraStartedError("Camera open timed out");
                    closeCamera();
                }
            },
            CAMERA_OPEN_TIMEOUT_MS
        );

        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = findCameraIdForPosition();
            mCameraCharacteristics = manager.getCameraCharacteristics(cameraId);
            mSupportedPreviewSizes = getSupportedPreviewSizes(cameraId);
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA_PERMISSION);
                return;
            }

            manager.openCamera(cameraId, stateCallback, null);
        } catch (Exception e) {
            logException(e);
        }
    }

    private String findCameraIdForPosition() throws Exception {
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

            // TextureView dimensions are already in pixels; avoid scaling by density to prevent oversized preview buffers.
            int desiredWidthPx = textureView.getWidth();
            int desiredHeightPx = textureView.getHeight();

            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, desiredWidthPx, desiredHeightPx);

            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            configureTransform(textureView.getWidth(), textureView.getHeight());
            Surface surface = new Surface(texture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(
                Collections.singletonList(surface),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                        try {
                            if (cameraDevice == null) {
                                return;
                            }
                            captureSession = cameraCaptureSession;
                            // reset retry counter on success
                            configureSessionRetryCount = 0;
                            eventListener.onCameraStarted();
                            updatePreview();
                        } catch (Exception e) {
                            logException(e);
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        handleConfigureFailedWithRetryForStart(
                            () -> {
                                try {
                                    createCameraPreview(); // Retry creating the camera preview
                                } catch (Exception e) {
                                    if (eventListener != null) {
                                        try {
                                            eventListener.onCameraStartedError("Retry failed: " + e.getMessage());
                                        } catch (Exception ignored) {}
                                    }
                                }
                            },
                            "Configuration failed"
                        );
                    }
                },
                null
            );
        } catch (Exception e) {
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
        } catch (Exception e) {
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
        final double ASPECT_TOLERANCE_LOCAL = ASPECT_TOLERANCE;
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
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE_LOCAL) continue;
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

        Logger.verbose("optimal preview size: w: " + optimalSize.getWidth() + " h: " + optimalSize.getHeight());
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
        if (
            getDeviceOrientation() == getSensorOrientation() ||
            Math.abs(getDeviceOrientation() - getSensorOrientation()) == 180 ||
            getDeviceOrientation() == 180
        ) {
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
        } catch (Exception e) {
            logException(e);
            // Handle the exception
            return new Size[0];
        }
    }

    @Override
    public void onPause() {
        isClosing = true;
        closeCamera();
        stopBackgroundThread();

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        isClosing = false;
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
        startBackgroundThread();
    }

    private void closeCamera() {
        isClosing = true;
        closeCaptureSession();
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
        if (mBackgroundThread != null) return;
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread == null) return;
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (Exception e) {
            logException(e);
        }
    }

    private void startPreview() throws Exception {
        if (cameraDevice == null || !textureView.isAvailable() || mPreviewSize == null) {
            return;
        }
        // Close any existing session before starting a new preview session.
        closeCaptureSession();
        SurfaceTexture texture = textureView.getSurfaceTexture();
        assert texture != null;
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);
        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        previewRequestBuilder.addTarget(surface);
        cameraDevice.createCaptureSession(
            Collections.singletonList(surface),
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try {
                        if (cameraDevice == null) {
                            return;
                        }
                        captureSession = cameraCaptureSession;
                        // reset retry counter on success
                        configureSessionRetryCount = 0;
                        updatePreview();
                    } catch (Exception e) {
                        logException(e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    handleConfigureFailedWithRetry(
                        () -> {
                            try {
                                startPreview(); // or takePicture(), etc.
                            } catch (Exception e) {
                                eventListener.onPictureTakenError("Retry failed: " + e.getMessage());
                            }
                        },
                        "Configuration change"
                    );
                }
            },
            null
        );
    }

    /**
     * Safely close any existing capture session to avoid BufferQueue abandonment and
     * camera configuration failures when switching between preview and still capture.
     */
    private void closeCaptureSession() {
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
            } catch (Exception ignored) {}
            try {
                captureSession.abortCaptures();
            } catch (Exception ignored) {}
            try {
                captureSession.close();
            } catch (Exception ignored) {}
            captureSession = null;
        }
    }

    private void handleConfigureFailedWithRetry(Runnable retryAction, String errorMessage) {
        if (configureSessionRetryCount < MAX_CONFIGURE_SESSION_RETRIES) {
            configureSessionRetryCount++;
            configureSessionRetryHandler.postDelayed(retryAction, CONFIGURE_SESSION_RETRY_DELAY_MS);
        } else {
            if (eventListener != null) {
                try {
                    eventListener.onPictureTakenError(errorMessage);
                } catch (Exception ignored) {}
            }
            configureSessionRetryCount = 0;
        }
    }

    // Overload with cleanup and custom error callback (used for ImageReader/session resources)
    private void handleConfigureFailedWithRetry(Runnable retryAction, Runnable onFinalFailure, Runnable onErrorCallback) {
        if (configureSessionRetryCount < MAX_CONFIGURE_SESSION_RETRIES) {
            configureSessionRetryCount++;
            configureSessionRetryHandler.postDelayed(retryAction, CONFIGURE_SESSION_RETRY_DELAY_MS);
        } else {
            try {
                if (onErrorCallback != null) onErrorCallback.run();
            } catch (Exception ignored) {}
            try {
                if (onFinalFailure != null) onFinalFailure.run();
            } catch (Exception ignored) {}
            configureSessionRetryCount = 0;
        }
    }

    // Similar retry helper used for starting the camera/preview; calls onCameraStartedError on final failure
    private void handleConfigureFailedWithRetryForStart(Runnable retryAction, String errorMessage) {
        if (configureSessionRetryCount < MAX_CONFIGURE_SESSION_RETRIES) {
            configureSessionRetryCount++;
            configureSessionRetryHandler.postDelayed(retryAction, CONFIGURE_SESSION_RETRY_DELAY_MS);
        } else {
            if (eventListener != null) {
                try {
                    eventListener.onCameraStartedError(errorMessage);
                } catch (Exception ignored) {}
            }
            configureSessionRetryCount = 0;
        }
    }

    private void logException(Exception e) {
        logError(e.getMessage());
    }

    private void logException(String message, Exception e) {
        logError(message + ": " + e.getMessage());
    }

    private void logError(String message) {
        Logger.error(message);
        if (bridge != null) {
            try {
                String sanitisedMessage = message.replace("\"", "\\\"");
                bridge.triggerWindowJSEvent("CameraPreview.error", "{ \"message\": \"" + sanitisedMessage + "\" }");
            } catch (Exception e) {
                Logger.error("Error in logError: " + e.getMessage());
            }
        }
    }

    private void logMessage(String message) {
        Logger.debug(message);
        if (bridge != null) {
            try {
                String sanitisedMessage = message.replace("\"", "\\\"");
                bridge.triggerWindowJSEvent("CameraPreview.log", "{ \"message\": \"" + sanitisedMessage + "\" }");
            } catch (Exception e) {
                Logger.error("Error in logMessage: " + e.getMessage());
            }
        }
    }
}
