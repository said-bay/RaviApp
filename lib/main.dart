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
    SystemChrome.setSystemUIOverlayStyle(
      const SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: Brightness.light,
        systemNavigationBarColor: Colors.black,
        systemNavigationBarIconBrightness: Brightness.light,
      ),
    );

    return MaterialApp(
      title: 'Duvar Kağıdı Değiştirici',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF00E5FF),
          background: Colors.black,
          surface: Colors.black,
          onSurface: Colors.white,
        ),
        scaffoldBackgroundColor: Colors.black,
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

class _WallpaperChangerScreenState extends State<WallpaperChangerScreen> with SingleTickerProviderStateMixin {
  static const platform = MethodChannel('com.example.wallpaper_changer/service');
  static const _wallpaperChannel = MethodChannel('com.example.wallpaper_changer/wallpaper');
  static const _serviceChannel = MethodChannel('com.example.wallpaper_changer/service');
  bool isServiceRunning = false;
  late AnimationController _animationController;
  late Animation<double> _animation;

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
    _animationController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    _animation = CurvedAnimation(
      parent: _animationController,
      curve: Curves.easeInOut,
    );
    checkServiceStatus();
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  Future<void> checkServiceStatus() async {
    try {
      final bool status = await platform.invokeMethod('isServiceRunning');
      setState(() {
        isServiceRunning = status;
        if (status) {
          _animationController.forward();
        } else {
          _animationController.reverse();
        }
      });
    } on PlatformException catch (e) {
      debugPrint("Error checking service status: ${e.message}");
    }
  }

  Future<void> toggleService() async {
    try {
      if (isServiceRunning) {
        await _serviceChannel.invokeMethod('stopService');
        _animationController.reverse();
      } else {
        await _serviceChannel.invokeMethod('startService');
        _animationController.forward();
      }
      await checkServiceStatus();
    } on PlatformException catch (e) {
      debugPrint("Error toggling service: ${e.message}");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Colors.black,
              Colors.black,
              Theme.of(context).colorScheme.primary.withOpacity(0.1),
            ],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // Logo ve başlık
                const Icon(
                  Icons.auto_awesome,
                  size: 64,
                  color: Color(0xFF00E5FF),
                ),
                const SizedBox(height: 16),
                Text(
                  'Duvar Kağıdı Değiştirici',
                  style: TextStyle(
                    fontSize: 32,
                    fontWeight: FontWeight.bold,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Kilit ekranı duvar kağıdını otomatik değiştirme',
                  style: TextStyle(
                    fontSize: 16,
                    color: Colors.white70,
                  ),
                ),
                const SizedBox(height: 48),
                
                // Animasyonlu buton
                GestureDetector(
                  onTap: toggleService,
                  child: AnimatedBuilder(
                    animation: _animation,
                    builder: (context, child) {
                      return Container(
                        width: 80,
                        height: 80,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: LinearGradient(
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                            colors: [
                              Theme.of(context).colorScheme.primary,
                              Theme.of(context).colorScheme.primary.withOpacity(0.7),
                            ],
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: Theme.of(context).colorScheme.primary.withOpacity(0.3),
                              blurRadius: 20,
                              spreadRadius: _animation.value * 5,
                            ),
                          ],
                        ),
                        child: Icon(
                          isServiceRunning ? Icons.pause : Icons.play_arrow,
                          color: Colors.black,
                          size: 32,
                        ),
                      );
                    },
                  ),
                ),
                const SizedBox(height: 24),
                
                // Durum metni
                Text(
                  isServiceRunning ? 'Aktif' : 'Devre Dışı',
                  style: TextStyle(
                    fontSize: 18,
                    color: isServiceRunning 
                      ? Theme.of(context).colorScheme.primary 
                      : Colors.white54,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                
                // Alt bilgi
                const Padding(
                  padding: EdgeInsets.only(top: 48, left: 32, right: 32),
                  child: Text(
                    'Ekran her açıldığında rastgele bir duvar kağıdı gösterilecektir.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 14,
                      color: Colors.white38,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
