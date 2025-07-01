import { Component } from '@angular/core';

import {
  IonHeader, IonButton, IonToolbar, IonTitle, IonContent, IonCard, IonCardContent, IonCheckbox
} from '@ionic/angular/standalone';

// NATIVE
import { CameraPreview } from '@capacitor-community/camera-preview';
import type { CameraPreviewOptions } from '@capacitor-community/camera-preview';
import { FormsModule } from '@angular/forms';

const DEFAULT_PREVIEW_OPTIONS: CameraPreviewOptions = {
  parent: 'content',
  disableAudio: true,
  toBack: true,
};

@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
  imports: [
    IonButton,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonCard,
    IonCardContent,
    IonCheckbox,
    FormsModule
]
})
export class HomePage {

  public imageData: string | undefined;

  protected partialMode = false;

  constructor() { }

  public async showFrontCameraPreview(): Promise<void> {
    this.imageData = undefined;

    const options = { ...DEFAULT_PREVIEW_OPTIONS, position: 'front' };
    if (this.partialMode) {
      options.x = 50;
      options.y = 500;
      options.width = 200;
      options.height = 200;
      options.toBack = false;
    }

    await CameraPreview.start(options);
  }

  public async showRearCameraPreview(): Promise<void> {
    this.imageData = undefined;

    const options = { ...DEFAULT_PREVIEW_OPTIONS, position: 'rear' };
    if (this.partialMode) {
      options.x = 50;
      options.y = 500;
      options.width = 200;
      options.height = 200;
      options.toBack = false;
    }

    await CameraPreview.start(options);
  }

  public async stop(): Promise<void> {
    await CameraPreview.stop();
  }

  public async flip(): Promise<void> {
    await CameraPreview.flip();
  }

  public async captureSample(): Promise<void> {
    const cameraSampleOptions = {
      quality: 50
    };

    const result = await CameraPreview.captureSample(cameraSampleOptions);
    // console.log(`data:image/jpeg;base64,${result.value}`);
    this.imageData = `data:image/jpeg;base64,${result.value}`;

    await new Promise(resolve => setTimeout(resolve, 250));

    const sampleImageElement = document.getElementById('sampleImage') as HTMLImageElement;
    // NOTE: Can be set to the src of an image now
    sampleImageElement.src = this.imageData;

    await this.stop();
  }

}
