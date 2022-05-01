
import 'dart:async';

import 'package:flutter/services.dart';

class PathProviderAndroid {
  static const MethodChannel _channel = MethodChannel('path_provider_android');

  static Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
}
