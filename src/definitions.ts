declare module "@capacitor/core" {
  interface PluginRegistry {
    CameraPreview: CameraPreviewPlugin;
  }
}

export interface CameraPreviewPlugin {
  echo(options: { value: string }): Promise<{value: string}>;
}
