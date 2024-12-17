<p align="center"><br><img src="https://user-images.githubusercontent.com/236501/85893648-1c92e880-b7a8-11ea-926d-95355b8175c7.png" width="128" height="128" /></p>
<h3 align="center">Capacitor Camera Preview</h3>
<p align="center"><strong><code>@capacitor-community/camera-preview</code></strong></p>
<br>
<p align="center"><strong>CAPACITOR 5</strong></p><br>

<p align="center">
  Capacitor plugin that allows camera interaction from Javascript and HTML<br>(based on cordova-plugin-camera-preview).
</p>
<br>
Version 6 of this plugin requires Capacitor 6.

If you are using Capacitor 5, use [version 5](https://github.com/capacitor-community/camera-preview/releases/tag/v5.0.0)

If you are using Capacitor 4, use [version 4](https://github.com/capacitor-community/camera-preview/releases/tag/v4.0.0)

If you are using Capacitor 3, use [version 3](https://github.com/capacitor-community/camera-preview/releases/tag/v3.1.2)

If you are using Capacitor 2, use [version 1](https://github.com/capacitor-community/camera-preview/releases/tag/v1.2.1)

**PR's are greatly appreciated.**

-- [@arielhernandezmusa](https://github.com/arielhernandezmusa) and [@pbowyer](https://github.com/pbowyer), current maintainers

<!-- # Features

<ul>
  <li>Start a camera preview from HTML code.</li>
  <li>Maintain HTML interactivity.</li>
  <li>Drag the preview box.</li>
  <li>Set camera color effect.</li>
  <li>Send the preview box to back of the HTML content.</li>
  <li>Set a custom position for the camera preview box.</li>
  <li>Set a custom size for the preview box.</li>
  <li>Set a custom alpha for the preview box.</li>
  <li>Set the focus mode, zoom, color effects, exposure mode, white balance mode and exposure compensation</li>
  <li>Tap to focus</li>
</ul> -->

# Installation

```
yarn add @capacitor-community/camera-preview

or

npm install @capacitor-community/camera-preview
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
You will need to add two permissions to `Info.plist`. Follow the [Capacitor docs](https://capacitorjs.com/docs/ios/configuration#configuring-infoplist) and add permissions with the raw keys `NSCameraUsageDescription` and `NSMicrophoneUsageDescription`. `NSMicrophoneUsageDescription` is only required, if audio will be used. Otherwise set the `disableAudio` option to `true`, which also disables the microphone permission request.

## Extra Web installation steps

Add `import { CameraPreview } from '@capacitor-community/camera-preview';` in the file where you want to use the plugin.

then in html add `<div id="cameraPreview"></div>`

and `CameraPreview.start({ parent: "cameraPreview"});` will work.


# Methods

### start(options)

Starts the camera preview instance.
<br>

| Option   | values        | descriptions                                                           |
|----------|---------------|------------------------------------------------------------------------|
| position | front \| rear | Show front or rear camera when start the preview. Defaults to front    |
| width    | number        | (optional) The preview width in pixels, default window.screen.width (applicable to the android and ios platforms only)                                                                 |
| height   | number        | (optional) The preview height in pixels, default window.screen.height  (applicable to the android and ios platforms only)                                                              |
| x        | number        | (optional) The x origin, default 0 (applicable to the android and ios platforms only)    |
| y        | number        | (optional) The y origin, default 0 (applicable to the android and ios platforms only)    |
| toBack   | boolean       | (optional) Brings your html in front of your preview, default false (applicable to the android and ios platforms only) |
| paddingBottom | number       | (optional) The preview bottom padding in pixes. Useful to keep the appropriate preview sizes when orientation changes (applicable to the android and ios platforms only)           |
| rotateWhenOrientationChanged | boolean   | (optional) Rotate preview when orientation changes (applicable to the ios platforms only; default value is true)                                                      |
| storeToFile | boolean       | (optional) Capture images to a file and return back the file path instead of returning base64 encoded data, default false. |
| disableExifHeaderStripping | boolean       | (optional) Disable automatic rotation of the image, and let the browser deal with it, default true (applicable to the android and ios platforms only) |
| enableHighResolution | boolean       | (optional) Defaults to false - iOS only - Activate high resolution image capture so that output images are from the highest resolution possible on the device |
| disableAudio | boolean | (optional) Disables audio stream to prevent permission requests, default false. (applicable to web and iOS only) |
| lockAndroidOrientation | boolean | (optional) Locks device orientation when camera is showing, default false. (applicable to Android only) |
| enableOpacity | boolean | (optional) Make the camera preview see-through. Ideal for augmented reality uses. Default false (applicable to Android and web only)
| enableZoom | boolean | (optional) Set if you can pinch to zoom. Default false (applicable to the android and ios platforms only)

<!-- <strong>Options:</strong>
All options stated are optional and will default to values here

* `x` - Defaults to 0
* `y` - Defaults to 0
* `width` - Defaults to window.screen.width
* `height` - Defaults to window.screen.height
* `camera` - See <code>[CAMERA_DIRECTION](#camera_Settings.CameraDirection)</code> - Defaults to front camera
* `toBack` - Defaults to false - Set to true if you want your html in front of your preview
* `tapPhoto` - Defaults to true - Does not work if toBack is set to false in which case you use the takePicture method
* `tapFocus` - Defaults to false - Allows the user to tap to focus, when the view is in the foreground
* `previewDrag` - Defaults to false - Does not work if toBack is set to false
* `storeToFile` - Defaults to false - Capture images to a file and return back the file path instead of returning base64 encoded data.
* `disableExifHeaderStripping` - Defaults to false - **Android Only** - Disable automatic rotation of the image, and let the browser deal with it (keep reading on how to achieve it) -->

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

<!-- ### switchCamera([successCallback, errorCallback])

<info>Switch between the rear camera and front camera, if available.</info><br/>

```javascript
CameraPreview.switchCamera();
```

### show([successCallback, errorCallback])

<info>Show the camera preview box.</info><br/>

```javascript
CameraPreview.show();
```

### hide([successCallback, errorCallback])

<info>Hide the camera preview box.</info><br/>

```javascript
CameraPreview.hide();
``` -->

### capture(options)

| Option   | values        | descriptions                                                         |
|----------|---------------|----------------------------------------------------------------------|
| quality  | number        | (optional) The picture quality, 0 - 100, default 85                  |
| width    | number        | (optional) The picture width, default 0 (Device default)             |
| height   | number        | (optional) The picture height, default 0 (Device default)            |

<!-- <info>Take the picture. If width and height are not specified or are 0 it will use the defaults. If width and height are specified, it will choose a supported photo size that is closest to width and height specified and has closest aspect ratio to the preview. The argument `quality` defaults to `85` and specifies the quality/compression value: `0=max compression`, `100=max quality`.</info><br/> -->

```javascript
import { CameraPreviewPictureOptions } from '@capacitor-community/camera-preview';

const cameraPreviewPictureOptions: CameraPreviewPictureOptions = {
  quality: 50
};

const result = await CameraPreview.capture(cameraPreviewPictureOptions);
const base64PictureData = result.value;

// do sometime with base64PictureData

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

// do something with base64PictureData

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

### startRecordVideo(options)  ---- ANDROID and iOS only

<info>Start capturing video</info><br/>

```javascript
const cameraPreviewOptions: CameraPreviewOptions = {
  position: 'front',
  width: window.screen.width,
  height: window.screen.height,
};

CameraPreview.startRecordVideo(cameraPreviewOptions);
```

### stopRecordVideo()  ---- ANDROID and iOS only

<info>Finish capturing a video. The captured video will be returned as a file path and the video format is .mp4</info><br/>

```javascript
const resultRecordVideo = await CameraPreview.stopRecordVideo();
```

### setOpacity(options: CameraOpacityOptions): Promise<{}>;  ---- ANDROID only

<info>Set the opacity for the camera preview</info><br/>

```javascript
const myCamera = CameraPreview.start({enableOpacity: true});
myCamera.setOpacity({opacity: 0.4});
```

### isCameraStarted()  ---- ANDROID and iOS only

<info>Check or detect if the camera has been started</info><br/>

```javascript
const { value } = await CameraPreview.isCameraStarted();
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

<!--

# Settings

<a name="camera_Settings.FocusMode"></a>

### FOCUS_MODE

<info>Focus mode settings:</info><br/>

| Name | Type | Default | Note |
| --- | --- | --- | --- |
| FIXED | string | fixed |  |
| AUTO | string | auto |  |
| CONTINUOUS | string | continuous | IOS Only |
| CONTINUOUS_PICTURE | string | continuous-picture | Android Only |
| CONTINUOUS_VIDEO | string | continuous-video | Android Only |
| EDOF | string | edof | Android Only |
| INFINITY | string | infinity | Android Only |
| MACRO | string | macro | Android Only |

<a name="camera_Settings.FlashMode"></a>

### FLASH_MODE

<info>Flash mode settings:</info><br/>

| Name | Type | Default | Note |
| --- | --- | --- | --- |
| OFF | string | off |  |
| ON | string | on |  |
| AUTO | string | auto |  |
| RED_EYE | string | red-eye | Android Only |
| TORCH | string | torch |  |

<a name="camera_Settings.CameraDirection"></a>

### CAMERA_DIRECTION

<info>Camera direction settings:</info><br/>

| Name | Type | Default |
| --- | --- | --- |
| BACK | string | back |
| FRONT | string | front |

<a name="camera_Settings.ColorEffect"></a>

### COLOR_EFFECT

<info>Color effect settings:</info><br/>

| Name | Type | Default | Note |
| --- | --- | --- | --- |
| AQUA | string | aqua | Android Only |
| BLACKBOARD | string | blackboard | Android Only |
| MONO | string | mono | |
| NEGATIVE | string | negative | |
| NONE | string | none | |
| POSTERIZE | string | posterize | |
| SEPIA | string | sepia | |
| SOLARIZE | string | solarize | Android Only |
| WHITEBOARD | string | whiteboard | Android Only |

<a name="camera_Settings.ExposureMode"></a>

### EXPOSURE_MODE

<info>Exposure mode settings:</info><br/>

| Name | Type | Default | Note |
| --- | --- | --- | --- |
| AUTO | string | auto | IOS Only |
| CONTINUOUS | string | continuous | |
| CUSTOM | string | custom | |
| LOCK | string | lock | IOS Only |

Note: Use AUTO to allow the device automatically adjusts the exposure once and then changes the exposure mode to LOCK.

<a name="camera_Settings.WhiteBalanceMode"></a>

### WHITE_BALANCE_MODE

<info>White balance mode settings:</info><br/>

| Name | Type | Default | Note |
| --- | --- | --- | --- |
| LOCK | string | lock | |
| AUTO | string | auto | |
| CONTINUOUS | string | continuous | IOS Only |
| INCANDESCENT | string | incandescent | |
| CLOUDY_DAYLIGHT | string | cloudy-daylight | |
| DAYLIGHT | string | daylight | |
| FLUORESCENT | string | fluorescent | |
| SHADE | string | shade | |
| TWILIGHT | string | twilight | |
| WARM_FLUORESCENT | string | warm-fluorescent | |

# IOS Quirks
It is not possible to use your computers webcam during testing in the simulator, you must device test.

# Customize Android Support Library versions (Android only)
The default `ANDROID_SUPPORT_LIBRARY_VERSION` is set to `26+`.
If you need a different version, add argument `--variable ANDROID_SUPPORT_LIBRARY_VERSION="{version}"`.

Or edit `config.xml` with following,

```xml
<plugin name="cordova-plugin-camera-preview" spec="X.X.X">
  <variable name="ANDROID_SUPPORT_LIBRARY_VERSION" value="26+" />
</plugin>
```

# Sample App

<a href="https://github.com/cordova-plugin-camera-preview/cordova-plugin-camera-preview-sample-app">cordova-plugin-camera-preview-sample-app</a> for a complete working Cordova example for Android and iOS platforms.

# Screenshots

<img src="https://raw.githubusercontent.com/cordova-plugin-camera-preview/cordova-plugin-camera-preview/master/img/android-1.png"/> <img hspace="20" src="https://raw.githubusercontent.com/cordova-plugin-camera-preview/cordova-plugin-camera-preview/master/img/android-2.png"/>

# Credits

Maintained by [Weston Ganger](https://westonganger.com) - [@westonganger](https://github.com/westonganger)

Created by Marcel Barbosa Pinto [@mbppower](https://github.com/mbppower)
Â -->

# Demo

A working example can be found at [Demo](https://github.com/capacitor-community/camera-preview/tree/master/demo)

To run the demo on your local network and access media devices, a secure context is needed. Add an `.env` file at the root of the demo folder with `HTTPS=true` to start react with HTTPS.
