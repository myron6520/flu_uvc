import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flu_uvc/flu_uvc.dart';
import 'package:flutter_zxing/flutter_zxing.dart';
import 'dart:ui' as ui;

import 'package:m2pos_common/plugins/app_plugin.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _fluUvcPlugin = FluUvc();

  String _code = "";
  @override
  void initState() {
    super.initState();
    _fluUvcPlugin.barcodeDetectedStream.listen((code) {
      AppPlugin.playKeyClickSound();
      AppPlugin.showToast(code);
      _code = code;
      setState(() {});
    });
  }

  Timer? _loopTimer;
  bool _isScanning = false;
  int _testCount = 0;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Plugin example app')),
        body: Column(
          children: [
            ElevatedButton(
              onPressed: () async {
                debugPrint("canScan:${await _fluUvcPlugin.canScan()}");
              },
              child: Text('canScan'),
            ),
            ElevatedButton(
              onPressed: () async {
                await _fluUvcPlugin.startScan();
              },
              child: Text('Start Scan'),
            ),
            ElevatedButton(
              onPressed: () async {
                await _fluUvcPlugin.stopScan();
              },
              child: Text('Stop Scan'),
            ),
            Row(
              children: [
                ElevatedButton(
                  onPressed: () async {
                    startLoopingScan();
                  },
                  child: Text('Auto Test'),
                ),
                Text('测试次数：$_testCount'),
              ],
            ),
            ElevatedButton(
              onPressed: () async {
                try {
                  final imageInfo = await _fluUvcPlugin.getImage();

                  imageData = imageInfo['jpeg'] as Uint8List;
                  setState(() {});
                } catch (e) {
                  debugPrint("getImage error: $e");
                }
              },
              child: Text('Get Image'),
            ),
            Text(_code),
            if (imageData != null)
              Image.memory(imageData!, width: 320, height: 240),
            ...codes.map((e) => Text(e.text ?? "")),
          ],
        ),
      ),
    );
  }

  void startLoopingScan() {
    if (_loopTimer != null) {
      return;
    }

    _fluUvcPlugin.startScan();
    _isScanning = true;

    _loopTimer = Timer.periodic(Duration(seconds: 4), (timer) async {
      if (_isScanning) {
        await _fluUvcPlugin.stopScan();
        setState(() {
          _testCount++;
        });
        debugPrint("_testCount==$_testCount");
      } else {
        await _fluUvcPlugin.startScan();
      }
      _isScanning = !_isScanning;
    });
  }

  void stopLoopingScan() {
    _loopTimer?.cancel();
    _loopTimer = null;
    _isScanning = false;
  }

  List<Code> codes = [];

  Uint8List? imageData;
  void _getImage() async {
    try {
      final imageInfo = await _fluUvcPlugin.getImage();

      final width = imageInfo['width'] as int;
      final height = imageInfo['height'] as int;
      final rgbData = imageInfo['data'] as Uint8List;
      // imageData = imageInfo['jpeg'] as Uint8List;
      // 创建 RGBA 数据
      final rgbaData = Uint8List(width * height * 4);

      final code = Zxing().readBarcode(
        rgbData,
        DecodeParams(
          imageFormat: ImageFormat.rgb,
          width: int.tryParse("${imageInfo['width']}") ?? 0,
          height: int.tryParse("${imageInfo['height']}") ?? 0,
        ),
      );
      print("code:${code.text} error:${code.error}");
      if ((code.text ?? "").isNotEmpty) {
        for (var element in codes) {
          if (element.text == code.text) {
            return;
          }
        }
        codes.add(code);
      }
      setState(() {});
    } catch (e) {
      debugPrint("getImage error: $e");
    }
  }
}

///code:6972205226407
