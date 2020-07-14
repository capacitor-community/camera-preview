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
  /** The preview width in pixels, default window.screen.width */
  width?: number;
  /** The preview height in pixels, default window.screen.height */
  height?: number;
  /** Choose the camera to use 'front' or 'rear', default 'front' */
  position?: CameraPosition | string;
}
export interface CameraPreviewPictureOptions {
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
}
