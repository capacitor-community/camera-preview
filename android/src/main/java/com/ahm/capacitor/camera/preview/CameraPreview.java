package com.ahm.capacitor.camera.preview;

import android.hardware.Camera;
import android.support.v4.app.ActivityCompat;
import android.widget.FrameLayout;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

@NativePlugin()
public class CameraPreview extends Plugin {

    private int containerViewId = 20;

    @PluginMethod()
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", value);
        call.success(ret);
    }

    @PluginMethod()
    public void start(PluginCall call) {
        String value = call.getString("value");

        bridge.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                ActivityCompat.requestDragAndDropPermissions(getActivity(), null);

                FrameLayout containerView = bridge.getActivity().findViewById(containerViewId);
                if(containerView == null){
                    containerView = new FrameLayout(bridge.getActivity().getApplicationContext());
                    containerView.setId(containerViewId);

                    FrameLayout.LayoutParams containerLayoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                    bridge.getActivity().addContentView(containerView, containerLayoutParams);
                }

                Camera cameraInstance = getCameraInstance();
                Preview preview = new Preview(getContext(), cameraInstance);
                containerView.addView(preview);

                getBridge().getWebView().setBackgroundColor(0x00000000);
                getBridge().getWebView().getParent();
                getBridge().getWebView().bringToFront();
            }
        });


//        webView.getView().setBackgroundColor(0x00000000);
//        webViewParent = webView.getView().getParent();
//        ((ViewGroup)webView.getView()).bringToFront();

        System.out.println("asdasdasdasdasd");
        JSObject ret = new JSObject();
        ret.put("value", value);
        call.success(ret);
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }
}
