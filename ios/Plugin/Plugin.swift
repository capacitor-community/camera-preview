import Foundation
import Capacitor
import AVFoundation
/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
@objc(CameraPreview)
public class CameraPreview: CAPPlugin {
    let cameraController = CameraController()
    var cameraPosition: CameraPosition = .rear
    var x: CGFloat = 0.0
    var y: CGFloat = 0.0
    var previewView: UIView!
    var previewWidth: CGFloat = UIScreen.main.bounds.size.width
    var previewHeight: CGFloat = UIScreen.main.bounds.size.height
    var paddingBottom: CGFloat = 0
    var rotateWhenOrientationChanged = true
    var toBack = false
    var storeToFile = false
    var enableZoom = false
    var enableHighResolution = false
    
    /**
     Start the camera preview in a new UIView
     */
    @objc public func start(_ call: CAPPluginCall) {
        AVCaptureDevice.requestAccess(for: AVMediaType.video) { [weak self] granted in
            guard granted else {
                call.reject("camera access not granted")
                return
            }
            
            // Initialize settings provided via API call
            self?.initializePluginSettings(call: call)
            
            if let captureSession = self?.cameraController.captureSession, captureSession.isRunning {
                call.reject("camera already started")
                return
            }
            
            self?.cameraController.prepare(cameraPosition: self?.cameraPosition, enableHighResolution: self?.enableHighResolution ?? false) { error in
                if let error = error {
                    call.reject(error.localizedDescription)
                    return
                }
                
                DispatchQueue.main.async {
                    self?.displayCameraPreviewView()
                    call.resolve()
                }
            }
        }
    }
    
    /**
     Stops any currently running capture session
     */
    @objc public func stop(_ call: CAPPluginCall) {
        guard self.cameraController.captureSession?.isRunning ?? false else {
            call.reject("camera already stopped")
            return;
        }
        
        DispatchQueue.main.async {
            self.cameraController.stop()
            self.previewView.removeFromSuperview()
            self.webView?.isOpaque = true
            call.resolve()
        }
    }
    
    /**
     Capture a photo with the currently active capture device
     */
    @objc func capture(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let quality: Int = call.getInt("quality", 85)

            self.cameraController.captureImage { (image, error) in
                guard let image = image else {
                    print(error ?? "Image capture error")
                    guard let error = error else {
                        call.reject("Image capture error")
                        return
                    }
                    
                    call.reject(error.localizedDescription)
                    return
                }
                
                let imageData = image.jpegData(compressionQuality: CGFloat(quality / 100))

                if self.storeToFile == false {
                    let imageBase64 = imageData?.base64EncodedString()
                    call.resolve(["value": imageBase64!])
                } else {
                    do {
                        let fileUrl = self.getTempFilePath()
                        try imageData?.write(to: fileUrl)
                        call.resolve(["value": fileUrl.absoluteString])
                    } catch {
                        call.reject("error writing image to file")
                    }
                }
            }
        }
    }
    
    @objc public func flip(_ call: CAPPluginCall) {
        do {
            try self.cameraController.switchCameras()
            call.resolve()
        } catch {
            call.reject("failed to flip camera")
        }
    }
    
    /**
     Captures a sample image from the video stream.
     */
    @objc func captureSample(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let quality: Int = call.getInt("quality", 85)
            
            self.cameraController.captureSample { image, error in
                guard let image = image else {
                    print("Image capture error: \(String(describing: error))")
                    call.reject("Image capture error: \(String(describing: error))")
                    return
                }
                
                let imageData: Data?
                if self.cameraPosition == .front {
                    let flippedImage = image.withHorizontallyFlippedOrientation()
                    imageData = flippedImage.jpegData(compressionQuality: CGFloat(quality / 100))
                } else {
                    imageData = image.jpegData(compressionQuality: CGFloat(quality / 100))
                }
                
                if self.storeToFile == false {
                    let imageBase64 = imageData?.base64EncodedString()
                    call.resolve(["value": imageBase64!])
                } else {
                    do {
                        let fileUrl = self.getTempFilePath()
                        try imageData?.write(to: fileUrl)
                        call.resolve(["value": fileUrl.absoluteString])
                    } catch {
                        call.reject("Error writing image to file")
                    }
                }
            }
        }
    }
    
    /**
     Return an array of supported flash modes of the currently active capture device
     */
    @objc func getSupportedFlashModes(_ call: CAPPluginCall) {
        do {
            let supportedFlashModes = try self.cameraController.getSupportedFlashModes()
            call.resolve(["result": supportedFlashModes])
        } catch {
            call.reject("failed to get supported flash modes")
        }
    }
    
    /**
     Set the flash mode for the currently active capture device
     */
    @objc func setFlashMode(_ call: CAPPluginCall) {
        guard let flashMode = call.getString("flashMode") else {
            call.reject("failed to set flash mode. required parameter flashMode is missing")
            return
        }
        
        var flashModeAsEnum: AVCaptureDevice.FlashMode?
        switch flashMode {
        case "off":
            flashModeAsEnum = AVCaptureDevice.FlashMode.off
        case "on":
            flashModeAsEnum = AVCaptureDevice.FlashMode.on
        case "auto":
            flashModeAsEnum = AVCaptureDevice.FlashMode.auto
        default: break
        }
        
        do {
            if flashModeAsEnum != nil {
                try self.cameraController.setFlashMode(flashMode: flashModeAsEnum!)
            } else if flashMode == "torch" {
                try self.cameraController.setTorchMode()
            } else {
                call.reject("Flash Mode not supported")
                return
            }
            
            call.resolve()
        } catch {
            call.reject("failed to set flash mode")
        }
    }
    
    /**
     Helper method for initializing the plugin settings based on the Capacitor call
     */
    private func initializePluginSettings(call: CAPPluginCall) {
        self.cameraPosition = call.getString("position") == "front" ? .front : .rear
        
        if let previewWidth = call.getInt("width") {
            self.previewWidth = CGFloat(previewWidth)
        } else {
            self.previewWidth = UIScreen.main.bounds.size.width
        }
        
        if let previewHeight = call.getInt("height") {
            self.previewHeight = CGFloat(previewHeight)
        } else {
            self.previewHeight = UIScreen.main.bounds.size.height
        }
        
        self.x = CGFloat(call.getInt("x", 0)) / 2
        self.y = CGFloat(call.getInt("y", 0)) / 2
        
        self.paddingBottom = CGFloat(call.getInt("paddingBottom", 0))
        
        self.rotateWhenOrientationChanged = call.getBool("rotateWhenOrientationChanged") ?? true
        
        self.toBack = call.getBool("toBack") ?? false

        self.storeToFile = call.getBool("storeToFile") ?? false

        self.enableZoom = call.getBool("enableZoom") ?? false
        
        self.enableHighResolution = call.getBool("enableHighResolution", false)
    }

    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        let cameraState: String

        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .notDetermined:
            cameraState = "prompt"
        case .restricted, .denied:
            cameraState = "denied"
        case .authorized:
            cameraState = "granted"
        @unknown default:
            cameraState = "prompt"
        }

        call.resolve(["camera": cameraState])
    }
    
    @objc public override func requestPermissions(_ call: CAPPluginCall) {
        AVCaptureDevice.requestAccess(for: .video) { [weak self] _ in
            self?.checkPermissions(call)
        }
    }

    /**
     Displays the camera preview view in the UI
     */
    private func displayCameraPreviewView() {
        self.previewView = UIView(frame: CGRect(x: self.x, y: self.y, width: self.previewWidth, height: self.previewHeight - self.paddingBottom))
        self.webView?.isOpaque = false
        self.webView?.backgroundColor = UIColor.clear
        self.webView?.scrollView.backgroundColor = UIColor.clear
        self.webView?.superview?.addSubview(self.previewView)
        
        if self.toBack {
            self.webView?.superview?.bringSubviewToFront(self.webView!)
        }
        
        if self.rotateWhenOrientationChanged {
            NotificationCenter.default.addObserver(self, selector: #selector(CameraPreview.rotated), name: UIDevice.orientationDidChangeNotification, object: nil)
        }
        
        self.cameraController.displayPreview(on: self.previewView)
        
        let frontView = self.toBack ? self.webView : self.previewView
        self.cameraController.setupGestures(target: frontView ?? self.previewView, enableZoom: self.enableZoom)
    }
    
    /**
     Handler funciton for updating the previewLayer frame based on plugin settings and the current device orientation
     */
    @objc private func rotated() {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
            return
        }
        
        let interfaceOrientation = windowScene.interfaceOrientation
        let height = self.previewHeight - self.paddingBottom
        
        if interfaceOrientation.isLandscape {
            previewView.frame = CGRect(x: self.y, y: self.x, width: max(height, self.previewWidth), height: min(height, self.previewWidth))
            cameraController.previewLayer.frame = previewView.frame
        } else if interfaceOrientation.isPortrait {
            previewView.frame = CGRect(x: self.x, y: self.y, width: min(height, self.previewWidth), height: max(height, self.previewWidth))
            cameraController.previewLayer.frame = previewView.frame
        }
        
        cameraController.updateVideoOrientation()
    }
    
    /**
     Get user's cache directory path
     */
    private func getTempFilePath() -> URL {
        let path = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let randomIdentifier =  UUID().uuidString.replacingOccurrences(of: "-", with: "")
        let finalIdentifier = String(randomIdentifier.prefix(8))
        let fileName="cpcp_capture_"+finalIdentifier+".jpg"
        let fileUrl=path.appendingPathComponent(fileName)
        
        return fileUrl
    }
}
