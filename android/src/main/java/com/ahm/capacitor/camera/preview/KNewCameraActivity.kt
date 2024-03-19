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
        //        fun onFocusSet(pointX: Int, pointY: Int)
//        fun onFocusSetError(message: String?)
//        fun onBackButton()
        fun onCameraStarted()
        fun onCameraDetected(rotation: String, bounds: Rect?)
    }

    private var eventListener: CameraPreviewListener? = null
    private lateinit var viewBinding: NewCameraActivityBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    //    private var opacity = 0f
    // The first rear facing camera
//    private var defaultCameraId = 0
    var defaultCamera: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    //    var tapToTakePicture = false
    //    var dragEnabled = false
//    var tapToFocus = false
//    var disableExifHeaderStripping = false
//    var storeToFile = false
    var toBack = false
    var storeToFile = false
    var enableFaceRecognition = false
//    var enableOpacity = false
    //    var enableZoom = false

    private var width = 0
    private var height = 0
    private var x = 0
    private var y = 0

    //    fragment.setEventListener(this)
    //    fragment.defaultCamera = position
    //    fragment.tapToTakePicture = false
    //    fragment.dragEnabled = false
    //    fragment.tapToFocus = true
    //    fragment.disableExifHeaderStripping = disableExifHeaderStripping
    //    fragment.storeToFile = storeToFile
    //    fragment.toBack = toBack
    //    fragment.enableOpacity = enableOpacity
    //    fragment.enableZoom = enableZoom

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
        viewBinding = NewCameraActivityBinding.inflate(layoutInflater, container, false)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

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

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())

        val contentValues =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CocosCapital-Image")
                }
            }

        // Create output options object which contains file + metadata
        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
                .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    eventListener?.onPictureTakenError(exc.message)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                    if (!storeToFile) {
                        eventListener?.onPictureTaken(imageToBase64(output.savedUri!!))
                    } else {
                        eventListener?.onPictureTaken(output.savedUri.toString())
                    }
                }
            }
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(
            {
                // Used to bind the lifecycle of cameras to the lifecycle owner
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Preview
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
//                        .setTargetResolution(Size(1080, 1920)) // Set target resolution here
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY) // Set capture mode here
                    .build()

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it ->
                        it.setAnalyzer(cameraExecutor, FaceAnalyzer { rotation, bounds ->
                            Log.d(TAG, "Rotation: $rotation")
                            bounds?.let{ rect ->
                                Log.d(TAG, "Bounds: ${rect.flattenToString()}")
                            }
                            eventListener?.onCameraDetected(rotation, bounds)
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

    private fun imageToBase64(imageUri: Uri): String? {
        // Read image file into byte array
        val file = File(imageUri.path!!)
        val fis = FileInputStream(file)
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var length: Int
        while (fis.read(buffer).also { length = it } != -1) {
            baos.write(buffer, 0, length)
        }
        val imageBytes = baos.toByteArray()

        // Convert byte array to Base64 string
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
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

                                var rotation = "CENTER"
                                if (eulerY > 28) {
                                    rotation = "LEFT"
                                } else if (eulerY < -28) {
                                    rotation = "RIGHT"
                                }
                                listeners.forEach { it(rotation, bounds) }
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

