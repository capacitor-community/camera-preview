import { Component } from "@angular/core";
import { FormsModule } from "@angular/forms";

import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCheckbox,
  IonContent,
  IonHeader,
  IonTitle,
  IonToolbar,
} from "@ionic/angular/standalone";

// NATIVE
import { CameraPreview } from "@capacitor-community/camera-preview";
import type { CameraPreviewOptions } from "@capacitor-community/camera-preview";

const DEFAULT_PREVIEW_OPTIONS: CameraPreviewOptions = {
  parent: "content",
  disableAudio: true,
  toBack: true,
};

@Component({
  selector: "app-home",
  templateUrl: "home.page.html",
  styleUrls: ["home.page.scss"],
  imports: [
    FormsModule,
    IonButton,
    IonCard,
    IonCardContent,
    IonCheckbox,
    IonContent,
    IonHeader,
    IonTitle,
    IonToolbar,
  ],
})
export class HomePage {
  public imageData: string | undefined;

  protected partialMode = false;
  protected cameraActive = false;
  protected capturePending = false;
  protected stopPending = false;
  protected startStatus = "";
  protected actionStatus = "";
  protected readyStatus = "";

  private previewAttempt = 0;
  private readyPoll: ReturnType<typeof setInterval> | undefined;

  public async showFrontCameraPreview(): Promise<void> {
    await this.startPreview("front");
  }

  public async showRearCameraPreview(): Promise<void> {
    await this.startPreview("rear");
  }

  public async takePhoto(): Promise<void> {
    this.imageData = undefined;
    this.capturePending = true;
    this.actionStatus = "capture() pending";

    try {
      const result = await CameraPreview.capture({ quality: 85 });

      this.imageData = `data:image/jpeg;base64,${result.value}`;
      this.actionStatus = "capture() resolved";
    } catch (error: unknown) {
      this.actionStatus = `capture() rejected: ${this.getErrorMessage(error)}`;
    } finally {
      this.capturePending = false;
    }
  }

  public async flip(): Promise<void> {
    this.actionStatus = "flip() pending";

    try {
      await CameraPreview.flip();
      this.actionStatus = "flip() resolved";
    } catch (error: unknown) {
      this.actionStatus = `flip() rejected: ${this.getErrorMessage(error)}`;
    }
  }

  public async stop(): Promise<void> {
    ++this.previewAttempt;
    this.stopPending = true;
    this.actionStatus = "stop() pending";

    try {
      await CameraPreview.stop();
      this.resetPreviewState();
    } catch (error: unknown) {
      this.actionStatus = `stop() rejected: ${this.getErrorMessage(error)}`;
    } finally {
      this.stopPending = false;
    }
  }

  private async startPreview(position: "front" | "rear"): Promise<void> {
    const attempt = ++this.previewAttempt;

    document.documentElement.classList.add('camera-preview-active');

    this.imageData = undefined;
    this.cameraActive = true;
    this.startStatus = "start() pending";
    this.actionStatus = "";
    this.startReadyPolling();

    const options: CameraPreviewOptions = {
      ...DEFAULT_PREVIEW_OPTIONS,
      position,
    };

    if (this.partialMode) {
      options.x = 50;
      options.y = 500;
      options.width = 200;
      options.height = 200;
      options.toBack = false;
    }

    try {
      await CameraPreview.start(options);

      if (attempt === this.previewAttempt) {
        this.startStatus = "start() resolved";
      }
    } catch (error: unknown) {
      if (attempt === this.previewAttempt) {
        this.startStatus = `start() rejected: ${this.getErrorMessage(error)}`;
      }
    }
  }

  private resetPreviewState(): void {
    document.documentElement.classList.remove('camera-preview-active');

    this.stopReadyPolling();

    this.imageData = undefined;
    this.cameraActive = false;
    this.capturePending = false;
    this.startStatus = "";
    this.actionStatus = "";
    this.readyStatus = "";
  }

  /**
   * Polls isCameraStarted() so the issue #424 acceptance test can see readiness flip from false to
   * true only once the native preview has produced its first frame.
   */
  private startReadyPolling(): void {
    this.stopReadyPolling();
    this.readyStatus = "isCameraStarted(): unknown";

    const poll = async (): Promise<void> => {
      try {
        const { value } = await CameraPreview.isCameraStarted();
        this.readyStatus = `isCameraStarted(): ${value}`;
      } catch (error: unknown) {
        this.readyStatus = `isCameraStarted() rejected: ${this.getErrorMessage(error)}`;
      }
    };

    void poll();
    this.readyPoll = setInterval(() => void poll(), 500);
  }

  private stopReadyPolling(): void {
    if (this.readyPoll !== undefined) {
      clearInterval(this.readyPoll);
      this.readyPoll = undefined;
    }
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }

    if (typeof error === "object" && error !== null && "message" in error) {
      const message = (error as { message?: unknown }).message;
      if (typeof message === "string") {
        return message;
      }
    }

    return String(error);
  }
}
