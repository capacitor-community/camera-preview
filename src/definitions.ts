declare module "@capacitor/core" {
  interface PluginRegistry {
    CameraPreview: CameraPreviewPlugin;
  }
}

export type CameraPosition = 'rear' | 'front';
export interface CameraPreviewOptions {
  /** Parent element to attach the video preview element to (applicable to the web platform only) */
  parent?: string;
  /** Class name to add to the video preview element (applicable to the web platform only) */
  className?: string;
  /** The preview width in pixels, default window.screen.width (applicable to the android and ios platforms only) */
  width?: number;
  /** The preview height in pixels, default window.screen.height (applicable to the android and ios platforms only) */
  height?: number;
  /** The x origin, default 0 (applicable to the android and ios platforms only) */
  x?: number;
  /** The y origin, default 0 (applicable to the android and ios platforms only) */
  y?: number;
  /**  Brings your html in front of your preview, default false (applicable to the android only) */
  toBack?: boolean;
  /** The preview bottom padding in pixes. Useful to keep the appropriate preview sizes when orientation changes (applicable to the android and ios platforms only) */
  paddingBottom?: number;
  /** Rotate preview when orientation changes (applicable to the ios platforms only; default value is true) */
  rotateWhenOrientationChanged?: boolean;
  /** Choose the camera to use 'front' or 'rear', default 'front' */
  position?: CameraPosition | string;
  /** Defaults to false - Capture images to a file and return back the file path instead of returning base64 encoded data */
  storeToFile?: boolean;
  /** Defaults to false - Android Only - Disable automatic rotation of the image, and let the browser deal with it (keep reading on how to achieve it) */
  disableExifHeaderStripping?: boolean;
  /** Defaults to false - iOS only - Activate high resolution image capture so that output images are from the highest resolution possible on the device **/
  enableHighResolution?: boolean;
  /** Defaults to false - Web only - Disables audio stream to prevent permission requests and output switching */
  disableAudio?: boolean;
  /** If camea preview will manage opacity.  Default is false */
  enableOpacity?: boolean;
}
export interface CameraPreviewPictureOptions {
  /** The picture height, optional, default 0 (Device default) */
  height?: number;
  /** The picture width, optional, default 0 (Device default) */
  width?: number;
  /** The picture quality, 0 - 100, default 85 */
  quality?: number;
}

export interface CameraSampleOptions {
  /** The picture quality, 0 - 100, default 85 */
  quality?: number;
}
export interface CameraOpacityOptions {
    /** The picture quality, 0 - 100, default 85 */
    opacity?: number;
}

export type CameraPreviewFlashMode = 'off' | 'on' | 'auto' | 'red-eye' | 'torch';

export interface CameraPreviewPlugin {
  start(options: CameraPreviewOptions): Promise<{}>;
  stop(): Promise<{}>;
  capture(options: CameraPreviewPictureOptions): Promise<{ value: string }>;
  captureSample(options: CameraSampleOptions): Promise<{ value: string }>;
  getSupportedFlashModes(): Promise<{
    result: CameraPreviewFlashMode[]
  }>;
  setFlashMode(options: { flashMode: CameraPreviewFlashMode | string }): void;
  flip(): void;
  setOpacity(options: CameraOpacityOptions): void;
}
