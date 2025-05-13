import 'dart:typed_data';

import 'flu_uvc_platform_interface.dart';

class FluUvc {
  Future<String?> getPlatformVersion() {
    return FluUvcPlatform.instance.getPlatformVersion();
  }

  Future<bool> initCamera() {
    return FluUvcPlatform.instance.initCamera();
  }

  Future<bool> startCapture() {
    return FluUvcPlatform.instance.startCapture();
  }

  Future<bool> stopCapture() {
    return FluUvcPlatform.instance.stopCapture();
  }

  Future<Map> getImage() {
    return FluUvcPlatform.instance.getImage();
  }

  Future<bool> releaseCamera() {
    return FluUvcPlatform.instance.releaseCamera();
  }
}
