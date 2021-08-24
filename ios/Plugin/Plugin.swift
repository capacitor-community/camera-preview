import Foundation
import Capacitor
import AVFoundation
/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
@objc(CameraPreview)
public class CameraPreview: CAPPlugin {
    
    var previewView:UIView!
    var cameraPosition = String()
    let cameraController = CameraController()
    var x: CGFloat?
    var y: CGFloat?
    var width: CGFloat?
    var height: CGFloat?
    var paddingBottom: CGFloat?
    var rotateWhenOrientationChanged: Bool?
    var toBack: Bool?
    var storeToFile: Bool?
    var highResolutionOutput: Bool = false
    
    @objc func rotated() {
        let state = UIApplication.shared.applicationState
        if(self.previewView == nil || self.cameraController.captureSession?.isRunning == false || state == .background || state == .inactive
            || UIDevice.current.orientation == UIDeviceOrientation.portraitUpsideDown){
            return;
        }
        
        self.updateFrameAndOrientation()
    }
    
    func updateFrameAndOrientation() {
        let height = self.paddingBottom != nil ? self.height! - self.paddingBottom!: self.height!;
        let videoOrientation: AVCaptureVideoOrientation
        
        if UIDevice.current.orientation.isLandscape {
            if(self.cameraController.isOpenedFromPortraitMode)
            {
                self.previewView.frame = CGRect(x: self.y!, y: self.x!, width: height, height: self.width!)
            }
            else{
                self.previewView.frame = CGRect(x: self.x!, y: self.y!, width: self.width!, height: height)
            }
            
            if (UIDevice.current.orientation == UIDeviceOrientation.landscapeLeft) {
                videoOrientation = .landscapeRight
            }
            else {
                videoOrientation = .landscapeLeft
            }
        }
        else {
            if(self.cameraController.isOpenedFromPortraitMode)
            {
                self.previewView.frame = CGRect(x: self.x!, y: self.y!, width: self.width!, height: height)
            }
            else{
                self.previewView.frame = CGRect(x: self.y!, y: self.x!, width: height, height: self.width!)
            }
            
            videoOrientation = .portrait
        }
        
        self.cameraController.previewLayer?.frame = self.previewView.frame
        self.cameraController.previewLayer?.connection?.videoOrientation = videoOrientation
    }
    
    @objc func start(_ call: CAPPluginCall) {
        self.cameraPosition = call.getString("position") ?? "rear"
        self.highResolutionOutput = call.getBool("enableHighResolution") ?? false
        self.cameraController.highResolutionOutput = self.highResolutionOutput;
        
        if call.getInt("width") != nil {
            self.width = CGFloat(call.getInt("width")!)
        } else {
            self.width = UIScreen.main.bounds.size.width
        }
        if call.getInt("height") != nil {
            self.height = CGFloat(call.getInt("height")!)
        } else {
            self.height = UIScreen.main.bounds.size.height
        }
        self.x = call.getInt("x") != nil ? CGFloat(call.getInt("x")!)/UIScreen.main.scale: 0
        self.y = call.getInt("y") != nil ? CGFloat(call.getInt("y")!)/UIScreen.main.scale: 0
        if call.getInt("paddingBottom") != nil {
            self.paddingBottom = CGFloat(call.getInt("paddingBottom")!)
        }
        
        AVCaptureDevice.requestAccess(for: .video, completionHandler: { (granted: Bool) in
            if (!granted) {
                call.reject("permission failed");
            }
        });
        
        self.rotateWhenOrientationChanged = call.getBool("rotateWhenOrientationChanged") ?? true
        self.toBack = call.getBool("toBack") ?? false
        self.storeToFile = call.getBool("storeToFile") ?? false
        
        if (self.rotateWhenOrientationChanged == true) {
            NotificationCenter.default.addObserver(self, selector: #selector(CameraPreview.rotated), name: UIDevice.orientationDidChangeNotification, object: nil)
        }
        
        DispatchQueue.main.async {
            if (self.cameraController.captureSession?.isRunning ?? false) {
                call.reject("camera already started")
            } else {
                self.cameraController.prepare(cameraPosition: self.cameraPosition){error in
                    if let error = error {
                        print(error)
                        call.reject(error.localizedDescription)
                        return
                    }
                    self.previewView = UIView(frame: CGRect(x: self.x!, y: self.y!, width: self.width!, height: self.height!))
                    self.cameraController.isOpenedFromPortraitMode = self.height! > self.width!
                    
                    self.webView?.isOpaque = false
                    self.webView?.backgroundColor = UIColor.clear
                    self.webView!.scrollView.backgroundColor = UIColor.clear
                    self.webView?.superview?.addSubview(self.previewView)
                    if (self.toBack!) {
                        self.webView?.superview?.bringSubviewToFront(self.webView!)
                    }
                    try? self.cameraController.displayPreview(on: self.previewView)
                    call.resolve()
                    
                }
            }
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
    
    @objc func stop(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            if (self.cameraController.captureSession?.isRunning ?? false) {
                self.cameraController.captureSession?.stopRunning()
                self.previewView.removeFromSuperview()
                self.webView?.isOpaque = true
                call.resolve()
            } else {
                call.reject("camera already stopped")
            }
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
            
            let quality: Int = call.getInt("quality", 85)
            let thumbnailWidth: CGFloat = (CGFloat)(call.getInt("thumbnailWidth", 0))
            let imageQuality:CGFloat =  min(abs(CGFloat(quality)) / 100.0, 1.0);
            
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
                if (self.cameraPosition == "front") {
                    let flippedImage = image.withHorizontallyFlippedOrientation()
                    imageData = flippedImage.jpegData(compressionQuality: imageQuality)
                    
                } else {
                    imageData = image.jpegData(compressionQuality: imageQuality)
                }
                
                var thumbnailImageData: Data?
                if(thumbnailWidth > 0){
                    let thumbnailImage = image.reformat(to: CGSize(width: thumbnailWidth, height: 0));
                    
                    if (self.cameraPosition == "front") {
                        let flippedThumbnailImage = thumbnailImage.withHorizontallyFlippedOrientation()
                        thumbnailImageData = flippedThumbnailImage.jpegData(compressionQuality: imageQuality)
                    } else {
                        thumbnailImageData = thumbnailImage.jpegData(compressionQuality: imageQuality)
                    }
                }
                
                if (self.storeToFile == false){
                    let imageBase64 = imageData?.base64EncodedString()
                    
                    if(thumbnailImageData != nil){
                        let thumbnailImageBase64 = thumbnailImageData?.base64EncodedString()
                        
                        call.resolve(["image": imageBase64!, "thumbnailImage":thumbnailImageBase64!])
                    }else{
                        call.resolve(["image": imageBase64!])
                    }
                }else{
                    do{
                        let imageUrl=self.getTempFilePath()
                        try imageData?.write(to:imageUrl)
                        
                        if(thumbnailImageData != nil){
                            let thumbnailUrl=self.getTempFilePath()
                            try thumbnailImageData?.write(to:thumbnailUrl)
                            
                            call.resolve(["image": imageUrl.absoluteString, "thumbnailImage":thumbnailUrl.absoluteString])
                        }
                        else{
                            call.resolve(["image": imageUrl.absoluteString])
                        }
                    }catch{
                        call.reject("error writing image to file")
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
            case "off" :
                flashModeAsEnum = AVCaptureDevice.FlashMode.off
            case "on":                                                                                                                                                                                                                                                 
                flashModeAsEnum = AVCaptureDevice.FlashMode.on
            case "auto":
                flashModeAsEnum = AVCaptureDevice.FlashMode.auto
            default: break;
            }
            if flashModeAsEnum != nil {
                try self.cameraController.setFlashMode(flashMode: flashModeAsEnum!)
            } else if(flashMode == "torch") {
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
