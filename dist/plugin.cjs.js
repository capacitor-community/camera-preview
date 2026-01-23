'use strict';

var core = require('@capacitor/core');

const CameraPreview = core.registerPlugin('CameraPreview', {
  web: () =>
    Promise.resolve()
      .then(function () {
        return web;
      })
      .then((m) => new m.CameraPreviewWeb()),
});

class CameraPreviewWeb extends core.WebPlugin {
  async start(options) {
    // eslint-disable-next-line no-async-promise-executor
    return new Promise(async (resolve, reject) => {
      var _a;
      await navigator.mediaDevices
        .getUserMedia({
          audio: !options.disableAudio,
          video: true,
        })
        .then((stream) => {
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
        if ((_a = navigator.mediaDevices) === null || _a === undefined ? undefined : _a.getUserMedia) {
          const constraints = {
            video: {
              width: { ideal: options.width },
              height: { ideal: options.height },
            },
          };
          if (options.position === 'rear') {
            constraints.video.facingMode = 'environment';
            this.isBackCamera = true;
          } else {
            this.isBackCamera = false;
          }
          navigator.mediaDevices.getUserMedia(constraints).then(
            function (stream) {
              //video.src = window.URL.createObjectURL(stream);
              videoElement.srcObject = stream;
              videoElement.play();
              resolve();
            },
            (err) => {
              reject(err);
            },
          );
        }
      } else {
        reject({ message: 'camera already started' });
      }
    });
  }
  async startRecordVideo() {
    throw this.unimplemented('Not implemented on web.');
  }
  async stopRecordVideo() {
    throw this.unimplemented('Not implemented on web.');
  }
  async stop() {
    const video = document.getElementById('video');
    if (video) {
      video.pause();
      const st = video.srcObject;
      const tracks = st.getTracks();
      for (const track of tracks) {
        track.stop();
      }
      video.remove();
    }
  }
  async capture(options) {
    return new Promise((resolve) => {
      const video = document.getElementById('video');
      const canvas = document.createElement('canvas');
      // video.width = video.offsetWidth;
      const context = canvas.getContext('2d');
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      // flip horizontally back camera isn't used
      if (!this.isBackCamera) {
        context.translate(video.videoWidth, 0);
        context.scale(-1, 1);
      }
      context.drawImage(video, 0, 0, video.videoWidth, video.videoHeight);
      let base64EncodedImage;
      if (options.quality != undefined) {
        base64EncodedImage = canvas
          .toDataURL('image/jpeg', options.quality / 100.0)
          .replace('data:image/jpeg;base64,', '');
      } else {
        base64EncodedImage = canvas.toDataURL('image/png').replace('data:image/png;base64,', '');
      }
      resolve({
        value: base64EncodedImage,
      });
    });
  }
  async captureSample(_options) {
    return this.capture(_options);
  }
  async getSupportedFlashModes() {
    throw new Error('getSupportedFlashModes not supported under the web platform');
  }
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async setFlashMode(_options) {
    throw new Error('setFlashMode not supported under the web platform');
  }
  async flip() {
    throw new Error('flip not supported under the web platform');
  }
  async getZoom() {
    throw new Error('getZoom not supported under the web platform');
  }
  async setZoom(_options) {
    throw new Error('setZoom not supported under the web platform');
  }
  async getMaxZoom() {
    throw new Error('getMaxZoom not supported under the web platform');
  }
  async getMaxZoomLimit() {
    throw new Error('getMaxZoomLimit not supported under the web platform');
  }
  async setMaxZoomLimit(_options) {
    throw new Error('setMaxZoomLimit not supported under the web platform');
  }
  async setOpacity(_options) {
    const video = document.getElementById('video');
    if (!!video && !!_options['opacity']) {
      video.style.setProperty('opacity', _options['opacity'].toString());
    }
  }
  async isCameraStarted() {
    throw this.unimplemented('Not implemented on web.');
  }
  async getCameraCharacteristics() {
    throw new Error('getCameraCharacteristics not supported under the web platform');
  }
  async setApi(options) {
    throw new Error('setApi not supported under the web platform: ' + options.api);
  }
  async getCamera2SupportLevel() {
    throw new Error('getCamera2SupportLevel not supported under the web platform');
  }
}

var web = /*#__PURE__*/ Object.freeze({
  __proto__: null,
  CameraPreviewWeb: CameraPreviewWeb,
});

exports.CameraPreview = CameraPreview;
//# sourceMappingURL=plugin.cjs.js.map
