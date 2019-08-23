import { WebPlugin } from "@capacitor/core";
import { CameraPreviewPlugin } from "./definitions";

export class CameraPreviewWeb extends WebPlugin implements CameraPreviewPlugin {
  constructor() {
    super({
      name: "CameraPreview",
      platforms: ["web"]
    });
  }

  async start(options: { parent: string, className: string }): Promise<{}> {
    return new Promise((resolve, reject) => {
      const video = document.getElementById("video");
      const parent = document.getElementById(options.parent);

      if (!video) {
        const videoElement = document.createElement("video");
        videoElement.id = "video";
        videoElement.setAttribute("class", options.className || "")

        parent.appendChild(videoElement);

        if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
          // Not adding `{ audio: true }` since we only want video now
          navigator.mediaDevices.getUserMedia({ video: true }).then(
            function(stream) {
              //video.src = window.URL.createObjectURL(stream);
              videoElement.srcObject = stream;
              videoElement.play();
              resolve();
            },
            err => {
              reject(err);
            }
          );
        }
      } else {
        reject("camera already started");
      }
    });
  }

  async stop(): Promise<any> {
    const video = <HTMLVideoElement>document.getElementById("video");
    video.pause();
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
      // Not adding `{ audio: true }` since we only want video now
      navigator.mediaDevices
        .getUserMedia({ video: true })
        .then(function(stream: MediaStream) {
          //video.src = window.URL.createObjectURL(stream);
          const tracks = stream.getTracks();
          console.log(tracks);
          tracks.forEach(track => {
            track.stop();
            stream.removeTrack(track);
          });

          video.src = "";
          video.pause();
          video.parentNode.removeChild(video);
        });
    }
  }

  async capture(): Promise<any> {
    return new Promise((resolve, _) => {
      const video = <HTMLVideoElement>document.getElementById("video");
      const canvas = document.createElement("canvas");

      // video.width = video.offsetWidth;

      const context = canvas.getContext("2d");
      canvas.width        = video.videoWidth;
      canvas.height        = video.videoHeight;
      context.drawImage(video, 0, 0, video.videoWidth, video.videoHeight);
      resolve({
        value: canvas
          .toDataURL("image/png")
          .replace("data:image/png;base64,", "")
      });
    });
  }
}

const CameraPreview = new CameraPreviewWeb();

export { CameraPreview };

import { registerWebPlugin } from "@capacitor/core";
registerWebPlugin(CameraPreview);
