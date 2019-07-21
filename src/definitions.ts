declare module "@capacitor/core" {
  interface PluginRegistry {
    CameraPreview: CameraPreviewPlugin;
  }
}

export interface CameraPreviewPlugin {
  start(options: {parent: string}): Promise<{value: string}>;
  stop(): Promise<any>;
  capture(): Promise<any>;
}
