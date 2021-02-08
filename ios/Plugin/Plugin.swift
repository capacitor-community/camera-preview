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

        let height = self.paddingBottom != nil ? self.height! - self.paddingBottom!: self.height!;

        if UIDevice.current.orientation.isLandscape {

            self.previewView.frame = CGRect(x: self.y!, y: self.x!, width: height, height: self.width!)
            self.cameraController.previewLayer?.frame = self.previewView.frame

            if (UIDevice.current.orientation == UIDeviceOrientation.landscapeLeft) {
                self.cameraController.previewLayer?.connection?.videoOrientation = .landscapeRight
            }

            if (UIDevice.current.orientation == UIDeviceOrientation.landscapeRight) {
                self.cameraController.previewLayer?.connection?.videoOrientation = .landscapeLeft
            }
        }

        if UIDevice.current.orientation.isPortrait {
            self.previewView.frame = CGRect(x: self.x!, y: self.y!, width: self.width!, height: self.height!)
            self.cameraController.previewLayer?.frame = self.previewView.frame
            self.cameraController.previewLayer?.connection?.videoOrientation = .portrait
        }
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
                    self.webView.isOpaque = false
                    self.webView.backgroundColor = UIColor.clear
                    self.webView.scrollView.backgroundColor = UIColor.clear
                    self.webView.superview?.addSubview(self.previewView)
                    if (self.toBack!) {
                        self.webView.superview?.bringSubviewToFront(self.webView)
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
                self.webView.isOpaque = true
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
            if (self.cameraPosition == "front") {
                let flippedImage = image.withHorizontallyFlippedOrientation()
                imageData = flippedImage.jpegData(compressionQuality: CGFloat(quality!/100))
            } else {
                imageData = image.jpegData(compressionQuality: CGFloat(quality!/100))
            }

            if (self.storeToFile == false){
                let imageBase64 = imageData?.base64EncodedString()
                call.resolve(["value": imageBase64!])
            }else{
                do{
                    let fileUrl=self.getTempFilePath()
                    try imageData?.write(to:fileUrl)
                    call.resolve(["value":fileUrl.absoluteString])
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
