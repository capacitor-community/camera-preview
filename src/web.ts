import { WebPlugin } from '@capacitor/core';

import type {
  CameraPreviewOptions,
  CameraPreviewPictureOptions,
  CameraPreviewFlashMode,
  CameraSampleOptions,
  CameraOpacityOptions,
} from './definitions';
import { CameraPreviewPlugin } from './definitions';

export class CameraPreviewWeb extends WebPlugin implements CameraPreviewPlugin {
  /**
   *  track which camera is used based on start options
   *  used in capture
   */
  private isBackCamera: boolean;

  constructor() {
    super({
      name: 'CameraPreview',
      platforms: ['web'],
    });
  }

  async start(options: CameraPreviewOptions): Promise<void> {
    await navigator.mediaDevices
      .getUserMedia({
        audio: !options.disableAudio,
        video: true,
      })
      .then((stream: MediaStream) => {
        // Stop any existing stream so we can request media with different constraints based on user input
        stream.getTracks().forEach((track) => track.stop());
      })
      .catch((error) => {
        reject(error);
      });

    const video = document.getElementById('video');
    const parent = document.getElementById(options.parent);

    if (!video) {
      const videoElement = document.createElement('video');
      videoElement.id = 'video';
      videoElement.setAttribute('class', options.className || '');

      // Don't flip video feed if camera is rear facing
      if (options.position !== 'rear') {
        videoElement.setAttribute('style', '-webkit-transform: scaleX(-1); transform: scaleX(-1);');
      }

      const userAgent = navigator.userAgent.toLowerCase();
      const isSafari = userAgent.includes('safari') && !userAgent.includes('chrome');

      // Safari on iOS needs to have the autoplay, muted and playsinline attributes set for video.play() to be successful
      // Without these attributes videoElement.play() will throw a NotAllowedError
      // https://developer.apple.com/documentation/webkit/delivering_video_content_for_safari
      if (isSafari) {
        videoElement.setAttribute('autoplay', 'true');
        videoElement.setAttribute('muted', 'true');
        videoElement.setAttribute('playsinline', 'true');
      }

      parent.appendChild(videoElement);

      if (navigator?.mediaDevices?.getUserMedia) {
        const constraints: MediaStreamConstraints = {
          video: {
            width: { ideal: options.width },
            height: { ideal: options.height },
          },
        };

        if (options.position === 'rear') {
          (constraints.video as MediaTrackConstraints).facingMode = 'environment';
          this.isBackCamera = true;
        } else {
          this.isBackCamera = false;
        }

          const self = this;
          await navigator.mediaDevices.getUserMedia(constraints).then(
            function (stream) {
              if (document.getElementById('video')) {
                //video.src = window.URL.createObjectURL(stream);
                videoElement.srcObject = stream;
                videoElement.play();
                resolve({});
              } else {
                self.stopStream(stream);
                reject({ message: 'camera already stopped' });
              }
            },
            (err) => {
              reject(err);
            }
          );
        }
      } else {
        reject({ message: 'camera already started' });
      }
    });
  }

  private stopStream(stream: any) {
    if (stream) {
      const tracks = stream.getTracks();

      for (let i = 0; i < tracks.length; i++) {
        const track = tracks[i];
        track.stop();
      }
    }
  }

  async stop(): Promise<any> {
    const video = document.getElementById('video') as HTMLVideoElement;
    if (video) {
      video.pause();

      this.stopStream(video.srcObject);

      video.remove();
    }
  }

  async capture(options: CameraPreviewPictureOptions): Promise<any> {
    return new Promise((resolve, reject) => {
      const video = <HTMLVideoElement>document.getElementById('video');
      if (!video || !video.srcObject) {
        reject({ message: 'camera is not running' });
        return;
      }

      // video.width = video.offsetWidth;

      let base64EncodedImage;

      if (video && video.videoWidth > 0 && video.videoHeight > 0) {
        const canvas = document.createElement('canvas');
        const context = canvas.getContext('2d');
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;

        // flip horizontally back camera isn't used
        if (!this.isBackCamera) {
          context.translate(video.videoWidth, 0);
          context.scale(-1, 1);
        }
        context.drawImage(video, 0, 0, video.videoWidth, video.videoHeight);

        if ((options.format || 'jpeg') === 'jpeg') {
          base64EncodedImage = canvas
            .toDataURL('image/jpeg', (options.quality || 85) / 100.0)
            .replace('data:image/jpeg;base64,', '');
        } else {
          base64EncodedImage = canvas.toDataURL('image/png').replace('data:image/png;base64,', '');
        }
      }

      resolve({
        value: base64EncodedImage,
      });
    });
  }

  async captureSample(_options: CameraSampleOptions): Promise<any> {
    return this.capture(_options);
  }

  async getSupportedFlashModes(): Promise<{
    result: CameraPreviewFlashMode[];
  }> {
    throw new Error('getSupportedFlashModes not supported under the web platform');
  }

  async setFlashMode(_options: { flashMode: CameraPreviewFlashMode | string }): Promise<void> {
    throw new Error('setFlashMode not supported under the web platform' + _options);
  }

  async flip(): Promise<void> {
    throw new Error('flip not supported under the web platform');
  }

  async setOpacity(_options: CameraOpacityOptions): Promise<any> {
    const video = document.getElementById('video') as HTMLVideoElement;
    if (!!video && !!_options['opacity']) {
      video.style.setProperty('opacity', _options['opacity'].toString());
    }
  }
}