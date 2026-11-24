import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_client.dart';
import 'models.dart';
import 'push_service.dart';

/// Địa chỉ máy chủ mặc định:
///  - Web/Windows: backend chạy cùng máy -> localhost
///  - Android emulator: 10.0.2.2 là máy tính chủ
String defaultServerUrl() {
  if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) return 'http://10.0.2.2:8080';
  return 'http://localhost:8080';
}

/// Phiên đăng nhập + tùy chọn của app (địa chỉ máy chủ, giao diện sáng/tối).
class SessionStore extends ChangeNotifier {
  static const _kServer = 'server_url';
  static const _kTheme = 'theme_mode';
  static const _kAccess = 'access_token';
  static const _kRefresh = 'refresh_token';
  static const _kExpiry = 'access_expiry';
  static const _kUser = 'user_json';

  final FlutterSecureStorage _secure = const FlutterSecureStorage();
  late final SharedPreferences _prefs;
  late final ApiClient api;

  AppUser? user;
  ThemeMode themeMode = ThemeMode.dark;
  bool ready = false;

  bool get loggedIn => user != null && api.tokens != null;
  String get serverUrl => api.baseUrl;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    api = ApiClient(_prefs.getString(_kServer) ?? defaultServerUrl());
    themeMode = _prefs.getString(_kTheme) == 'light' ? ThemeMode.light : ThemeMode.dark;
    api.onTokensChanged = _saveTokens;
    api.onSessionExpired = () {
      user = null;
      _clearTokens();
      notifyListeners();
    };

    try {
      final access = await _secure.read(key: _kAccess);
      final refresh = await _secure.read(key: _kRefresh);
      final expiry = await _secure.read(key: _kExpiry);
      if (access != null && refresh != null && expiry != null) {
        api.tokens = Tokens(access, refresh, DateTime.parse(expiry));
        final me = await api.get('/api/me') as Map<String, dynamic>;
        user = AppUser.fromJson(me);
      }
    } on ApiException catch (e) {
      // Mất mạng khi mở app: vẫn giữ phiên, chỉ đăng xuất khi máy chủ từ chối token
      if (!e.isNetwork) {
        api.tokens = null;
        await _clearTokens();
      } else {
        final cached = _prefs.getString(_kUser);
        if (cached != null) user = _decodeUser(cached);
      }
    } catch (_) {
      api.tokens = null;
    }
    ready = true;
    notifyListeners();
  }

  Future<void> login(String username, String password) async {
    final data = await api.login(username, password);
    user = AppUser.fromJson(data['user'] as Map<String, dynamic>);
    await _prefs.setString(_kUser, _encodeUser(user!));
    notifyListeners();
  }

  Future<void> logout() async {
    await PushService.instance.unregister(api);
    await api.logout();
    user = null;
    await _clearTokens();
    notifyListeners();
  }

  Future<void> setServerUrl(String url) async {
    api.baseUrl = url.trim();
    await _prefs.setString(_kServer, api.baseUrl);
    notifyListeners();
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    themeMode = mode;
    await _prefs.setString(_kTheme, mode == ThemeMode.light ? 'light' : 'dark');
    notifyListeners();
  }

  void _saveTokens(Tokens t) {
    _secure.write(key: _kAccess, value: t.access);
    _secure.write(key: _kRefresh, value: t.refresh);
    _secure.write(key: _kExpiry, value: t.accessExpiresAt.toIso8601String());
  }

  Future<void> _clearTokens() async {
    await _secure.delete(key: _kAccess);
    await _secure.delete(key: _kRefresh);
    await _secure.delete(key: _kExpiry);
  }

  static String _encodeUser(AppUser u) => '${u.id}|${u.username}|${u.role.api}|${u.fullName ?? ''}';

  static AppUser? _decodeUser(String s) {
    final p = s.split('|');
    if (p.length < 4) return null;
    return AppUser(id: int.parse(p[0]), username: p[1], role: roleFrom(p[2]), fullName: p.sublist(3).join('|'), enabled: true);
  }
}
