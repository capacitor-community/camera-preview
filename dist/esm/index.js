import { registerPlugin } from '@capacitor/core';
const CameraPreview = registerPlugin('CameraPreview', {
    web: () => import('./web').then((m) => new m.CameraPreviewWeb()),
});
export * from './definitions';
export { CameraPreview };
//# sourceMappingURL=index.js.map