#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(CameraPreview, "CameraPreview",
           CAP_PLUGIN_METHOD(start, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(stop, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(capture, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(captureSample, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(flip, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getSupportedFlashModes, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(setFlashMode, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(startRecordVideo, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(stopRecordVideo, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(isCameraStarted, CAPPluginReturnPromise);
)
