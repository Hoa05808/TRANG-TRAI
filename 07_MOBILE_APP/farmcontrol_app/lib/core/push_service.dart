import 'dart:async';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

import 'api_client.dart';

/// Thông báo đẩy Firebase Cloud Messaging.
///
/// - Chỉ chạy trên Android / iOS và khi app được build kèm cấu hình Firebase
///   (android/app/google-services.json). Thiếu cấu hình thì app vẫn chạy, chỉ không có thông báo đẩy.
/// - Sau khi đăng nhập: xin quyền thông báo, lấy token, gửi lên backend (POST /api/push-tokens).
/// - Khi đăng xuất: gỡ token khỏi backend để không nhận cảnh báo của tài khoản cũ.
/// - Khi chạy nền / tắt app, hệ điều hành tự hiện thông báo; bấm vào sẽ mở màn Cảnh báo.
class PushService {
  PushService._();
  static final PushService instance = PushService._();

  bool available = false;
  String? statusMessage;
  String? _token;
  StreamSubscription<String>? _refreshSub;

  /// Phát id cảnh báo khi người dùng bấm vào thông báo.
  final ValueNotifier<int?> openedAlert = ValueNotifier<int?>(null);

  bool get _supportedPlatform =>
      !kIsWeb && (defaultTargetPlatform == TargetPlatform.android || defaultTargetPlatform == TargetPlatform.iOS);

  Future<void> init() async {
    if (!_supportedPlatform) {
      statusMessage = 'Thông báo đẩy chỉ có trên app Android / iOS';
      return;
    }
    try {
      await Firebase.initializeApp();
      available = true;
    } catch (e) {
      statusMessage = 'App chưa được build kèm cấu hình Firebase (google-services.json)';
      debugPrint('FCM tắt: $e');
      return;
    }
    FirebaseMessaging.onMessageOpenedApp.listen(_onOpened);
    final initial = await FirebaseMessaging.instance.getInitialMessage();
    if (initial != null) _onOpened(initial);
  }

  void _onOpened(RemoteMessage m) {
    openedAlert.value = int.tryParse(m.data['alertId'] ?? '') ?? -1;
  }

  Future<void> register(ApiClient api) async {
    if (!available) return;
    try {
      final settings = await FirebaseMessaging.instance.requestPermission(alert: true, badge: true, sound: true);
      if (settings.authorizationStatus == AuthorizationStatus.denied) {
        statusMessage = 'Bạn đã tắt quyền thông báo cho FarmControl trong cài đặt điện thoại';
        return;
      }
      _token = await FirebaseMessaging.instance.getToken();
      if (_token != null) await _send(api, _token!);
      await _refreshSub?.cancel();
      _refreshSub = FirebaseMessaging.instance.onTokenRefresh.listen((t) {
        _token = t;
        _send(api, t);
      });
      statusMessage = 'Đang nhận thông báo đẩy';
    } catch (e) {
      statusMessage = 'Không đăng ký được thông báo đẩy: $e';
    }
  }

  Future<void> _send(ApiClient api, String token) => api.post('/api/push-tokens', {
        'token': token,
        'platform': defaultTargetPlatform == TargetPlatform.iOS ? 'IOS' : 'ANDROID',
      });

  Future<void> unregister(ApiClient api) async {
    await _refreshSub?.cancel();
    _refreshSub = null;
    final t = _token;
    if (!available || t == null) return;
    try {
      await api.post('/api/push-tokens/unregister', {'token': t});
    } catch (_) {
      // mất mạng khi đăng xuất: backend sẽ tự xóa token khi FCM báo không còn hiệu lực
    }
  }
}
