import 'dart:typed_data';

import 'flu_uvc_platform_interface.dart';

class FluUvc {
  Future<bool> canScan() {
    return FluUvcPlatform.instance.canScan();
  }

  Future<void> startScan() {
    return FluUvcPlatform.instance.startScan();
  }

  Future<void> stopScan() {
    return FluUvcPlatform.instance.stopScan();
  }

  Future<Map> getImage() {
    return FluUvcPlatform.instance.getImage();
  }
}
