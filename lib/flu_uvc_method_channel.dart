import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flu_uvc_platform_interface.dart';

/// An implementation of [FluUvcPlatform] that uses method channels.
class MethodChannelFluUvc extends FluUvcPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flu_uvc')
    ..setMethodCallHandler((call) async {
      if (call.method == 'onBarcodeDetected') {
        final barcode = call.arguments as String;
        print('Barcode detected: $barcode');
      }
    });

  @override
  Future<bool> canScan() async {
    final result = await methodChannel.invokeMethod<bool>('canScan');
    return result ?? false;
  }

  @override
  Future<void> startScan() async {
    await methodChannel.invokeMethod<void>('startScan');
  }

  @override
  Future<void> stopScan() async {
    await methodChannel.invokeMethod<void>('stopScan');
  }

  @override
  Future<Map> getImage() async {
    final result = await methodChannel.invokeMethod<Map>('getImage');
    return result ?? {};
  }
}
