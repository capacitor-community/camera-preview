//
//  CameraController.swift
//  Plugin
//
//  Created by Ariel Hernandez Musa on 7/14/19.
//  Copyright © 2019 Max Lynch. All rights reserved.
//

import AVFoundation
import UIKit
import CoreMotion

class CameraController: NSObject {
    var captureSession: AVCaptureSession?
    
    var currentCameraPosition: CameraPosition?
    
    var frontCamera: AVCaptureDevice?
    var frontCameraInput: AVCaptureDeviceInput?
    
    var dataOutput: AVCaptureVideoDataOutput?
    var photoOutput: AVCapturePhotoOutput?
    
    var rearCamera: AVCaptureDevice?
    var rearCameraInput: AVCaptureDeviceInput?
    
    var previewLayer: AVCaptureVideoPreviewLayer?
    
    var flashMode = AVCaptureDevice.FlashMode.off
    var photoCaptureCompletionBlock: ((UIImage?, Error?) -> Void)?
    var sampleBufferCaptureCompletionBlock: ((UIImage?, Error?) -> Void)?
    
    var highResolutionOutput: Bool = false
    
    var audioDevice: AVCaptureDevice?
    var audioInput: AVCaptureDeviceInput?
    
    var orientation:UIInterfaceOrientation = UIInterfaceOrientation.portrait
    var isOpenedFromPortraitMode:Bool = UIDevice.current.orientation.isPortrait
    
    var motionManager: CMMotionManager!
    var zoomFactor: CGFloat = 1.0
}

extension CameraController {
    func prepare(cameraPosition: String, completionHandler: @escaping (Error?) -> Void) {
        func createCaptureSession() {
            self.captureSession = AVCaptureSession()
            self.captureSession?.sessionPreset = .photo
            self.isOpenedFromPortraitMode = UIDevice.current.orientation.isPortrait
        }
        
        func configureCaptureDevices() throws {
            
            let session = AVCaptureDevice.DiscoverySession(deviceTypes: [.builtInWideAngleCamera], mediaType: AVMediaType.video, position: .unspecified)
            
            let cameras = session.devices.compactMap { $0 }
            guard !cameras.isEmpty else { throw CameraControllerError.noCamerasAvailable }
            
            for camera in cameras {
                if camera.position == .front {
                    self.frontCamera = camera
                }
                
                if camera.position == .back {
                    self.rearCamera = camera
                    
                    try camera.lockForConfiguration()
                    camera.focusMode = .continuousAutoFocus
                    camera.unlockForConfiguration()
                }
            }
            self.audioDevice = AVCaptureDevice.default(for: AVMediaType.audio)
        }
        
        func configureDeviceInputs() throws {
            guard let captureSession = self.captureSession else { throw CameraControllerError.captureSessionIsMissing }
            
            if cameraPosition == "rear" {
                if let rearCamera = self.rearCamera {
                    self.rearCameraInput = try AVCaptureDeviceInput(device: rearCamera)
                    
                    if captureSession.canAddInput(self.rearCameraInput!) { captureSession.addInput(self.rearCameraInput!) }
                    
                    self.currentCameraPosition = .rear
                }
            } else if cameraPosition == "front" {
                if let frontCamera = self.frontCamera {
                    self.frontCameraInput = try AVCaptureDeviceInput(device: frontCamera)
                    
                    if captureSession.canAddInput(self.frontCameraInput!) { captureSession.addInput(self.frontCameraInput!) }
                    else { throw CameraControllerError.inputsAreInvalid }
                    
                    self.currentCameraPosition = .front
                }
            } else { throw CameraControllerError.noCamerasAvailable }
            
            // Add audio input
            if let audioDevice = self.audioDevice {
                self.audioInput = try AVCaptureDeviceInput(device: audioDevice)
                if captureSession.canAddInput(self.audioInput!) {
                    captureSession.addInput(self.audioInput!)
                } else {
                    throw CameraControllerError.inputsAreInvalid
                }
            }
        }
        
        func configurePhotoOutput() throws {
            guard let captureSession = self.captureSession else { throw CameraControllerError.captureSessionIsMissing }
            
            self.photoOutput = AVCapturePhotoOutput()
            self.photoOutput!.setPreparedPhotoSettingsArray([AVCapturePhotoSettings(format: [AVVideoCodecKey : AVVideoCodecType.jpeg])], completionHandler: nil)
            self.photoOutput?.isHighResolutionCaptureEnabled = self.highResolutionOutput
            if captureSession.canAddOutput(self.photoOutput!) { captureSession.addOutput(self.photoOutput!) }
            captureSession.startRunning()
        }
        
        func configureDataOutput() throws {
            guard let captureSession = self.captureSession else { throw CameraControllerError.captureSessionIsMissing }
            
            self.dataOutput = AVCaptureVideoDataOutput()
            self.dataOutput?.videoSettings = [
                (kCVPixelBufferPixelFormatTypeKey as String): NSNumber(value: kCVPixelFormatType_32BGRA as UInt32)
            ]
            self.dataOutput?.alwaysDiscardsLateVideoFrames = true
            if captureSession.canAddOutput(self.dataOutput!) {
                captureSession.addOutput(self.dataOutput!)
            }
            
            captureSession.commitConfiguration()
            
            let queue = DispatchQueue(label: "DataOutput", attributes: [])
            self.dataOutput?.setSampleBufferDelegate(self, queue: queue)
        }
        
        func detectOrientationByAccelerometer() throws {
            let splitAngle:Double = 0.75
            let updateTimer:TimeInterval = 0.5
            
            motionManager = CMMotionManager()
            motionManager?.gyroUpdateInterval = updateTimer
            motionManager?.accelerometerUpdateInterval = updateTimer
            
            var orientationLast    = UIInterfaceOrientation(rawValue: 0)!
            
            if motionManager.isAccelerometerAvailable {
                motionManager?.startAccelerometerUpdates(to: OperationQueue.current ?? OperationQueue.main, withHandler: {
                    (acceleroMeterData, error) -> Void in
                    if error == nil {
                        let acceleration = (acceleroMeterData?.acceleration)!
                        var orientationNew = UIInterfaceOrientation(rawValue: 0)!
                        
                        if acceleration.x >= splitAngle {
                            orientationNew = .landscapeLeft
                        }
                        else if acceleration.x <= -(splitAngle) {
                            orientationNew = .landscapeRight
                        }
                        else if acceleration.y <= -(splitAngle) {
                            orientationNew = .portrait
                        }
                        else if acceleration.y >= splitAngle {
                            orientationNew = .portraitUpsideDown
                        }
                        
                        if orientationNew != orientationLast && orientationNew != .unknown{
                            orientationLast = orientationNew
                            deviceOrientationChanged(orientation: orientationNew)
                        }
                    }
                    else {
                        print("error : \(error!)")
                    }
                })
            }
            else{
                throw CameraControllerError.noAccelerometerAvailable
            }
        }
        
        func deviceOrientationChanged(orientation:UIInterfaceOrientation) {
            self.orientation = orientation;
        }
        
        DispatchQueue(label: "prepare").async {
            do {
                createCaptureSession()
                try configureCaptureDevices()
                try configureDeviceInputs()
                try configurePhotoOutput()
                try configureDataOutput()
//                try configureVideoOutput()
                try detectOrientationByAccelerometer()
            }
            
            catch {
                DispatchQueue.main.async {
                    completionHandler(error)
                }
                
                return
            }
            
            DispatchQueue.main.async {
                self.updateVideoOrientation()
                
                completionHandler(nil)
            }
        }
    }
    
    func displayPreview(on view: UIView) throws {
        guard let captureSession = self.captureSession, captureSession.isRunning else { throw CameraControllerError.captureSessionIsMissing }
        
        self.previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        self.previewLayer?.videoGravity = AVLayerVideoGravity.resizeAspectFill
        
        let orientation: UIDeviceOrientation = UIDevice.current.orientation
        let videoOrientation: AVCaptureVideoOrientation
        
        switch (orientation) {
        case .portrait:
            videoOrientation = .portrait
        case .landscapeRight:
            videoOrientation = .landscapeLeft
        case .landscapeLeft:
            videoOrientation = .landscapeRight
        case .portraitUpsideDown:
            videoOrientation = .portraitUpsideDown
        case .faceUp, .faceDown, .unknown:
            fallthrough
        @unknown default:
            switch UIApplication.shared.statusBarOrientation {
            case .portrait:
                videoOrientation = .portrait
            case .landscapeRight:
                videoOrientation = .landscapeRight
            case .landscapeLeft:
                videoOrientation = .landscapeLeft
            case .portraitUpsideDown:
                videoOrientation = .portraitUpsideDown
            case .unknown:
                fallthrough
            @unknown default:
                videoOrientation = .portrait
            }
        }
        self.previewLayer?.connection?.videoOrientation = videoOrientation
        
        let pinch = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
        view.addGestureRecognizer(pinch)
        
        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        view.addGestureRecognizer(tap)
        
        view.layer.insertSublayer(self.previewLayer!, at: 0)
        self.previewLayer?.frame = view.frame
    }
    
    func updateVideoOrientation() {
        assert(Thread.isMainThread) // UIApplication.statusBarOrientation requires the main thread.
        let videoOrientation: AVCaptureVideoOrientation
        switch UIDevice.current.orientation {
        case .portrait:
            videoOrientation = .portrait
        case .landscapeLeft:
            videoOrientation = .landscapeRight
        case .landscapeRight:
            videoOrientation = .landscapeLeft
        case .portraitUpsideDown:
            videoOrientation = .portraitUpsideDown
        case .faceUp, .faceDown, .unknown:
            fallthrough
        @unknown default:
            switch UIApplication.shared.statusBarOrientation {
            case .portrait:
                videoOrientation = .portrait
            case .landscapeLeft:
                videoOrientation = .landscapeLeft
            case .landscapeRight:
                videoOrientation = .landscapeRight
            case .portraitUpsideDown:
                videoOrientation = .portraitUpsideDown
            case .unknown:
                fallthrough
            @unknown default:
                videoOrientation = .portrait
            }
        }
        
        previewLayer?.connection?.videoOrientation = videoOrientation
        dataOutput?.connections.forEach { $0.videoOrientation = videoOrientation }
    }
    
    func switchCameras() throws {
        guard let currentCameraPosition = currentCameraPosition, let captureSession = self.captureSession, captureSession.isRunning else { throw CameraControllerError.captureSessionIsMissing }
        
        captureSession.beginConfiguration()
        
        func switchToFrontCamera() throws {
            
            guard let rearCameraInput = self.rearCameraInput, captureSession.inputs.contains(rearCameraInput),
                  let frontCamera = self.frontCamera else { throw CameraControllerError.invalidOperation }
            
            self.frontCameraInput = try AVCaptureDeviceInput(device: frontCamera)
            
            captureSession.removeInput(rearCameraInput)
            
            if captureSession.canAddInput(self.frontCameraInput!) {
                captureSession.addInput(self.frontCameraInput!)
                
                self.currentCameraPosition = .front
            }
            
            else {
                throw CameraControllerError.invalidOperation
            }
        }
        
        func switchToRearCamera() throws {
            
            guard let frontCameraInput = self.frontCameraInput, captureSession.inputs.contains(frontCameraInput),
                  let rearCamera = self.rearCamera else { throw CameraControllerError.invalidOperation }
            
            self.rearCameraInput = try AVCaptureDeviceInput(device: rearCamera)
            
            captureSession.removeInput(frontCameraInput)
            
            if captureSession.canAddInput(self.rearCameraInput!) {
                captureSession.addInput(self.rearCameraInput!)
                
                self.currentCameraPosition = .rear
            }
            
            else { throw CameraControllerError.invalidOperation }
        }
        
        switch currentCameraPosition {
        case .front:
            try switchToRearCamera()
            
        case .rear:
            try switchToFrontCamera()
        }
        
        captureSession.commitConfiguration()
    }
    
    func captureImage(completion: @escaping (UIImage?, Error?) -> Void) {
        guard let captureSession = captureSession, captureSession.isRunning else { completion(nil, CameraControllerError.captureSessionIsMissing); return }
        let settings = AVCapturePhotoSettings()
        
        settings.flashMode = self.flashMode
        settings.isHighResolutionPhotoEnabled = self.highResolutionOutput;
        let videoOrientation: AVCaptureVideoOrientation
        
        if self.orientation == .portrait {
            videoOrientation = AVCaptureVideoOrientation.portrait
        }else if (self.orientation == .landscapeLeft){
            videoOrientation = AVCaptureVideoOrientation.landscapeLeft
        }else if (self.orientation == .landscapeRight){
            videoOrientation = AVCaptureVideoOrientation.landscapeRight
        }else if (self.orientation == .portraitUpsideDown){
            videoOrientation = AVCaptureVideoOrientation.portraitUpsideDown
        }else {
            videoOrientation = AVCaptureVideoOrientation.portrait
        }
        
        self.photoOutput?.connection(with: AVMediaType.video)?.videoOrientation = videoOrientation
        self.photoOutput?.capturePhoto(with: settings, delegate: self)
        self.photoCaptureCompletionBlock = completion
    }
    
    func captureSample(completion: @escaping (UIImage?, Error?) -> Void) {
        guard let captureSession = captureSession,
              captureSession.isRunning else {
            completion(nil, CameraControllerError.captureSessionIsMissing)
            return
        }
        
        self.sampleBufferCaptureCompletionBlock = completion
    }
    
    func getSupportedFlashModes() throws -> [String] {
        var currentCamera: AVCaptureDevice?
        switch currentCameraPosition {
        case .front:
            currentCamera = self.frontCamera!;
        case .rear:
            currentCamera = self.rearCamera!;
        default: break;
        }
        
        guard
            let device = currentCamera
        else {
            throw CameraControllerError.noCamerasAvailable
        }
        
        var supportedFlashModesAsStrings: [String] = []
        if device.hasFlash {
            guard let supportedFlashModes: [AVCaptureDevice.FlashMode] = self.photoOutput?.supportedFlashModes else {
                throw CameraControllerError.noCamerasAvailable
            }
            
            for flashMode in supportedFlashModes {
                var flashModeValue: String?
                switch flashMode {
                case AVCaptureDevice.FlashMode.off:
                    flashModeValue = "off"
                case AVCaptureDevice.FlashMode.on:
                    flashModeValue = "on"
                case AVCaptureDevice.FlashMode.auto:
                    flashModeValue = "auto"
                default: break;
                }
                if flashModeValue != nil {
                    supportedFlashModesAsStrings.append(flashModeValue!)
                }
            }
        }
        if device.hasTorch {
            supportedFlashModesAsStrings.append("torch")
        }
        return supportedFlashModesAsStrings
        
    }
    
    func setFlashMode(flashMode: AVCaptureDevice.FlashMode) throws {
        var currentCamera: AVCaptureDevice?
        switch currentCameraPosition {
        case .front:
            currentCamera = self.frontCamera!;
        case .rear:
            currentCamera = self.rearCamera!;
        default: break;
        }
        
        guard let device = currentCamera else {
            throw CameraControllerError.noCamerasAvailable
        }
        
        guard let supportedFlashModes: [AVCaptureDevice.FlashMode] = self.photoOutput?.supportedFlashModes else {
            throw CameraControllerError.invalidOperation
        }
        if supportedFlashModes.contains(flashMode) {
            do {
                try device.lockForConfiguration()
                
                if(device.hasTorch && device.isTorchAvailable && device.torchMode == AVCaptureDevice.TorchMode.on) {
                    device.torchMode = AVCaptureDevice.TorchMode.off
                }
                self.flashMode = flashMode
                let photoSettings = AVCapturePhotoSettings()
                photoSettings.flashMode = flashMode
                self.photoOutput?.photoSettingsForSceneMonitoring = photoSettings
                
                device.unlockForConfiguration()
            } catch {
                throw CameraControllerError.invalidOperation
            }
        } else {
            throw CameraControllerError.invalidOperation
        }
    }
    
    func setTorchMode() throws {
        var currentCamera: AVCaptureDevice?
        switch currentCameraPosition {
        case .front:
            currentCamera = self.frontCamera!;
        case .rear:
            currentCamera = self.rearCamera!;
        default: break;
        }
        
        guard
            let device = currentCamera,
            device.hasTorch,
            device.isTorchAvailable
        else {
            throw CameraControllerError.invalidOperation
        }
        
        do {
            try device.lockForConfiguration()
            if (device.isTorchModeSupported(AVCaptureDevice.TorchMode.on)) {
                device.torchMode = AVCaptureDevice.TorchMode.on
            } else if (device.isTorchModeSupported(AVCaptureDevice.TorchMode.auto)) {
                device.torchMode = AVCaptureDevice.TorchMode.auto
            } else {
                device.torchMode = AVCaptureDevice.TorchMode.off
            }
            device.unlockForConfiguration()
        } catch {
            throw CameraControllerError.invalidOperation
        }
        
    }
    
    func captureVideo(completion: @escaping (URL?, Error?) -> Void) {
        guard let captureSession = self.captureSession, captureSession.isRunning else {
            completion(nil, CameraControllerError.captureSessionIsMissing)
            return
        }
        let path = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let identifier = UUID()
        let randomIdentifier = identifier.uuidString.replacingOccurrences(of: "-", with: "")
        let finalIdentifier = String(randomIdentifier.prefix(8))
        let fileName="cpcp_video_"+finalIdentifier+".mp4"
        
        let fileUrl = path.appendingPathComponent(fileName)
        try? FileManager.default.removeItem(at: fileUrl)
//        videoOutput!.startRecording(to: fileUrl, recordingDelegate: self)
//        self.videoRecordCompletionBlock = completion
    }
    
    func stopRecording(completion: @escaping (Error?) -> Void) {
        guard let captureSession = self.captureSession, captureSession.isRunning else {
            completion(CameraControllerError.captureSessionIsMissing)
            return
        }
//        self.videoOutput?.stopRecording()
    }
    
    @objc
    private func handlePinch(_ pinch: UIPinchGestureRecognizer) {
        guard let device = self.currentCameraPosition == .rear ? rearCamera : frontCamera else { return }
        
        func minMaxZoom(_ factor: CGFloat) -> CGFloat { return min(max(factor, 1.0), device.activeFormat.videoMaxZoomFactor) }
        
        func update(scale factor: CGFloat) {
            do {
                try device.lockForConfiguration()
                defer { device.unlockForConfiguration() }
                device.videoZoomFactor = factor
            } catch {
                debugPrint(error)
            }
        }
        
        let newScaleFactor = minMaxZoom(pinch.scale * zoomFactor)
        
        switch pinch.state {
        case .began: fallthrough
        case .changed: update(scale: newScaleFactor)
        case .ended:
            zoomFactor = minMaxZoom(newScaleFactor)
            update(scale: zoomFactor)
        default: break
        }
    }
    
    @objc
    private func handleTap(_ tap: UITapGestureRecognizer) {
        guard let device = self.currentCameraPosition == .rear ? rearCamera : frontCamera else { return }
        
        let point = tap.location(in: tap.view)
        let devicePoint = self.previewLayer?.captureDevicePointConverted(fromLayerPoint: point)
        
        do {
            try device.lockForConfiguration()
            defer { device.unlockForConfiguration() }
            let focusMode = AVCaptureDevice.FocusMode.autoFocus
            if device.isFocusPointOfInterestSupported && device.isFocusModeSupported(focusMode) {
                device.focusPointOfInterest = CGPoint(x: CGFloat(devicePoint?.x ?? 0), y: CGFloat(devicePoint?.y ?? 0))
                device.focusMode = focusMode
            }
            
            let exposureMode = AVCaptureDevice.ExposureMode.autoExpose
            if device.isExposurePointOfInterestSupported && device.isExposureModeSupported(exposureMode) {
                device.exposurePointOfInterest = CGPoint(x: CGFloat(devicePoint?.x ?? 0), y: CGFloat(devicePoint?.y ?? 0))
                device.exposureMode = exposureMode
            }
        } catch {
            debugPrint(error)
        }
    }
}

extension CameraController: AVCapturePhotoCaptureDelegate {
    public func photoOutput(_ captureOutput: AVCapturePhotoOutput, didFinishProcessingPhoto photoSampleBuffer: CMSampleBuffer?, previewPhoto previewPhotoSampleBuffer: CMSampleBuffer?,
                            resolvedSettings: AVCaptureResolvedPhotoSettings, bracketSettings: AVCaptureBracketedStillImageSettings?, error: Swift.Error?) {
        if let error = error { self.photoCaptureCompletionBlock?(nil, error) }
        
        else if let buffer = photoSampleBuffer, let data = AVCapturePhotoOutput.jpegPhotoDataRepresentation(forJPEGSampleBuffer: buffer, previewPhotoSampleBuffer: nil),
                let image = UIImage(data: data) {
            self.photoCaptureCompletionBlock?(image.reformat(), nil)
        }
        
        else {
            self.photoCaptureCompletionBlock?(nil, CameraControllerError.unknown)
        }
    }
}

extension CameraController: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let completion = sampleBufferCaptureCompletionBlock else { return }
        
        guard let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            completion(nil, CameraControllerError.unknown)
            return
        }
        
        CVPixelBufferLockBaseAddress(imageBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(imageBuffer, .readOnly) }
        
        let baseAddress = CVPixelBufferGetBaseAddress(imageBuffer)
        let bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer)
        let width = CVPixelBufferGetWidth(imageBuffer)
        let height = CVPixelBufferGetHeight(imageBuffer)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo: UInt32 = CGBitmapInfo.byteOrder32Little.rawValue |
            CGImageAlphaInfo.premultipliedFirst.rawValue
        
        let context = CGContext(
            data: baseAddress,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: colorSpace,
            bitmapInfo: bitmapInfo
        )
        
        guard let cgImage = context?.makeImage() else {
            completion(nil, CameraControllerError.unknown)
            return
        }
        
        let image = UIImage(cgImage: cgImage)
//        completion(image.fixedOrientation(), nil)
        
        sampleBufferCaptureCompletionBlock = nil
    }
}


enum CameraControllerError: Swift.Error {
    case captureSessionAlreadyRunning
    case captureSessionIsMissing
    case inputsAreInvalid
    case invalidOperation
    case noCamerasAvailable
    case noAccelerometerAvailable
    case unknown
}

public enum CameraPosition {
    case front
    case rear
}

extension CameraControllerError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case .captureSessionAlreadyRunning:
            return NSLocalizedString("Capture Session is Already Running", comment: "Capture Session Already Running")
        case .captureSessionIsMissing:
            return NSLocalizedString("Capture Session is Missing", comment: "Capture Session Missing")
        case .inputsAreInvalid:
            return NSLocalizedString("Inputs Are Invalid", comment: "Inputs Are Invalid")
        case .invalidOperation:
            return NSLocalizedString("Invalid Operation", comment: "invalid Operation")
        case .noCamerasAvailable:
            return NSLocalizedString("Failed to access device camera(s)", comment: "No Cameras Available")
        case .unknown:
            return NSLocalizedString("Unknown", comment: "Unknown")
        case .noAccelerometerAvailable:
            return NSLocalizedString("No accelerometer available", comment: "No accelerometer available")
        }
    }
}

extension UIImage {
    /**
     Generates a new image from the existing one, implicitly resetting any orientation.
     Dimensions greater than 0 will resize the image while preserving the aspect ratio.
     */
    func reformat(to size: CGSize? = nil) -> UIImage {
        let imageHeight = self.size.height
        let imageWidth = self.size.width
        
        // determine the max dimensions, 0 is treated as 'no restriction'
        var maxWidth: CGFloat
        if let size = size, size.width > 0 {
            maxWidth = size.width
        } else {
            maxWidth = imageWidth
        }
        let maxHeight: CGFloat
        if let size = size, size.height > 0 {
            maxHeight = size.height
        } else {
            maxHeight = imageHeight
        }
        // adjust to preserve aspect ratio
        var targetWidth = min(imageWidth, maxWidth)
        var targetHeight = (imageHeight * maxWidth) / imageWidth
        if targetHeight > maxHeight {
            targetWidth = (imageWidth * maxHeight) / imageHeight
            targetHeight = maxHeight
        }
        // generate the new image and return
        let format: UIGraphicsImageRendererFormat = UIGraphicsImageRendererFormat.default()
        format.scale = 1.0
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: targetWidth, height: targetHeight), format: format)
        return renderer.image { (_) in
            self.draw(in: CGRect(origin: .zero, size: CGSize(width: targetWidth, height: targetHeight)))
        }
    }
}

extension CameraController: AVCaptureFileOutputRecordingDelegate {
    func fileOutput(_ output: AVCaptureFileOutput, didFinishRecordingTo outputFileURL: URL, from connections: [AVCaptureConnection], error: Error?) {
//        if error == nil {
//            self.videoRecordCompletionBlock?(outputFileURL, nil)
//        } else {
//            self.videoRecordCompletionBlock?(nil, error)
//        }
    }
}
