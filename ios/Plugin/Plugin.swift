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
    let cameraController = CameraController()

    @objc func start(_ call: CAPPluginCall) {
        //        let value = call.getString("value") ?? ""
        DispatchQueue.main.async {
            if (self.cameraController.captureSession?.isRunning ?? false) {
                call.reject("camera already started")
            } else {
                self.cameraController.prepare{error in
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
                    call.success()

                }
            }
        }
    }

    @objc func stop(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            self.cameraController.captureSession?.stopRunning()
            self.previewView.removeFromSuperview()
            self.webView.isOpaque = true
            call.success()
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

            call.success(["value": imageBase64!])
        }
    }
}
