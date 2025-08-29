import { WebPlugin } from '@capacitor/core';
import type { CameraPreviewOptions, CameraPreviewPictureOptions, CameraPreviewPlugin, CameraPreviewFlashMode, CameraSampleOptions, CameraOpacityOptions } from './definitions';
export declare class CameraPreviewWeb extends WebPlugin implements CameraPreviewPlugin {
    /**
     *  track which camera is used based on start options
     *  used in capture
     */
    private isBackCamera;
    start(options: CameraPreviewOptions): Promise<void>;
    startRecordVideo(): Promise<void>;
    stopRecordVideo(): Promise<void>;
    stop(): Promise<any>;
    capture(options: CameraPreviewPictureOptions): Promise<any>;
    captureSample(_options: CameraSampleOptions): Promise<any>;
    getSupportedFlashModes(): Promise<{
        result: CameraPreviewFlashMode[];
    }>;
    setFlashMode(_options: {
        flashMode: CameraPreviewFlashMode | string;
    }): Promise<void>;
    flip(): Promise<void>;
    getZoom(): Promise<{
        value: number;
    }>;
    setZoom(_options: {
        zoom: number;
    }): Promise<void>;
    getMaxZoom(): Promise<{
        value: number;
    }>;
    getMaxZoomLimit(): Promise<{
        value: number;
    }>;
    setMaxZoomLimit(_options: {
        zoom: number;
    }): Promise<void>;
    setOpacity(_options: CameraOpacityOptions): Promise<any>;
    isCameraStarted(): Promise<{
        value: boolean;
    }>;
    getCameraCharacteristics(): Promise<any>;
    setApi(options: {
        api: number;
    }): Promise<void>;
    getCamera2SupportLevel(): Promise<{
        name: string;
        level: number;
    }>;
}
