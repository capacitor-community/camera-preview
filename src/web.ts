import { WebPlugin } from '@capacitor/core';
import { CameraPreviewPlugin } from './definitions';

export class CameraPreviewWeb extends WebPlugin implements CameraPreviewPlugin {
  constructor() {
    super({
      name: 'CameraPreview',
      platforms: ['web']
    });
  }

  async echo(options: { value: string }): Promise<{value: string}> {
    console.log('ECHO', options);
    return options;
  }
}

const CameraPreview = new CameraPreviewWeb();

export { CameraPreview };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(CameraPreview);
