import 'package:flutter_test/flutter_test.dart';
import 'package:flu_uvc/flu_uvc.dart';
import 'package:flu_uvc/flu_uvc_platform_interface.dart';
import 'package:flu_uvc/flu_uvc_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFluUvcPlatform
    with MockPlatformInterfaceMixin
    implements FluUvcPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final FluUvcPlatform initialPlatform = FluUvcPlatform.instance;

  test('$MethodChannelFluUvc is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFluUvc>());
  });

  test('getPlatformVersion', () async {
    FluUvc fluUvcPlugin = FluUvc();
    MockFluUvcPlatform fakePlatform = MockFluUvcPlatform();
    FluUvcPlatform.instance = fakePlatform;

    expect(await fluUvcPlugin.getPlatformVersion(), '42');
  });
}
