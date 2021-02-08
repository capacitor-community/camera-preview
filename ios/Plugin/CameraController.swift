//
//  CameraController.swift
//  Plugin
//
//  Created by Ariel Hernandez Musa on 7/14/19.
//  Copyright © 2019 Max Lynch. All rights reserved.
//

import AVFoundation
import UIKit

class CameraController: NSObject {
    var captureSession: AVCaptureSession?
    
    var currentCameraPosition: CameraPosition?
    
    var frontCamera: AVCaptureDevice?
    var frontCameraInput: AVCaptureDeviceInput?
    
    var photoOutput: AVCapturePhotoOutput?
    
    var rearCamera: AVCaptureDevice?
    var rearCameraInput: AVCaptureDeviceInput?
    
    var previewLayer: AVCaptureVideoPreviewLayer?
    
    var flashMode = AVCaptureDevice.FlashMode.off
    var photoCaptureCompletionBlock: ((UIImage?, Error?) -> Void)?

    var highResolutionOutput: Bool = false
}

extension CameraController {
    func prepare(cameraPosition: String, completionHandler: @escaping (Error?) -> Void) {
        func createCaptureSession() {
            self.captureSession = AVCaptureSession()
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
        }

        func configurePhotoOutput() throws {
            guard let captureSession = self.captureSession else { throw CameraControllerError.captureSessionIsMissing }

            self.photoOutput = AVCapturePhotoOutput()
            self.photoOutput!.setPreparedPhotoSettingsArray([AVCapturePhotoSettings(format: [AVVideoCodecKey : AVVideoCodecType.jpeg])], completionHandler: nil)
            self.photoOutput?.isHighResolutionCaptureEnabled = self.highResolutionOutput
            if captureSession.canAddOutput(self.photoOutput!) { captureSession.addOutput(self.photoOutput!) }
            captureSession.startRunning()
        }

        DispatchQueue(label: "prepare").async {
            do {
                createCaptureSession()
                try configureCaptureDevices()
                try configureDeviceInputs()
                try configurePhotoOutput()
            }

            catch {
                DispatchQueue.main.async {
                    completionHandler(error)
                }

                return
            }

            DispatchQueue.main.async {
                completionHandler(nil)
            }
        }
    }

    func displayPreview(on view: UIView) throws {
        guard let captureSession = self.captureSession, captureSession.isRunning else { throw CameraControllerError.captureSessionIsMissing }

        self.previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        self.previewLayer?.videoGravity = AVLayerVideoGravity.resizeAspectFill

        let orientation: UIDeviceOrientation = UIDevice.current.orientation
        let statusBarOrientation = UIApplication.shared.statusBarOrientation
        switch (orientation) {
        case .portrait:
            self.previewLayer?.connection?.videoOrientation = .portrait
        case .landscapeRight:
           self.previewLayer?.connection?.videoOrientation = .landscapeLeft
        case .landscapeLeft:
            self.previewLayer?.connection?.videoOrientation = .landscapeRight
        case .portraitUpsideDown:
            self.previewLayer?.connection?.videoOrientation = .portraitUpsideDown
        case .faceUp, .faceDown:
            switch (statusBarOrientation) {
            case .portrait:
                self.previewLayer?.connection?.videoOrientation = .portrait
            case .landscapeRight:
               self.previewLayer?.connection?.videoOrientation = .landscapeRight
            case .landscapeLeft:
                self.previewLayer?.connection?.videoOrientation = .landscapeLeft
            case .portraitUpsideDown:
                self.previewLayer?.connection?.videoOrientation = .portraitUpsideDown
            default:
                self.previewLayer?.connection?.videoOrientation = .portrait
            }
        default:
            self.previewLayer?.connection?.videoOrientation = .portrait
        }

        view.layer.insertSublayer(self.previewLayer!, at: 0)
        self.previewLayer?.frame = view.frame
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

        let currentDevice: UIDevice = .current
        let deviceOrientation: UIDeviceOrientation = currentDevice.orientation
        let statusBarOrientation = UIApplication.shared.statusBarOrientation
        if deviceOrientation == .portrait {
           self.photoOutput?.connection(with: AVMediaType.video)?.videoOrientation = AVCaptureVideoOrientation.portrait
        }else if (deviceOrientation == .landscapeLeft){
           self.photoOutput?.connection(with: AVMediaType.video)?.videoOrientation = AVCaptureVideoOrientation.landscapeRight
        }else if (deviceOrientation == .landscapeRight){
           self.photoOutput?.connection(with: AVMediaType.video)?.videoOrientation = AVCaptureVideoOrientation.landscapeLeft
        }else if (deviceOrientation == .portraitUpsideDown){
           self.photoOutput?.connection(with: AVMediaType.video)?.videoOrientation = AVCaptureVideoOrientation.portraitUpsideDown
        }else if (deviceOrientation == .faceUp || deviceOrientation == .faceDown){
            switch (statusBarOrientation) {
            case .portrait:
                self.photoOutput?.connection(with: AVMediaType.video)?.videoOrientation = AVCaptureVideoOrientation.portrait
            case .landscapeRight:
                self.photoOutput?.connection(with: AVMediaType.video)?.videoOrientation = AVCaptureVideoOrientation.landscapeRight
            case .landscapeLeft:
                self.photoOutput?.connection(with: AVMediaType.video)?.videoOrientation = AVCaptureVideoOrientation.landscapeLeft
            case .portraitUpsideDown:
                self.photoOutput?.connection(with: AVMediaType.video)?.videoOrientation = AVCaptureVideoOrientation.portraitUpsideDown
            default:
                self.photoOutput?.connection(with: AVMediaType.video)?.videoOrientation = AVCaptureVideoOrientation.portrait
            }
        }else {
           self.photoOutput?.connection(with: AVMediaType.video)?.videoOrientation = AVCaptureVideoOrientation.portrait
        }
        self.photoOutput?.capturePhoto(with: settings, delegate: self)
        self.photoCaptureCompletionBlock = completion
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
}

extension CameraController: AVCapturePhotoCaptureDelegate {
    public func photoOutput(_ captureOutput: AVCapturePhotoOutput, didFinishProcessingPhoto photoSampleBuffer: CMSampleBuffer?, previewPhoto previewPhotoSampleBuffer: CMSampleBuffer?,
                            resolvedSettings: AVCaptureResolvedPhotoSettings, bracketSettings: AVCaptureBracketedStillImageSettings?, error: Swift.Error?) {
        if let error = error { self.photoCaptureCompletionBlock?(nil, error) }
            
        else if let buffer = photoSampleBuffer, let data = AVCapturePhotoOutput.jpegPhotoDataRepresentation(forJPEGSampleBuffer: buffer, previewPhotoSampleBuffer: nil),
            let image = UIImage(data: data) {
            self.photoCaptureCompletionBlock?(image.fixedOrientation(), nil)
        }
            
        else {
            self.photoCaptureCompletionBlock?(nil, CameraControllerError.unknown)
        }
    }
}




enum CameraControllerError: Swift.Error {
    case captureSessionAlreadyRunning
    case captureSessionIsMissing
    case inputsAreInvalid
    case invalidOperation
    case noCamerasAvailable
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

        }
    }
}

extension UIImage {

    func fixedOrientation() -> UIImage? {
        
        guard imageOrientation != UIImage.Orientation.up else {
            //This is default orientation, don't need to do anything
            return self.copy() as? UIImage
        }
        
        guard let cgImage = self.cgImage else {
            //CGImage is not available
            return nil
        }

        guard let colorSpace = cgImage.colorSpace, let ctx = CGContext(data: nil, width: Int(size.width), height: Int(size.height), bitsPerComponent: cgImage.bitsPerComponent, bytesPerRow: 0, space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            return nil //Not able to create CGContext
        }
        
        var transform: CGAffineTransform = CGAffineTransform.identity
        switch imageOrientation {
        case .down, .downMirrored:
            transform = transform.translatedBy(x: size.width, y: size.height)
            transform = transform.rotated(by: CGFloat.pi)
            print("down")
            break
        case .left, .leftMirrored:
            transform = transform.translatedBy(x: size.width, y: 0)
            transform = transform.rotated(by: CGFloat.pi / 2.0)
            print("left")
            break
        case .right, .rightMirrored:
            transform = transform.translatedBy(x: 0, y: size.height)
            transform = transform.rotated(by: CGFloat.pi / -2.0)
            print("right")
            break
        case .up, .upMirrored:
            break
        }
        
        //Flip image one more time if needed to, this is to prevent flipped image
        switch imageOrientation {
        case .upMirrored, .downMirrored:
            transform.translatedBy(x: size.width, y: 0)
            transform.scaledBy(x: -1, y: 1)
            break
        case .leftMirrored, .rightMirrored:
            transform.translatedBy(x: size.height, y: 0)
            transform.scaledBy(x: -1, y: 1)
        case .up, .down, .left, .right:
            break
        }
        
        ctx.concatenate(transform)
        
        switch imageOrientation {
        case .left, .leftMirrored, .right, .rightMirrored:
            ctx.draw(self.cgImage!, in: CGRect(x: 0, y: 0, width: size.height, height: size.width))
        default:
            ctx.draw(self.cgImage!, in: CGRect(x: 0, y: 0, width: size.width, height: size.height))
            break
        }
        guard let newCGImage = ctx.makeImage() else { return nil }
        return UIImage.init(cgImage: newCGImage, scale: 1, orientation: .up)
    }
}
