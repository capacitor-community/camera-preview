// Controller that make use of AVFoundation to detect faces in a video stream
// and send the results to the Cordova plugin.
//
// Created by Juan Pablo Civile on 09/01/2019.
//
import AVFoundation

class FaceRecognitionController: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let captureSession = AVCaptureSession()
    private let videoDataOutput = AVCaptureVideoDataOutput()
    private let motionThreshold: CGFloat = 0.1
    private var previousFacePosition: CGPoint?
    
    override init() {
        super.init()
        
        guard let videoDevice = AVCaptureDevice.default(for: .video),
              let videoInput = try? AVCaptureDeviceInput(device: videoDevice) else {
            return
        }
        
        if captureSession.canAddInput(videoInput) {
            captureSession.addInput(videoInput)
        }
        
        videoDataOutput.setSampleBufferDelegate(self, queue: DispatchQueue.global(qos: .userInteractive))
        
        if captureSession.canAddOutput(videoDataOutput) {
            captureSession.addOutput(videoDataOutput)
        }
    }
    
    func startCapture() {
        captureSession.startRunning()
    }
    
    func stopCapture() {
        captureSession.stopRunning()
    }
    
    // MARK: - AVCaptureVideoDataOutputSampleBufferDelegate
    
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            return
        }
        
        let facePosition = detectFacePosition(in: pixelBuffer)
        
        if let previousPosition = previousFacePosition {
            let motion = calculateMotion(from: previousPosition, to: facePosition)
            
            if motion < motionThreshold {
                // Smooth shaking detected
                // Do something here
                
            }
        }
        
        previousFacePosition = facePosition
    }
    
    private func detectFacePosition(in pixelBuffer: CVPixelBuffer) -> CGPoint? {
        // Perform face detection using Vision framework or any other face detection library
        // Return the position of the detected face
        var facePosition: CGPoint? = nil

        // Create a request handler.
        let requestHandler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .up, options: [:])

        // Create a new request to recognize faces.
        let request = VNDetectFaceRectanglesRequest { (request, error) in
            if let error = error {
                print("Failed to detect faces: \(error)")
                return
            }

            guard let results = request.results as? [VNFaceObservation] else {
                print("The result is not a sequence of VNFaceObservation")
                return
            }

            for face in results {
                let box = face.boundingBox
                let x = box.origin.x + box.size.width / 2.0
                let y = box.origin.y + box.size.height / 2.0
                facePosition = CGPoint(x: x, y: y)
            }
        }

        // Perform the face detection request.
        do {
            try requestHandler.perform([request])
        } catch {
            print("Failed to perform detection: \(error)")
        }

        return facePosition
    }
    
    private func calculateMotion(from startPoint: CGPoint, to endPoint: CGPoint) -> CGFloat {
        let deltaX = endPoint.x - startPoint.x
        let deltaY = endPoint.y - startPoint.y
        return sqrt(deltaX * deltaX + deltaY * deltaY)
    }
}
