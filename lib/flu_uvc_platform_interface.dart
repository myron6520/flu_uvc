import 'dart:async';
import 'dart:typed_data';

import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flu_uvc_method_channel.dart';

abstract class FluUvcPlatform extends PlatformInterface {
  /// Constructs a FluUvcPlatform.
  FluUvcPlatform() : super(token: _token);

  static final Object _token = Object();

  static FluUvcPlatform _instance = MethodChannelFluUvc();

  /// The default instance of [FluUvcPlatform] to use.
  ///
  /// Defaults to [MethodChannelFluUvc].
  static FluUvcPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FluUvcPlatform] when
  /// they register themselves.
  static set instance(FluUvcPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  late final StreamController<String> _barcodeDetectedController =
      StreamController.broadcast();

  Stream<String> get barcodeDetectedStream =>
      _barcodeDetectedController.stream.asBroadcastStream();
  void onBarcodeDetected(String barcode) {
    _barcodeDetectedController.add(barcode);
  }

  Future<bool> canScan() {
    throw UnimplementedError('canScan() has not been implemented.');
  }

  Future<void> startScan() {
    throw UnimplementedError('startScan() has not been implemented.');
  }

  Future<void> stopScan() {
    throw UnimplementedError('stopScan() has not been implemented.');
  }

  Future<Map> getImage() {
    throw UnimplementedError('getImage() has not been implemented.');
  }
}
