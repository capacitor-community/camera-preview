declare module "@capacitor/core" {
  interface PluginRegistry {
    CameraPreview: CameraPreviewPlugin;
  }
}

export interface CameraPreviewPlugin {
  start({}): Promise<{}>;
  stop(): Promise<{}>;
  capture(): Promise<{ value: string }>;
}
