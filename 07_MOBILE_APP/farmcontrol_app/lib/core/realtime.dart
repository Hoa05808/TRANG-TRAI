import 'dart:async';
import 'dart:convert';

import 'package:stomp_dart_client/stomp_dart_client.dart';

import 'api_client.dart';

/// Kết nối STOMP tới backend: /topic/dashboard, /topic/alerts, /topic/commands.
/// Tự kết nối lại (lấy token mới mỗi lần) khi mạng chập chờn.
class Realtime {
  final ApiClient api;
  final void Function(Map<String, dynamic>) onDashboard;
  final void Function(Map<String, dynamic>) onAlert;
  final void Function(Map<String, dynamic>) onCommand;
  final void Function(bool connected) onConnectionChanged;

  StompClient? _client;
  Timer? _retry;
  bool _stopped = true;
  bool connected = false;

  Realtime({
    required this.api,
    required this.onDashboard,
    required this.onAlert,
    required this.onCommand,
    required this.onConnectionChanged,
  });

  void start() {
    _stopped = false;
    _connect();
  }

  void stop() {
    _stopped = true;
    _retry?.cancel();
    _client?.deactivate();
    _client = null;
    _setConnected(false);
  }

  Future<void> _connect() async {
    if (_stopped) return;
    try {
      await api.ensureFreshToken();
    } catch (_) {}
    final token = api.tokens?.access;
    if (token == null || _stopped) return;

    _client?.deactivate();
    final client = StompClient(
      config: StompConfig(
        url: api.webSocketUrl,
        stompConnectHeaders: {'Authorization': 'Bearer $token'},
        reconnectDelay: Duration.zero, // tự quản lý kết nối lại để dùng token mới
        heartbeatIncoming: const Duration(seconds: 20),
        heartbeatOutgoing: const Duration(seconds: 20),
        connectionTimeout: const Duration(seconds: 10),
        onConnect: (frame) {
          _setConnected(true);
          _subscribe('/topic/dashboard', onDashboard);
          _subscribe('/topic/alerts', onAlert);
          _subscribe('/topic/commands', onCommand);
        },
        onDisconnect: (_) => _scheduleRetry(),
        onWebSocketDone: _scheduleRetry,
        onWebSocketError: (_) => _scheduleRetry(),
        onStompError: (_) => _scheduleRetry(),
      ),
    );
    _client = client;
    client.activate();
  }

  void _subscribe(String destination, void Function(Map<String, dynamic>) handler) {
    _client?.subscribe(
      destination: destination,
      callback: (frame) {
        final body = frame.body;
        if (body == null || body.isEmpty) return;
        try {
          handler(jsonDecode(body) as Map<String, dynamic>);
        } catch (_) {
          // bỏ qua gói lỗi định dạng
        }
      },
    );
  }

  void _scheduleRetry() {
    _setConnected(false);
    if (_stopped || (_retry?.isActive ?? false)) return;
    _retry = Timer(const Duration(seconds: 5), _connect);
  }

  void _setConnected(bool value) {
    if (connected == value) return;
    connected = value;
    onConnectionChanged(value);
  }
}
