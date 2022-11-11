import { registerPlugin } from '@capacitor/core';

import type { CameraPreviewPlugin } from './definitions';

const CameraPreview = registerPlugin<CameraPreviewPlugin>('CameraPreview', {
  web: () => import('./web').then((m) => new m.CameraPreviewWeb()),
});

export * from './definitions';
export { CameraPreview };
