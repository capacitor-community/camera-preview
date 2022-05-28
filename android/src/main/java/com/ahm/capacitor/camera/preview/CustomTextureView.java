package com.ahm.capacitor.camera.preview;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.SurfaceHolder;
import android.view.TextureView;

class CustomTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    private final String TAG = "CustomTextureView";

    CustomTextureView(Context context) {
        super(context);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {}

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
}
