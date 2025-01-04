import 'dart:async';
import 'dart:math';
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
  bool _isEnabled = false;
  StreamSubscription? _screenStateSubscription;
  static const _screenStateChannel = EventChannel('com.example.wallpaper_changer/screen_state');
  static const _wallpaperChannel = MethodChannel('com.example.wallpaper_changer/wallpaper');

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
      if (event == 'SCREEN_ON' && _isEnabled) {
        _setWallpaper();
      }
    });
  }

  Future<void> _requestPermissions() async {
    var status = await Permission.storage.request();
    if (status.isGranted) {
      setState(() {
        _isEnabled = true;
      });
      _setWallpaper(); // İlk duvar kağıdını ayarla
    }
  }

  Future<void> _setWallpaper() async {
    try {
      final random = Random();
      final wallpaper = wallpapers[random.nextInt(wallpapers.length)];
      
      // Asset'i geçici bir dosyaya kopyala
      final ByteData data = await rootBundle.load(wallpaper);
      final String tempPath = '${(await getTemporaryDirectory()).path}/${wallpaper.split('/').last}';
      final File tempFile = File(tempPath);
      await tempFile.writeAsBytes(data.buffer.asUint8List());

      // Duvar kağıdını ayarla
      final result = await _wallpaperChannel.invokeMethod<bool>(
        'setLockScreenWallpaper',
        {'path': tempFile.path},
      );

      if (result == true && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Duvar kağıdı başarıyla değiştirildi')),
        );
      }
    } catch (e) {
      debugPrint('Duvar kağıdı değiştirme hatası: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Duvar kağıdı değiştirilirken bir hata oluştu')),
        );
      }
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
            ElevatedButton(
              onPressed: _isEnabled ? null : _requestPermissions,
              child: Text(_isEnabled 
                ? 'Duvar Kağıdı Değiştirici Aktif' 
                : 'Kilit Ekranı Duvar Kağıdını Aktifleştir'),
            ),
            if (_isEnabled) ...[
              const SizedBox(height: 20),
              const Text(
                'Duvar kağıdı değiştirici aktif!\nEkranı her açtığınızda duvar kağıdı otomatik değişecek.',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _setWallpaper,
                child: const Text('Duvar Kağıdını Şimdi Değiştir'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
