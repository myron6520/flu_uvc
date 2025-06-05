import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flu_uvc/flu_uvc.dart';
import 'package:flutter_zxing/flutter_zxing.dart';
import 'dart:ui' as ui;

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

  @override
  void initState() {
    super.initState();
    initPlatformState();
    // Timer.periodic(const Duration(milliseconds: 50), (timer) {
    //   _getImage();
    // });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {} on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;
  }

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

            if (imageData != null)
              Image.memory(imageData!, width: 320, height: 240),
            ...codes.map((e) => Text(e.text ?? "")),
          ],
        ),
      ),
    );
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
