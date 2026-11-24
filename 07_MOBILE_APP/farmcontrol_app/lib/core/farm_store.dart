import 'dart:async';

import 'package:flutter/foundation.dart';

import 'api_client.dart';
import 'models.dart';
import 'realtime.dart';

/// Trạng thái trang trại dùng chung cho các màn hình.
/// Nguồn chính: WebSocket. Khi WebSocket mất -> tự hỏi REST mỗi 10 giây.
class FarmStore extends ChangeNotifier {
  final ApiClient api;
  late final Realtime _realtime;
  Timer? _poll;

  Dashboard? dashboard;
  List<FarmAlert> activeAlerts = [];
  bool realtimeConnected = false;
  ApiException? serverError; // không kết nối được backend
  DateTime? lastServerContact;

  /// Lệnh đang chờ theo từng loại lệnh (để hiện tiến trình trên nút điều khiển)
  final Map<int, FarmCommand> commands = {};

  /// Phát ra khi một lệnh kết thúc (thành công hay thất bại) để hiện thông báo
  final _finished = StreamController<FarmCommand>.broadcast();
  Stream<FarmCommand> get finishedCommands => _finished.stream;

  /// Cảnh báo mới (để hiện snackbar)
  final _newAlerts = StreamController<FarmAlert>.broadcast();
  Stream<FarmAlert> get newAlerts => _newAlerts.stream;

  FarmStore(this.api) {
    _realtime = Realtime(
      api: api,
      onDashboard: (j) {
        dashboard = Dashboard.fromJson(j);
        lastServerContact = DateTime.now();
        serverError = null;
        notifyListeners();
      },
      onAlert: _onAlert,
      onCommand: (j) => _onCommand(FarmCommand.fromJson(j)),
      onConnectionChanged: (c) {
        realtimeConnected = c;
        if (c) refresh(); // đồng bộ lại sau khi kết nối lại
        notifyListeners();
      },
    );
  }

  void start() {
    _realtime.start();
    refresh();
    _poll?.cancel();
    _poll = Timer.periodic(const Duration(seconds: 10), (_) {
      if (!realtimeConnected) refresh();
    });
  }

  void stop() {
    _poll?.cancel();
    _realtime.stop();
    dashboard = null;
    activeAlerts = [];
    commands.clear();
  }

  Future<void> refresh() async {
    try {
      final results = await Future.wait([api.get('/api/dashboard'), api.get('/api/alerts/active')]);
      dashboard = Dashboard.fromJson(results[0] as Map<String, dynamic>);
      activeAlerts = (results[1] as List).map((e) => FarmAlert.fromJson(e as Map<String, dynamic>)).toList();
      serverError = null;
      lastServerContact = DateTime.now();
    } on ApiException catch (e) {
      serverError = e;
    }
    notifyListeners();
  }

  void _onAlert(Map<String, dynamic> j) {
    final a = FarmAlert.fromJson(j);
    final existed = activeAlerts.any((x) => x.id == a.id);
    activeAlerts.removeWhere((x) => x.id == a.id);
    if (a.active) {
      activeAlerts.insert(0, a);
      if (!existed) _newAlerts.add(a);
    }
    notifyListeners();
  }

  void _onCommand(FarmCommand c) {
    final prev = commands[c.id];
    commands[c.id] = c;
    if (!c.inFlight && (prev == null || prev.inFlight)) _finished.add(c);
    notifyListeners();
  }

  /// Lệnh đang chạy gần nhất cho một loại lệnh (ví dụ PUMP_ON), nếu có.
  FarmCommand? pendingFor(Iterable<String> types) {
    FarmCommand? found;
    for (final c in commands.values) {
      if (c.inFlight && types.contains(c.type) && (found == null || c.id > found.id)) found = c;
    }
    return found;
  }

  bool get anyCommandInFlight => commands.values.any((c) => c.inFlight);

  /// Gửi lệnh. Kết quả cuối cùng đến qua WebSocket; nếu WebSocket mất thì tự hỏi lại.
  Future<FarmCommand> sendCommand(String type) async {
    final c = FarmCommand.fromJson(await api.post('/api/commands', {'type': type}) as Map<String, dynamic>);
    _onCommand(c);
    _trackUntilDone(c.id);
    return c;
  }

  Future<void> _trackUntilDone(int id) async {
    for (var i = 0; i < 25; i++) {
      await Future<void>.delayed(const Duration(seconds: 1));
      final current = commands[id];
      if (current == null || !current.inFlight) return;
      if (realtimeConnected && i < 3) continue; // ưu tiên WebSocket
      try {
        _onCommand(FarmCommand.fromJson(await api.get('/api/commands/$id') as Map<String, dynamic>));
      } catch (_) {}
    }
  }

  Future<void> acknowledgeAlert(int id) async {
    _onAlert(await api.post('/api/alerts/$id/ack') as Map<String, dynamic>);
  }

  @override
  void dispose() {
    stop();
    _finished.close();
    _newAlerts.close();
    super.dispose();
  }
}
