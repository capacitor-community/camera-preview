package com.ahm.capacitor.camera.preview;

import android.graphics.Rect;
import android.hardware.Camera;

import com.getcapacitor.Logger;
import java.util.ArrayList;
import java.util.List;

public class MyFaceDetectionListener implements Camera.FaceDetectionListener {

    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {

        if (faces.length == 0) {
            Logger.info(this.getClass().getSimpleName(), "No faces detected");
        } else if (faces.length > 0) {
            Logger.info(this.getClass().getSimpleName(), "Faces Detected = " +
                    String.valueOf(faces.length));

            List<Rect> faceRects;
            faceRects = new ArrayList<Rect>();

            for (int i=0; i<faces.length; i++) {
                int left = faces[i].rect.left;
                int right = faces[i].rect.right;
                int top = faces[i].rect.top;
                int bottom = faces[i].rect.bottom;
                Rect uRect = new Rect(left, top, right, bottom);
                faceRects.add(uRect);
            }

            // add function to draw rects on view/surface/canvas
        }
    }
}
