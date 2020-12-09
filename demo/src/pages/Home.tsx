import {
  IonButton,
  IonContent,
  IonHeader,
  IonPage,
  IonTitle,
  IonToolbar,
} from "@ionic/react";
import React from "react";
import ExploreContainer from "../components/ExploreContainer";
import "./Home.css";
import { Plugins } from "@capacitor/core";

const Home: React.FC = () => {
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
            Plugins.CameraPreview.start({
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
            Plugins.CameraPreview.start({
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
            Plugins.CameraPreview.stop();
          }}
        >
          Stop
        </IonButton>
        <IonButton
          style={{ zIndex: "99999" }}
          onClick={() => {
            Plugins.CameraPreview.flip();
          }}
        >
          Flip
        </IonButton>
      </IonContent>
    </IonPage>
  );
};

export default Home;
