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
    
    @objc func rotated() {
        self.previewView.frame = CGRect(x: 0, y: 0, width: UIScreen.main.bounds.size.width, height: UIScreen.main.bounds.size.height)
        self.cameraController.previewLayer?.frame = self.previewView.frame
        
        if UIDevice.current.orientation.isLandscape {
            
            if (UIDevice.current.orientation == UIDeviceOrientation.landscapeLeft) {
                self.cameraController.previewLayer?.connection?.videoOrientation = .landscapeRight
            }
            
            if (UIDevice.current.orientation == UIDeviceOrientation.landscapeRight) {
                self.cameraController.previewLayer?.connection?.videoOrientation = .landscapeLeft
            }
        }

        if UIDevice.current.orientation.isPortrait {
            self.cameraController.previewLayer?.connection?.videoOrientation = .portrait
        }
    }

    @objc func start(_ call: CAPPluginCall) {
        self.cameraPosition = call.getString("position") ?? "rear"
        NotificationCenter.default.addObserver(self, selector: #selector(CameraPreview.rotated), name: UIDevice.orientationDidChangeNotification, object: nil)

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
                    self.previewView = UIView(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.size.width, height: UIScreen.main.bounds.size.height))
                    self.webView.isOpaque = false
                    self.webView.backgroundColor = UIColor.clear
                    self.webView.superview?.addSubview(self.previewView)
                    self.webView.superview?.bringSubviewToFront(self.webView)
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
            self.cameraController.captureSession?.stopRunning()
            self.previewView.removeFromSuperview()
            self.webView.isOpaque = true
            call.resolve()
        }
    }

    @objc func capture(_ call: CAPPluginCall) {
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

            let imageData = image.jpegData(compressionQuality: 90)
            let imageBase64 = imageData?.base64EncodedString()
            call.resolve(["value": imageBase64!])
        }
    }
    
}
