import Foundation
import Capacitor
import AVFoundation
import UIKit

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
@objc(CameraPreview)
public class CameraPreview: CAPPlugin, CAPBridgedPlugin {

    public let identifier = "CameraPreviewPlugin"
    public let jsName = "CameraPreview"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "start", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "capture", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "captureSample", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "flip", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getSupportedFlashModes", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setFlashMode", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startRecordVideo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopRecordVideo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isCameraStarted", returnType: CAPPluginReturnPromise)
    ]

    var previewView: UIView!
    var cameraPosition = String()
    let cameraController = CameraController()

    // swiftlint:disable identifier_name
    var x: CGFloat?
    var y: CGFloat?
    // swiftlint:enable identifier_name
    var width: CGFloat?
    var height: CGFloat?
    var paddingBottom: CGFloat?
    var rotateWhenOrientationChanged: Bool?
    var toBack: Bool?
    var storeToFile: Bool?
    var enableZoom: Bool?
    var highResolutionOutput: Bool = false
    var disableAudio: Bool = false

    // Helper to replace UIApplication.shared.statusBarOrientation (Deprecated in iOS 13+)
    // Updated to use connectedScenes for iOS 15+ compliance
    private func getInterfaceOrientation() -> UIInterfaceOrientation {
        if #available(iOS 13.0, *) {
            return UIApplication.shared.connectedScenes
                .first(where: { $0 is UIWindowScene })
                .flatMap({ $0 as? UIWindowScene })?.interfaceOrientation ?? .unknown
        } else {
            return UIApplication.shared.statusBarOrientation
        }
    }

    @objc func rotated() {
        guard let previewView = self.previewView,
              let x = self.x,
              let y = self.y,
              let width = self.width,
              let height = self.height else {
            return
        }

        let adjustedHeight = self.paddingBottom != nil ? height - self.paddingBottom! : height
        let orientation = getInterfaceOrientation()

        if orientation.isLandscape {
            previewView.frame = CGRect(x: y, y: x, width: max(adjustedHeight, width), height: min(adjustedHeight, width))
            self.cameraController.previewLayer?.frame = previewView.frame
        }

        if orientation.isPortrait {
            previewView.frame = CGRect(x: x, y: y, width: min(adjustedHeight, width), height: max(adjustedHeight, width))
            self.cameraController.previewLayer?.frame = previewView.frame
        }

        cameraController.updateVideoOrientation()
    }

    // FIX: Use by default explicit to avoid compilation errors
    @objc func start(_ call: CAPPluginCall) {

        self.cameraPosition = call.getString("position", "rear")
        self.highResolutionOutput = call.getBool("enableHighResolution", false)
        self.cameraController.highResolutionOutput = self.highResolutionOutput

        // FIX: Direct access to options for optional values (getInt requires default now)
        if let w = call.options["width"] as? Int {
            self.width = CGFloat(w)
        } else {
            self.width = UIScreen.main.bounds.size.width
        }

        if let h = call.options["height"] as? Int {
            self.height = CGFloat(h)
        } else {
            self.height = UIScreen.main.bounds.size.height
        }

        let xVal = call.options["x"] as? Int ?? 0
        self.x = CGFloat(xVal) / UIScreen.main.scale

        let yVal = call.options["y"] as? Int ?? 0
        self.y = CGFloat(yVal) / UIScreen.main.scale

        if let pb = call.options["paddingBottom"] as? Int {
            self.paddingBottom = CGFloat(pb)
        }

        self.rotateWhenOrientationChanged = call.getBool("rotateWhenOrientationChanged", true)
        self.toBack = call.getBool("toBack", false)
        self.storeToFile = call.getBool("storeToFile", false)
        self.enableZoom = call.getBool("enableZoom", false)
        self.disableAudio = call.getBool("disableAudio", false)

        AVCaptureDevice.requestAccess(for: .video, completionHandler: { (granted: Bool) in
            guard granted else {
                // SPM WORKAROUND: Reject not available, so we use resolve with error info
                call.resolve([
                    "success": false,
                    "error": "permission failed"
                ])
                return
            }

            DispatchQueue.main.async {
                if self.cameraController.captureSession?.isRunning ?? false {
                    call.resolve([
                        "success": false,
                        "error": "camera already started"
                    ])
                } else {
                    self.cameraController.prepare(cameraPosition: self.cameraPosition, disableAudio: self.disableAudio) { error in
                        if let error = error {
                            print(error)
                            call.resolve([
                                "success": false,
                                "error": error.localizedDescription
                            ])
                            return
                        }

                        guard let height = self.height, let width = self.width else {
                            call.resolve([
                                "success": false,
                                "error": "Invalid dimensions"
                            ])
                            return
                        }

                        let adjustedHeight = self.paddingBottom != nil ? height - self.paddingBottom! : height
                        self.previewView = UIView(frame: CGRect(x: self.x ?? 0, y: self.y ?? 0, width: width, height: adjustedHeight))
                        self.webView?.isOpaque = false
                        self.webView?.backgroundColor = UIColor.clear
                        self.webView?.scrollView.backgroundColor = UIColor.clear
                        self.webView?.superview?.addSubview(self.previewView)

                        if let toBack = self.toBack, toBack {
                            self.webView?.superview?.bringSubviewToFront(self.webView!)
                        }

                        try? self.cameraController.displayPreview(on: self.previewView)

                        let frontView = (self.toBack ?? false) ? self.webView : self.previewView
                        self.cameraController.setupGestures(target: frontView ?? self.previewView, enableZoom: self.enableZoom ?? false)

                        if self.rotateWhenOrientationChanged == true {
                            NotificationCenter.default.addObserver(self, selector: #selector(CameraPreview.rotated), name: UIDevice.orientationDidChangeNotification, object: nil)
                        }

                        call.resolve()
                    }
                }
            }
        })
    }

    @objc func flip(_ call: CAPPluginCall) {
        do {
            try self.cameraController.switchCameras()
            call.resolve()
        } catch {
            call.resolve([
                "success": false,
                "error": "failed to flip camera"
            ])
        }
    }

    @objc func stop(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            if self.cameraController.captureSession?.isRunning ?? false {
                self.cameraController.captureSession?.stopRunning()

                // Remove the orientation observer to prevent crashes
                if self.rotateWhenOrientationChanged == true {
                    NotificationCenter.default.removeObserver(self, name: UIDevice.orientationDidChangeNotification, object: nil)
                }

                if let previewView = self.previewView {
                    previewView.removeFromSuperview()
                    self.previewView = nil
                }
                self.webView?.isOpaque = true
                call.resolve()
            } else {
                call.resolve([
                    "success": false,
                    "error": "camera already stopped"
                ])
            }
        }
    }

    // Get user's cache directory path
    func getTempFilePath() -> URL {
        let path = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let identifier = UUID()
        let randomIdentifier = identifier.uuidString.replacingOccurrences(of: "-", with: "")
        let finalIdentifier = String(randomIdentifier.prefix(8))
        let fileName = "cpcp_capture_" + finalIdentifier + ".jpg"
        return path.appendingPathComponent(fileName)
    }

    @objc func capture(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let quality = call.getInt("quality", 85)

            self.cameraController.captureImage { (image, error) in
                guard let image = image else {
                    print(error ?? "Image capture error")
                    let errorMessage = error?.localizedDescription ?? "Image capture error"
                    call.resolve([
                        "success": false,
                        "error": errorMessage
                    ])
                    return
                }

                let imageData: Data?
                // Fix: Calculate correct floating point for quality
                let compression = CGFloat(quality) / 100.0

                if self.cameraController.currentCameraPosition == .front {
                    let flippedImage = image.withHorizontallyFlippedOrientation()
                    imageData = flippedImage.jpegData(compressionQuality: compression)
                } else {
                    imageData = image.jpegData(compressionQuality: compression)
                }

                if self.storeToFile == false {
                    let imageBase64 = imageData?.base64EncodedString() ?? ""
                    call.resolve(["value": imageBase64])
                } else {
                    do {
                        let fileUrl = self.getTempFilePath()
                        try imageData?.write(to: fileUrl)
                        call.resolve(["value": fileUrl.absoluteString])
                    } catch {
                        call.resolve([
                            "success": false,
                            "error": "error writing image to file"
                        ])
                    }
                }
            }
        }
    }

    @objc func captureSample(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let quality = call.getInt("quality", 85)

            self.cameraController.captureSample { image, error in
                guard let image = image else {
                    let msg = error?.localizedDescription ?? "Image capture error"
                    print("Image capture error: \(msg)")
                    call.resolve([
                        "success": false,
                        "error": msg
                    ])
                    return
                }

                let imageData: Data?
                let compression = CGFloat(quality) / 100.0

                if self.cameraPosition == "front" {
                    let flippedImage = image.withHorizontallyFlippedOrientation()
                    imageData = flippedImage.jpegData(compressionQuality: compression)
                } else {
                    imageData = image.jpegData(compressionQuality: compression)
                }

                if self.storeToFile == false {
                    let imageBase64 = imageData?.base64EncodedString() ?? ""
                    call.resolve(["value": imageBase64])
                } else {
                    do {
                        let fileUrl = self.getTempFilePath()
                        try imageData?.write(to: fileUrl)
                        call.resolve(["value": fileUrl.absoluteString])
                    } catch {
                        call.resolve([
                            "success": false,
                            "error": "Error writing image to file"
                        ])
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
            call.resolve([
                "success": false,
                "error": "failed to get supported flash modes"
            ])
        }
    }

    @objc func setFlashMode(_ call: CAPPluginCall) {
        // FIX V8: manual check on options because getString(key) without default doesn't exist or is risky
        guard let flashMode = call.options["flashMode"] as? String else {
            call.resolve([
                "success": false,
                "error": "failed to set flash mode. required parameter flashMode is missing"
            ])
            return
        }

        do {
            var flashModeAsEnum: AVCaptureDevice.FlashMode?
            switch flashMode {
            case "off":
                flashModeAsEnum = .off
            case "on":
                flashModeAsEnum = .on
            case "auto":
                flashModeAsEnum = .auto
            default: break
            }

            if let mode = flashModeAsEnum {
                try self.cameraController.setFlashMode(flashMode: mode)
            } else if flashMode == "torch" {
                try self.cameraController.setTorchMode()
            } else {
                call.resolve([
                    "success": false,
                    "error": "Flash Mode not supported"
                ])
                return
            }
            call.resolve()
        } catch {
            call.resolve([
                "success": false,
                "error": "failed to set flash mode"
            ])
        }
    }

    @objc func startRecordVideo(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            // Note: quality is used in the block, but we keep it for compatibility
            _ = call.getInt("quality", 85)

            self.cameraController.captureVideo { (url, error) in
                guard let url = url else {
                    print(error ?? "Video capture error")
                    call.resolve([
                        "success": false,
                        "error": error?.localizedDescription ?? "Video capture error"
                    ])
                    return
                }

                call.resolve(["value": url.absoluteString])
            }
        }
    }

    @objc func stopRecordVideo(_ call: CAPPluginCall) {
        self.cameraController.stopRecording { (_) in
            call.resolve()
        }
    }

    @objc func isCameraStarted(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            if self.cameraController.captureSession?.isRunning ?? false {
                call.resolve(["value": true])
            } else {
                call.resolve(["value": false])
            }
        }
    }
}
