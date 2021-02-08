# Capacitor Camera Preview

Capacitor plugin that allows camera interaction from Javascript and HTML (based on cordova-plugin-camera-preview)

**Releases are being kept up to date when appropriate. However, this plugin is under constant development. As such it is recommended to use master to always have the latest fixes & features.**

**PR's are greatly appreciated. Maintainer(s) wanted.**

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

<!-- Use any one of the installation methods listed below depending on which framework you use. -->

<!-- To install the master version with latest fixes and features -->

<!-- ```
cordova plugin add https://github.com/cordova-plugin-camera-preview/cordova-plugin-camera-preview.git

ionic cordova plugin add https://github.com/cordova-plugin-camera-preview/cordova-plugin-camera-preview.git

meteor add cordova:cordova-plugin-camera-preview@https://github.com/cordova-plugin-camera-preview/cordova-plugin-camera-preview.git#[latest_commit_id]

<plugin spec="https://github.com/cordova-plugin-camera-preview/cordova-plugin-camera-preview.git" source="git" />
``` -->

<!-- or if you want to use the last released version on npm -->

```
yarn add @capacitor-community/camera-preview

or

npm install @capacitor-community/camera-preview
```
Then run
```
npx cap sync
```

#### Android Quirks

On Android remember to add the plugin to `MainActivity`
```java
import com.ahm.capacitor.camera.preview.CameraPreview;


this.init(savedInstanceState, new ArrayList<Class<? extends Plugin>>() {{
      // Additional plugins you've installed go here
      // Ex: add(TotallyAwesomePlugin.class);
      add(CameraPreview.class);
}});
```

#### Web Quirks
Add `import '@capacitor-community/camera-preview'` to you entry script in ionic on `app.module.ts`, so capacitor can register the web platform from the plugin
<!--
#### iOS Quirks
If you are developing for iOS 10+ you must also add the following to your config.xml

```xml
<config-file platform="ios" target="*-Info.plist" parent="NSCameraUsageDescription" overwrite="true">
  <string>Allow the app to use your camera</string>
</config-file> -->

<!-- or for Phonegap -->

<!-- <gap:config-file platform="ios" target="*-Info.plist" parent="NSCameraUsageDescription" overwrite="true">
  <string>Allow the app to use your camera</string>
</gap:config-file>
``` -->
<!--
#### Android Quirks (older devices)
When using the plugin for older devices, the camera preview will take the focus inside the app once initialized.
In order to prevent the app from closing when a user presses the back button, the event for the camera view is disabled.
If you still want the user to navigate, you can add a listener for the back event for the preview
(see <code>[onBackButton](#onBackButton)</code>)
 -->

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
| disableAudio | boolean | (optional) Disables audio stream to prevent permission requests, default false. (applicable to web only) |

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
import { Plugins } from "@capacitor/core"
const { CameraPreview } = Plugins;
import { CameraPreviewOptions } from '@capacitor-community/camera-preview';

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
Take into account that this will make transparent all ion-content on application, if you want to show camera preview only in one page, just add a cutom class to your ion-content and make it transparent:

```css
.my-custom-camera-preview-content {
  --background: transparent;
}
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
import { CameraPreviewFlashMode } from 'c@capacitor-community/camera-preview';

const cameraPreviewPictureOptions: CameraPreviewPictureOptions = {
  quality: 50
};

const result = await CameraPreview.capture(cameraPreviewPictureOptions);
const base64PictureData = result.value;

// do sometime with base64PictureData

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

### startRecordVideo(options)  ---- ANDROID only

<info>Start capturing video</info><br/>

```javascript
const cameraPreviewOptions: CameraPreviewOptions = {
  position: 'front',
  width: window.screen.width,
  height: window.screen.height,
};

CameraPreview.startRecordVideo(cameraPreviewOptions);
```

### stopCaptureVideo()  ---- ANDROID only

<info>Finish capturing a video. The captured video will be returned as a file path and the video format is .mp4</info><br/>

```javascript
const resultRecordVideo = await CameraPreview.stopRecordVideo();
this.stopCamera();
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
