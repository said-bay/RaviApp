import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Duvar Kağıdı Değiştirici',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const WallpaperChangerScreen(),
    );
  }
}

class WallpaperChangerScreen extends StatefulWidget {
  const WallpaperChangerScreen({super.key});

  @override
  State<WallpaperChangerScreen> createState() => _WallpaperChangerScreenState();
}

class _WallpaperChangerScreenState extends State<WallpaperChangerScreen> {
  StreamSubscription? _screenStateSubscription;
  static const _screenStateChannel = EventChannel('com.example.wallpaper_changer/screen_state');
  static const _wallpaperChannel = MethodChannel('com.example.wallpaper_changer/wallpaper');
  static const _serviceChannel = MethodChannel('com.example.wallpaper_changer/service');
  bool isServiceRunning = false;

  final List<String> wallpapers = [
    'assets/wallpapers/Ravi-1.png',
    'assets/wallpapers/Ravi-2.png',
    'assets/wallpapers/Ravi-3.png',
    'assets/wallpapers/Ravi-4.png',
    'assets/wallpapers/Ravi-5.png',
  ];

  @override
  void initState() {
    super.initState();
    _initScreenStateListener();
    _checkServiceStatus();
  }

  @override
  void dispose() {
    _screenStateSubscription?.cancel();
    super.dispose();
  }

  Future<void> _initScreenStateListener() async {
    _screenStateSubscription = _screenStateChannel
        .receiveBroadcastStream()
        .listen((dynamic event) {
      debugPrint('Screen state changed: $event');
    });
  }

  Future<void> _toggleLockScreenWallpaper() async {
    try {
      if (isServiceRunning) {
        await _serviceChannel.invokeMethod('stopService');
        setState(() {
          isServiceRunning = false;
        });
      } else {
        await _serviceChannel.invokeMethod('startService');
        setState(() {
          isServiceRunning = true;
        });
      }
    } on PlatformException catch (e) {
      debugPrint("Error toggling service: ${e.message}");
    }
  }

  Future<void> _checkServiceStatus() async {
    try {
      final bool running = await _serviceChannel.invokeMethod('isServiceRunning');
      setState(() {
        isServiceRunning = running;
      });
    } on PlatformException catch (e) {
      debugPrint("Error checking service status: ${e.message}");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Duvar Kağıdı Değiştirici'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              'Kilit ekranı duvar kağıdını otomatik değiştirme',
              style: Theme.of(context).textTheme.headlineSmall,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: _toggleLockScreenWallpaper,
              icon: Icon(isServiceRunning ? Icons.stop : Icons.play_arrow),
              label: Text(isServiceRunning ? 'Devre Dışı Bırak' : 'Aktifleştir'),
              style: ElevatedButton.styleFrom(
                backgroundColor: isServiceRunning ? Colors.red : Colors.green,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
