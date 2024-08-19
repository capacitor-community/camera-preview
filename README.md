<h1 align="center">📷</h1>

<h3 align="center">Capacitor Camera Preview Lite</h3>
<p align="center"><strong><code>@michaelwolz/camera-preview-lite</code></strong></p>
<br>
<p align="center"><strong>CAPACITOR 6</strong></p><br>

<p align="center">
  Capacitor plugin that allows camera interaction from Javascript and HTML<br>(based on <a href="https://github.com/capacitor-community/camera-preview" target="_blank">@capacitor-community/camera-preview</a> which itself was based on cordova-plugin-camera-preview).

  This fork focuses on the camera functionality of the plugin by enabling high resolution photo output and better focus handling for new iPhone models.
</p>

<br>

Version 6 of this plugin requires Capacitor 6.

**PR's are greatly appreciated.**


# Installation

```
yarn add @michaelwolz/camera-preview-lite

or

npm install @michaelwolz/camera-preview-lite
```

Then run
```
npx cap sync
```

## Extra Android installation steps
**Important** `camera-preview` 3+ requires Gradle 7. If you are using Gradle 4, please use [version 2](https://github.com/capacitor-community/camera-preview/tree/v2.1.0) of this plugin.

Open `android/app/src/main/AndroidManifest.xml` and above the closing `</manifest>` tag add this line to request the CAMERA permission:
```xml
<uses-permission android:name="android.permission.CAMERA" />
```
For more help consult the [Capacitor docs](https://capacitorjs.com/docs/android/configuration#configuring-androidmanifestxml).

### Variables

This plugin will use the following project variables (defined in your app's `variables.gradle` file):

- `androidxExifInterfaceVersion`: version of `androidx.exifinterface:exifinterface` (default: `1.3.6`)

## Extra iOS installation steps

You will need to add two permissions to `Info.plist`. Follow the [Capacitor docs](https://capacitorjs.com/docs/ios/configuration#configuring-infoplist) and add permissions with the raw keys `NSCameraUsageDescription`.

## Extra Web installation steps

Add `import { CameraPreview } from '@capacitor-community/camera-preview';` in the file where you want to use the plugin.

then in html add `<div id="cameraPreview"></div>`

and `CameraPreview.start({ parent: "cameraPreview"});` will work.


# Methods

### start(options)

Starts the camera preview instance.
<br>

| Option                       | values       | descriptions                                                                                                                                                  |
|------------------------------|--------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| position                     | front \ rear | Show front or rear camera when start the preview. Defaults to front                                                                                           |
| width                        | number       | The preview width in pixels, default window.screen.width (applicable to the android and ios platforms only)                                                   |
| height                       | number       | The preview height in pixels, default window.screen.height  (applicable to the android and ios platforms only)                                                |
| x                            | number       | The x origin, default 0 (applicable to the android and ios platforms only)                                                                                    |
| y                            | number       | The y origin, default 0 (applicable to the android and ios platforms only)                                                                                    |
| toBack                       | boolean      | Brings your html in front of your preview, default false (applicable to the android and ios platforms only)                                                   |
| paddingBottom                | number       | The preview bottom padding in pixes. Useful to keep the appropriate preview sizes when orientation changes (applicable to the android and ios platforms only) |
| rotateWhenOrientationChanged | boolean      | Rotate preview when orientation changes (applicable to the ios platforms only; default value is true)                                                         |
| storeToFile                  | boolean      | Capture images to a file and return back the file path instead of returning base64 encoded data, default false.                                               |
| disableExifHeaderStripping   | boolean      | Disable automatic rotation of the image, and let the browser deal with it, default true (applicable to the android and ios platforms only)                    |
| enableHighResolution         | boolean      | Defaults to false - iOS only - Activate high resolution image capture so that output images are from the highest resolution on the device (photo quality)     |
| lockAndroidOrientation       | boolean      | Locks device orientation when camera is showing, default false. (applicable to Android only)                                                                  |
| enableOpacity                | boolean      | Make the camera preview see-through. Ideal for augmented reality uses. Default false (applicable to Android and web only)                                     |
| enableZoom                   | boolean      | Set if you can pinch to zoom. Default false (applicable to the android and ios platforms only)                                                                |

```javascript
import { CameraPreview, CameraPreviewOptions } from '@capacitor-community/camera-preview';

const cameraPreviewOptions: CameraPreviewOptions = {
  position: 'rear',
  height: 1920,
  width: 1080
};
CameraPreview.start(cameraPreviewOptions);
```

Remember to add the style below on your app's HTML or body element:

```css
ion-content {
  --background: transparent;
}
```

Take into account that this will make transparent all ion-content on application, if you want to show camera preview only in one page, just add a custom class to your ion-content and make it transparent:

```css
.my-custom-camera-preview-content {
  --background: transparent;
}
```

If the camera preview is not displaying after applying the above styles, apply transparent background color to the root div element of the parent component
Ex: VueJS >> App.vue component
```html
<template>
  <ion-app id="app">
    <ion-router-outlet />
  </ion-app>
</template>

<style>
#app {
  background-color: transparent !important;
}
<style>
```


### stop()

<info>Stops the camera preview instance.</info><br/>

```javascript
CameraPreview.stop();
```

### flip()
<info>Switch between rear and front camera only for android and ios, web is not supported</info>
```javascript
CameraPreview.flip()
```

### capture(options)

| Option               | values        | descriptions                                                                                    |
|----------------------|---------------|-------------------------------------------------------------------------------------------------|
| quality              | number        | (optional) The picture quality, 0 - 100, default 85                                             |
| width                | number        | (optional) The picture width, best fit respecting the aspect ratio of the device (Android only) |
| height               | number        | (optional) The picture height, best fit the aspect ratio of the device (Android only)           |

<info>Take the picture. If width and height are not specified or are 0 it will use the defaults. If width and height are specified, it will choose a supported photo size that is closest to width and height specified and has closest aspect ratio to the preview (only on Android). The argument `quality` defaults to `85` and specifies the quality/compression value: `0=max compression`, `100=max quality`.</info>

```javascript
import { CameraPreviewPictureOptions } from '@capacitor-community/camera-preview';

const cameraPreviewPictureOptions: CameraPreviewPictureOptions = {
  quality: 50
};

const result = await CameraPreview.capture(cameraPreviewPictureOptions);
const base64PictureData = result.value;
```

### captureSample(options)

| Option   | values        | descriptions                                                         |
|----------|---------------|----------------------------------------------------------------------|
| quality  | number        | (optional) The picture quality, 0 - 100, default 85                  |

<info>Captures a sample image from the video stream. Only for Android and iOS, web implementation falls back to `capture` method. This can be used to perform real-time analysis on the current frame in the video. The argument `quality` defaults to `85` and specifies the quality/compression value: `0=max compression`, `100=max quality`.</info><br/>

```javascript
import { CameraSampleOptions } from '@capacitor-community/camera-preview';

const cameraSampleOptions: CameraSampleOptions = {
  quality: 50
};

const result = await CameraPreview.captureSample(cameraSampleOptions);
const base64PictureData = result.value;
```

### getSupportedFlashModes()

<info>Get the flash modes supported by the camera device currently started. Returns an array containing supported flash modes. See <code>[FLASH_MODE](#camera_Settings.FlashMode)</code> for possible values that can be returned</info><br/>

```javascript
import { CameraPreviewFlashMode } from '@capacitor-community/camera-preview';

const flashModes = await CameraPreview.getSupportedFlashModes();
const supportedFlashModes: CameraPreviewFlashMode[] = flashModes.result;
```
### setFlashMode(options)

<info>Set the flash mode. See <code>[FLASH_MODE](#camera_Settings.FlashMode)</code> for details about the possible values for flashMode.</info><br/>

```javascript
const CameraPreviewFlashMode: CameraPreviewFlashMode = 'torch';

CameraPreview.setFlashMode(cameraPreviewFlashMode);
```

### setOpacity(options: CameraOpacityOptions): Promise<{}>;  ---- ANDROID only

<info>Set the opacity for the camera preview</info><br/>

```javascript
const myCamera = CameraPreview.start({enableOpacity: true});
myCamera.setOpacity({opacity: 0.4});
```

# Settings

<a name="camera_Settings.FlashMode"></a>

### FLASH_MODE

<info>Flash mode settings:</info><br/>

| Name    | Type    | Default | Note          |
| ------- | ------- | ------- | ------------- |
| OFF     | string  | off     |               |
| ON      | string  | on      |               |
| AUTO    | string  | auto    |               |
| RED_EYE | string  | red-eye | Android Only  |
| TORCH   | string  | torch   |               |
