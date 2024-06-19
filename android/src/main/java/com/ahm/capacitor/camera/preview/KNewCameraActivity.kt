package com.ahm.capacitor.camera.preview

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout;
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ahm.capacitor.camera.preview.capacitorcamerapreview.databinding.NewCameraActivityBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias FaceRotationAnalyzerListener = (rotationAnalyzer: String, bounds: Rect?) -> Unit

class KNewCameraActivity : Fragment() {
    interface CameraPreviewListener {
        fun onPictureTaken(originalPicture: String?)
        fun onPictureTakenError(message: String?)
        fun onCameraStarted()
        fun onCameraDetected(step: String, bounds: Rect?)
    }

    private var eventListener: CameraPreviewListener? = null
    private lateinit var viewBinding: NewCameraActivityBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    var defaultCamera: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    var toBack = false
    var storeToFile = false
    var enableFaceRecognition = false
    var width = 0
    var height = 0

    private var x = 0
    private var y = 0

    fun setEventListener(listener: CameraPreviewListener?) {
        this.eventListener = listener
    }
    fun setRect(x: Int, y: Int, width: Int, height: Int) {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(requireContext(), "Permission request denied", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = NewCameraActivityBinding.inflate(inflater, container, false)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        val layoutParams = FrameLayout.LayoutParams(width, height)
        layoutParams.setMargins(x, y, 0, 0)

        val frameContainerLayout: FrameLayout = viewBinding.frameContainer
        frameContainerLayout.layoutParams = layoutParams

        return viewBinding.root
    }


    fun hasCamera(): Boolean {
        return true
    }

    fun switchCamera() {
        defaultCamera =
            if (defaultCamera == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
        startCamera()
    }

    fun getSupportedFlashModes(context: Context): List<String> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIdList = cameraManager.cameraIdList
        val supportedFlashModes = mutableListOf<String>()

        for (id in cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
            if (flashAvailable == true) {
                supportedFlashModes.add(id)
            }
        }

        return supportedFlashModes
    }

    fun turnOnFlash() {
        imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
    }

    fun turnOffFlash() {
        imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
    }

    fun takePicture() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Set up image capture listener, which is triggered after the photo has been taken
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val base64Image = imageProxyToBase64(image)
                        eventListener?.onPictureTaken(base64Image)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting image to base64", e)
                        eventListener?.onPictureTakenError(e.message)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    eventListener?.onPictureTakenError(exc.message)
                }
            }
        )
    }

    private fun imageProxyToBase64(image: ImageProxy): String {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(
            {
                // Used to bind the lifecycle of cameras to the lifecycle owner
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Preview
                val preview = Preview.Builder()
                    .build().also {
                        it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder().build()

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it ->
                        it.setAnalyzer(cameraExecutor, FaceAnalyzer { step, bounds ->
                            Log.d(TAG, "Step: $step")
                            bounds?.let { rect ->
                                Log.d(TAG, "Bounds: ${rect.flattenToString()}")
                            }
                            eventListener?.onCameraDetected(step, bounds)
                        })
                    }

                // Select back camera as a default
                val cameraSelector = defaultCamera

                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    if (enableFaceRecognition) {
                        cameraProvider.bindToLifecycle(
                            this,
                            cameraSelector,
                            preview,
                            imageCapture,
                            imageAnalyzer
                        )
                    } else {
                        cameraProvider.bindToLifecycle(
                            this,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    }

                    eventListener?.onCameraStarted()
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CocosCapCameraPreview"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(Manifest.permission.CAMERA).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private class FaceAnalyzer(listener: FaceRotationAnalyzerListener? = null) : ImageAnalysis.Analyzer {
        private val faceDetector: FaceDetector = FaceDetection.getClient()
        private val listeners = ArrayList<FaceRotationAnalyzerListener>().apply { listener?.let { add(it) } }
        private var faceObjects: MutableList<Face> = ArrayList()

        @ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.size == 0) {
                            faceObjects.clear()
                            listeners.forEach { it("NO_FACES", null) }
                        } else if (faces.size > 1) {
                            faceObjects.clear()
                            listeners.forEach { it("MORE_THAN_ONE_FACE", null) }
                        } else {
                            val face = faces[0]
                            faceObjects.add(face)
                            if (faceObjects.size > 15) {
                                faceObjects.removeFirst()
                                // Euler Y angle represents the rotation around the vertical axis.
                                val eulerY: Double = (faceObjects.sumOf { it.headEulerAngleY.toDouble() }) / faceObjects.size
                                val bounds: Rect = face.boundingBox

                                var step = "CENTER"
                                if (eulerY > 28) {
                                    step = "LEFT"
                                } else if (eulerY < -28) {
                                    step = "RIGHT"
                                }
                                listeners.forEach { it(step, bounds) }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Face detection failed: " + e.message)
                    }
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }
    }
}

