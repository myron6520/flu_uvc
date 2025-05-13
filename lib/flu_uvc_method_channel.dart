import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flu_uvc_platform_interface.dart';

/// An implementation of [FluUvcPlatform] that uses method channels.
class MethodChannelFluUvc extends FluUvcPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flu_uvc');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }

  @override
  Future<bool> initCamera() async {
    final result = await methodChannel.invokeMethod<bool>('initCamera');
    return result ?? false;
  }

  @override
  Future<bool> startCapture() async {
    final result = await methodChannel.invokeMethod<bool>('startCapture');
    return result ?? false;
  }

  @override
  Future<bool> stopCapture() async {
    final result = await methodChannel.invokeMethod<bool>('stopCapture');
    return result ?? false;
  }

  @override
  Future<Map> getImage() async {
    final result = await methodChannel.invokeMethod<Map>('getImage');
    return result ?? {};
  }

  @override
  Future<bool> releaseCamera() async {
    final result = await methodChannel.invokeMethod<bool>('releaseCamera');
    return result ?? false;
  }
}
