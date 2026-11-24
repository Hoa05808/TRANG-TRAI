import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

/// Lỗi trả về từ backend: {"code": "...", "message": "..."}.
class ApiException implements Exception {
  final int status;
  final String code;
  final String message;

  ApiException(this.status, this.code, this.message);

  bool get isNetwork => status == 0;

  @override
  String toString() => message;
}

class Tokens {
  final String access;
  final String refresh;
  final DateTime accessExpiresAt;
  Tokens(this.access, this.refresh, this.accessExpiresAt);
}

/// HTTP client gắn token, tự làm mới access token khi hết hạn (refresh token xoay vòng).
class ApiClient {
  String baseUrl;
  Tokens? tokens;

  /// Gọi khi có cặp token mới (để lưu lại).
  void Function(Tokens tokens)? onTokensChanged;

  /// Gọi khi refresh token không còn dùng được -> phải đăng nhập lại.
  void Function()? onSessionExpired;

  final http.Client _http = http.Client();
  Future<bool>? _refreshing;

  static const _timeout = Duration(seconds: 15);

  ApiClient(this.baseUrl);

  Uri _uri(String path, [Map<String, dynamic>? query]) {
    final q = query == null
        ? null
        : {
            for (final e in query.entries)
              if (e.value != null) e.key: e.value is DateTime ? (e.value as DateTime).toUtc().toIso8601String() : '${e.value}'
          };
    return Uri.parse('${baseUrl.replaceAll(RegExp(r'/+$'), '')}$path').replace(queryParameters: q);
  }

  Future<dynamic> get(String path, {Map<String, dynamic>? query}) => _send('GET', path, query: query);
  Future<dynamic> post(String path, [Object? body]) => _send('POST', path, body: body);
  Future<dynamic> put(String path, [Object? body]) => _send('PUT', path, body: body);
  Future<dynamic> patch(String path, [Object? body]) => _send('PATCH', path, body: body);

  Future<dynamic> _send(String method, String path,
      {Map<String, dynamic>? query, Object? body, bool auth = true, bool retried = false}) async {
    if (auth) await ensureFreshToken();
    final req = http.Request(method, _uri(path, query));
    req.headers['Accept'] = 'application/json';
    if (body != null) {
      req.headers['Content-Type'] = 'application/json; charset=utf-8';
      req.body = jsonEncode(body);
    }
    if (auth && tokens != null) req.headers['Authorization'] = 'Bearer ${tokens!.access}';

    http.Response res;
    try {
      res = await http.Response.fromStream(await _http.send(req).timeout(_timeout));
    } on TimeoutException {
      throw ApiException(0, 'TIMEOUT', 'Máy chủ không phản hồi. Kiểm tra kết nối mạng.');
    } catch (_) {
      throw ApiException(0, 'NETWORK', 'Không kết nối được máy chủ ($baseUrl).');
    }

    if (res.statusCode == 401 && auth && !retried && tokens != null) {
      if (await _refresh()) {
        return _send(method, path, query: query, body: body, auth: auth, retried: true);
      }
    }
    final text = utf8.decode(res.bodyBytes);
    final data = text.isEmpty ? null : jsonDecode(text);
    if (res.statusCode >= 200 && res.statusCode < 300) return data;

    if (data is Map<String, dynamic>) {
      throw ApiException(res.statusCode, data['code'] as String? ?? 'HTTP_${res.statusCode}',
          data['message'] as String? ?? 'Lỗi ${res.statusCode}');
    }
    throw ApiException(res.statusCode, 'HTTP_${res.statusCode}', 'Lỗi máy chủ (${res.statusCode})');
  }

  /// Đăng nhập, trả về JSON {accessToken, refreshToken, expiresInSeconds, user}.
  Future<Map<String, dynamic>> login(String username, String password) async {
    final data = await _send('POST', '/api/auth/login',
        body: {'username': username, 'password': password}, auth: false) as Map<String, dynamic>;
    _setTokens(data);
    return data;
  }

  Future<void> logout() async {
    final t = tokens;
    tokens = null;
    if (t != null) {
      try {
        await _send('POST', '/api/auth/logout', body: {'refreshToken': t.refresh}, auth: false);
      } catch (_) {
        // không quan trọng nếu máy chủ không nhận được
      }
    }
  }

  /// Làm mới token nếu sắp hết hạn (dùng trước khi gọi API hoặc mở WebSocket).
  Future<void> ensureFreshToken() async {
    final t = tokens;
    if (t == null) return;
    if (t.accessExpiresAt.isBefore(DateTime.now().add(const Duration(seconds: 60)))) {
      await _refresh();
    }
  }

  Future<bool> _refresh() {
    return _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);
  }

  Future<bool> _doRefresh() async {
    final t = tokens;
    if (t == null) return false;
    try {
      final data = await _send('POST', '/api/auth/refresh', body: {'refreshToken': t.refresh}, auth: false)
          as Map<String, dynamic>;
      _setTokens(data);
      return true;
    } on ApiException catch (e) {
      if (!e.isNetwork) {
        tokens = null;
        onSessionExpired?.call();
      }
      return false;
    }
  }

  void _setTokens(Map<String, dynamic> data) {
    tokens = Tokens(
      data['accessToken'] as String,
      data['refreshToken'] as String,
      DateTime.now().add(Duration(seconds: (data['expiresInSeconds'] as num).toInt())),
    );
    onTokensChanged?.call(tokens!);
  }

  /// URL WebSocket STOMP tương ứng với baseUrl (http -> ws, https -> wss).
  String get webSocketUrl {
    final u = Uri.parse(baseUrl);
    return u.replace(scheme: u.scheme == 'https' ? 'wss' : 'ws', path: '/ws').toString();
  }
}
