import {
  IonButton,
  IonContent,
  IonHeader,
  IonPage,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import React, { useState } from "react";
import "./Home.css";
import { CameraPreview, CameraSampleOptions } from '@capacitor-community/camera-preview';


const Home: React.FC = () => {
  const [imageData, setImageData] = useState('');

  return (
    <IonPage>
      <IonHeader>
        <IonToolbar>
          <IonTitle>Blank</IonTitle>
        </IonToolbar>
      </IonHeader>
      <IonContent id="content" className="content-camera-preview" fullscreen>
        <IonButton
          style={{ zIndex: "99999" }}
          onClick={() => {
            CameraPreview.start({
              parent: "content",
              toBack: true,
              position: "front"
            });
          }}
        >
          Show Front Camera Preview
        </IonButton>
        <IonButton
          style={{ zIndex: "99999" }}
          onClick={() => {
            CameraPreview.start({
              parent: "content",
              toBack: true,
              position: "rear"
            });
          }}
        >
          Show Rear Camera Preview
        </IonButton>
        <IonButton
          style={{ zIndex: "99999" }}
          onClick={() => {
            CameraPreview.stop();
          }}
        >
          Stop
        </IonButton>
        <IonButton
          style={{ zIndex: "99999" }}
          onClick={() => {
            CameraPreview.flip();
          }}
        >
          Flip
        </IonButton>
        <IonButton
          style={{ zIndex: "99999" }}
          onClick={async () => {
            const cameraSampleOptions: CameraSampleOptions = {
              quality: 50
            };

            const result = await CameraPreview.captureSample(cameraSampleOptions);
            setImageData(`data:image/jpeg;base64,${result.value}`);
          }}
        >
          Capture Sample
        </IonButton>
        {imageData ? (
          <div>
            <img width="100px"
              src={imageData}
              alt="Most Recent"
            />
          </div>
        ) : (
          <div></div>
        )}

      </IonContent>
    </IonPage>
  );
};

export default Home;
