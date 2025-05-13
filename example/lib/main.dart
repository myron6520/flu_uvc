import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flu_uvc/flu_uvc.dart';
import 'package:flutter_zxing/flutter_zxing.dart';

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
    Timer.periodic(const Duration(milliseconds: 200), (timer) {
      _getImage();
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion =
          await _fluUvcPlugin.getPlatformVersion() ??
          'Unknown platform version';
    } on PlatformException {
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
                debugPrint("initCamera:${await _fluUvcPlugin.initCamera()}");
              },
              child: Text('Init Camera'),
            ),
            ElevatedButton(
              onPressed: () async {
                debugPrint(
                  "startCapture:${await _fluUvcPlugin.startCapture()}",
                );
              },
              child: Text('Start Capture'),
            ),
            ElevatedButton(
              onPressed: () async {
                debugPrint("stopCapture:${await _fluUvcPlugin.stopCapture()}");
              },
              child: Text('Stop Capture'),
            ),
            ElevatedButton(
              onPressed: () async {
                final image = await _fluUvcPlugin.getImage();
                print("image:${image}");
              },
              child: Text('Get Image'),
            ),
            ElevatedButton(
              onPressed: () async {
                debugPrint(
                  "releaseCamera:${await _fluUvcPlugin.releaseCamera()}",
                );
              },
              child: Text('Release Camera'),
            ),
            ...codes.map((e) => Text(e.text ?? "")),
          ],
        ),
      ),
    );
  }

  List<Code> codes = [];

  void _getImage() async {
    try {
      final imageInfo = await _fluUvcPlugin.getImage();
      final code = Zxing().readBarcode(
        imageInfo['data'],
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
