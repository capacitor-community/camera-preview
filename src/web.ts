import { WebPlugin } from "@capacitor/core";
import { CameraPreviewOptions, CameraPreviewPictureOptions, CameraPreviewPlugin, CameraPreviewFlashMode } from "./definitions";

export class CameraPreviewWeb extends WebPlugin implements CameraPreviewPlugin {
  constructor() {
    super({
      name: "CameraPreview",
      platforms: ["web"],
    });
  }

  async start(options: CameraPreviewOptions): Promise<{}> {
    return new Promise((resolve, reject) => {

      navigator.mediaDevices.getUserMedia({
        audio:!options.disableAudio,  
        video:true}
      );

      const video = document.getElementById("video");
      const parent = document.getElementById(options.parent);

      if (!video) {
        const videoElement = document.createElement("video");
        videoElement.id = "video";
        videoElement.setAttribute("class", options.className || "");
        videoElement.setAttribute(
          "style",
          "-webkit-transform: scaleX(-1); transform: scaleX(-1);"
        );

        parent.appendChild(videoElement);

        if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
          // Not adding `{ audio: true }` since we only want video now
          navigator.mediaDevices.getUserMedia({ video: true }).then(
            function (stream) {
              //video.src = window.URL.createObjectURL(stream);
              videoElement.srcObject = stream;
              videoElement.play();
              resolve({});
            },
            (err) => {
              reject(err);
            }
          );
        }
      } else {
        reject({ message: "camera already started" });
      }
    });
  }

  async stop(): Promise<any> {
    const video = <HTMLVideoElement>document.getElementById("video");
    if (video) {
      video.pause();

      const st: any = video.srcObject;
      const tracks = st.getTracks();

      for (var i = 0; i < tracks.length; i++) {
        var track = tracks[i];
        track.stop();
      }
      video.remove();
    }
  }

  async capture(_options: CameraPreviewPictureOptions): Promise<any> {
    return new Promise((resolve, _) => {
      const video = <HTMLVideoElement>document.getElementById("video");
      const canvas = document.createElement("canvas");

      // video.width = video.offsetWidth;

      const context = canvas.getContext("2d");
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      context.translate(video.videoWidth, 0);
      context.scale(-1, 1);
      context.drawImage(video, 0, 0, video.videoWidth, video.videoHeight);
      resolve({
        value: canvas
          .toDataURL("image/png")
          .replace("data:image/png;base64,", ""),
      });
    });
  }

  async getSupportedFlashModes(): Promise<{
    result: CameraPreviewFlashMode[]
  }> {
    throw new Error('getSupportedFlashModes not supported under the web platform');
  }

  async setFlashMode(_options: { flashMode: CameraPreviewFlashMode | string }): Promise<void> {
    throw new Error('setFlashMode not supported under the web platform');
  }

  async flip(): Promise<void> {
    throw new Error('flip not supported under the web platform');
  }
}

const CameraPreview = new CameraPreviewWeb();

export { CameraPreview };

import { registerWebPlugin } from "@capacitor/core";
registerWebPlugin(CameraPreview);
