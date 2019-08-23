declare module "@capacitor/core" {
  interface PluginRegistry {
    CameraPreview: CameraPreviewPlugin;
  }
}

export interface CameraPreviewPlugin {
  start(): Promise<void>;
  stop(): Promise<void>;
  capture(): Promise<string>;
}
