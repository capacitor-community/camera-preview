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
    var previewView: UIView!
    var cameraPosition: CameraPosition = .rear
    var x: CGFloat = 0.0
    var y: CGFloat = 0.0
    var width: CGFloat = UIScreen.main.bounds.size.width
    var height: CGFloat = UIScreen.main.bounds.size.height
    var paddingBottom: CGFloat = 0
    var rotateWhenOrientationChanged: Bool?
    var toBack: Bool = false
    var storeToFile: Bool?
    var enableZoom: Bool?
    var zoomFactor: CGFloat = 1.0
    
    private func hasCameraPermission() -> Bool {
        let status = AVCaptureDevice.authorizationStatus(for: AVMediaType.video)
        if (status == AVAuthorizationStatus.authorized) {
            return true
        }
        return false
    }
    
    @objc func rotated() {
        let height = self.height - self.paddingBottom
        
        if UIApplication.shared.statusBarOrientation.isLandscape {
            self.previewView.frame = CGRect(x: self.y, y: self.x, width: max(height, self.width), height: min(height, self.width))
            self.cameraController.previewLayer?.frame = self.previewView.frame
        }
        
        if UIApplication.shared.statusBarOrientation.isPortrait {
            if self.previewView != nil && self.x != nil && self.y != nil && self.width != nil && self.height != nil {
                self.previewView.frame = CGRect(x: self.x, y: self.y, width: min(height, self.width), height: max(height, self.width))
            }
            self.cameraController.previewLayer?.frame = self.previewView.frame
        }
        
        cameraController.updateVideoOrientation()
    }
    
    @objc func start(_ call: CAPPluginCall) {
        if (call.getString("position") == "front") {
            self.cameraPosition = .front
        }
        
        if let width = call.getInt("width") {
            self.width = CGFloat(width)
        }
        
        if let height = call.getInt("height") {
            self.height = CGFloat(height)
        }
        
        if let x = call.getInt("x") {
            self.x = CGFloat(x) / 2
        }
        
        if let y = call.getInt("y") {
            self.y = CGFloat(y) / 2
        }
        
        if let paddingBottom = call.getInt("paddingBottom") {
            self.paddingBottom = CGFloat(paddingBottom)
        }
        
        if let zoomFactor = call.getFloat("zoomFactor") {
            self.zoomFactor = CGFloat(zoomFactor)
        }
        
        self.rotateWhenOrientationChanged = call.getBool("rotateWhenOrientationChanged") ?? true
        self.toBack = call.getBool("toBack") ?? false
        self.storeToFile = call.getBool("storeToFile") ?? false
        self.enableZoom = call.getBool("enableZoom") ?? false
        
        
        guard self.cameraController.captureSession?.isRunning ?? true else {
            call.reject("camera already started")
            return
        }
        
        
        self.cameraController.prepareCamera(cameraPosition: self.cameraPosition, zoomFactor: self.zoomFactor) { error in
            if error != nil {
                call.reject(error!.localizedDescription)
                return
            }
            
            DispatchQueue.main.async {
                self.previewView = UIView(frame: CGRect(x: self.x, y: self.y, width: self.width, height: self.height - self.paddingBottom))
                self.webView?.isOpaque = false
                self.webView?.backgroundColor = UIColor.clear
                self.webView?.scrollView.backgroundColor = UIColor.clear
                self.webView?.superview?.addSubview(self.previewView)
                if self.toBack {
                    self.webView?.superview?.bringSubviewToFront(self.webView!)
                }
                
                
                self.cameraController.displayPreview(on: self.previewView)
                call.resolve()
            }
        }
    }
    
    @objc func stop(_ call: CAPPluginCall) {
        guard self.cameraController.captureSession?.isRunning ?? false else {
            call.reject("camera already stopped")
            return;
        }
        
        DispatchQueue.main.async {
            self.cameraController.captureSession?.stopRunning()
            self.previewView.removeFromSuperview()
            self.webView?.isOpaque = true
            call.resolve()
        }
    }
    
    @objc func flip(_ call: CAPPluginCall) {
        do {
            try self.cameraController.switchCameras()
            call.resolve()
        } catch {
            call.reject("failed to flip camera")
        }
    }
    
    // Get user's cache directory path
    @objc func getTempFilePath() -> URL {
        let path = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let identifier = UUID()
        let randomIdentifier = identifier.uuidString.replacingOccurrences(of: "-", with: "")
        let finalIdentifier = String(randomIdentifier.prefix(8))
        let fileName="cpcp_capture_"+finalIdentifier+".jpg"
        let fileUrl=path.appendingPathComponent(fileName)
        return fileUrl
    }
    
    @objc func capture(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let quality: Int? = call.getInt("quality", 85)
            
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
                let imageData: Data?
                if self.cameraController.currentCameraPosition == .front {
                    let flippedImage = image.withHorizontallyFlippedOrientation()
                    imageData = flippedImage.jpegData(compressionQuality: CGFloat(quality!/100))
                } else {
                    imageData = image.jpegData(compressionQuality: CGFloat(quality!/100))
                }
                
                if self.storeToFile == false {
                    let imageBase64 = imageData?.base64EncodedString()
                    call.resolve(["value": imageBase64!])
                } else {
                    do {
                        let fileUrl=self.getTempFilePath()
                        try imageData?.write(to: fileUrl)
                        call.resolve(["value": fileUrl.absoluteString])
                    } catch {
                        call.reject("error writing image to file")
                    }
                }
            }
        }
    }
    
    @objc func captureSample(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let quality: Int? = call.getInt("quality", 85)
            
            self.cameraController.captureSample { image, error in
                guard let image = image else {
                    print("Image capture error: \(String(describing: error))")
                    call.reject("Image capture error: \(String(describing: error))")
                    return
                }
                
                let imageData: Data?
                if self.cameraPosition == .front {
                    let flippedImage = image.withHorizontallyFlippedOrientation()
                    imageData = flippedImage.jpegData(compressionQuality: CGFloat(quality!/100))
                } else {
                    imageData = image.jpegData(compressionQuality: CGFloat(quality!/100))
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
    
    @objc func getSupportedFlashModes(_ call: CAPPluginCall) {
        do {
            let supportedFlashModes = try self.cameraController.getSupportedFlashModes()
            call.resolve(["result": supportedFlashModes])
        } catch {
            call.reject("failed to get supported flash modes")
        }
    }
    
    @objc func setFlashMode(_ call: CAPPluginCall) {
        guard let flashMode = call.getString("flashMode") else {
            call.reject("failed to set flash mode. required parameter flashMode is missing")
            return
        }
        do {
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
}
