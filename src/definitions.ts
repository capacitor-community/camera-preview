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
}
export interface CameraPreviewPictureOptions {
  /** The picture height, optional, default 0 (Device default) */
  height?: number;
  /** The picture width, optional, default 0 (Device default) */
  width?: number;
  /** The picture quality, 0 - 100, default 85 */
  quality?: number;
}
export type CameraPreviewFlashMode = 'off' | 'on' | 'auto' | 'red-eye' | 'torch';

export interface CameraPreviewPlugin {
  start(options: CameraPreviewOptions): Promise<{}>;
  stop(): Promise<{}>;
  capture(options: CameraPreviewPictureOptions): Promise<{ value: string }>;
  getSupportedFlashModes(): Promise<{
    result: CameraPreviewFlashMode[]
  }>;
  setFlashMode(options: { flashMode: CameraPreviewFlashMode | string }): void;
  flip(): void;
}
