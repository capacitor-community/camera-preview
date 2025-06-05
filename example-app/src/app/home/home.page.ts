import { Component } from '@angular/core';
import { NgIf } from '@angular/common';
import {
  IonHeader, IonButton, IonToolbar, IonTitle, IonContent, IonCard, IonCardContent
} from '@ionic/angular/standalone';

// NATIVE
import { CameraPreview } from '@capacitor-community/camera-preview';

@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
  imports: [
    IonButton, IonHeader, IonToolbar, IonTitle, IonContent, IonCard, IonCardContent, NgIf
  ]
})
export class HomePage {

  public imageData: string | undefined;

  constructor() { }

  public async showFrontCameraPreview(
    useSafeArea: boolean = false
  ): Promise<void> {
    this.imageData = undefined;
    await CameraPreview.start({
      parent: 'content',
      toBack: true,
      position: 'front',
      disableAudio: true,
      useSafeArea
    });
  }

  public async showRearCameraPreview(
    useSafeArea: boolean = false
  ): Promise<void> {
    this.imageData = undefined;
    await CameraPreview.start({
      parent: 'content',
      toBack: true,
      position: 'rear',
      disableAudio: true,
      useSafeArea
    });
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
