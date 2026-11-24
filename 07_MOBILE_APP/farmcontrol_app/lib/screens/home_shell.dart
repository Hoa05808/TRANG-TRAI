import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/farm_store.dart';
import '../core/models.dart';
import '../core/push_service.dart';
import '../core/session_store.dart';
import '../theme/scada_theme.dart';
import '../widgets/common.dart';
import 'alerts_screen.dart';
import 'charts_screen.dart';
import 'control_screen.dart';
import 'dashboard_screen.dart';
import 'history_screen.dart';
import 'sensors_screen.dart';
import 'settings_screen.dart';

class _Dest {
  final String label;
  final IconData icon;
  final IconData selected;
  final Widget page;
  const _Dest(this.label, this.icon, this.selected, this.page);
}

const _destinations = [
  _Dest('Tổng quan', Icons.dashboard_outlined, Icons.dashboard, DashboardScreen()),
  _Dest('Điều khiển', Icons.toggle_off_outlined, Icons.toggle_on, ControlScreen()),
  _Dest('Cảm biến', Icons.sensors_outlined, Icons.sensors, SensorsScreen()),
  _Dest('Biểu đồ', Icons.show_chart_outlined, Icons.show_chart, ChartsScreen()),
  _Dest('Cảnh báo', Icons.notifications_outlined, Icons.notifications, AlertsScreen()),
  _Dest('Lịch sử', Icons.history_outlined, Icons.history, HistoryScreen()),
  _Dest('Cài đặt', Icons.settings_outlined, Icons.settings, SettingsScreen()),
];

/// Khung chính: màn hình rộng dùng thanh bên (đủ 7 mục),
/// điện thoại dùng thanh dưới 5 mục + Lịch sử/Cài đặt trên thanh tiêu đề.
class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;
  final List<StreamSubscription<dynamic>> _subs = [];

  @override
  void initState() {
    super.initState();
    final farm = context.read<FarmStore>();
    _subs.add(farm.finishedCommands.listen(_onCommandFinished));
    _subs.add(farm.newAlerts.listen(_onNewAlert));
    // Thông báo đẩy: đăng ký token và mở màn Cảnh báo khi bấm vào thông báo
    PushService.instance.register(context.read<SessionStore>().api);
    PushService.instance.openedAlert.addListener(_onPushOpened);
    if (PushService.instance.openedAlert.value != null) _onPushOpened();
  }

  void _onPushOpened() {
    if (!mounted || PushService.instance.openedAlert.value == null) return;
    PushService.instance.openedAlert.value = null;
    setState(() => _index = 4);
    context.read<FarmStore>().refresh();
  }

  @override
  void dispose() {
    for (final s in _subs) {
      s.cancel();
    }
    PushService.instance.openedAlert.removeListener(_onPushOpened);
    super.dispose();
  }

  void _onCommandFinished(FarmCommand c) {
    if (!mounted) return;
    final ok = c.success;
    showInfo(context, '${c.typeName}: ${c.statusLabel}${!ok && c.message != null ? ' – ${c.message}' : ''}',
        color: ok ? Scada.running.withValues(alpha: 0.9) : Scada.critical);
  }

  void _onNewAlert(FarmAlert a) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      backgroundColor: Scada.severityColor(a.severity),
      // Khẩn cấp: giữ đến khi người dùng đóng; cảnh báo thường tự ẩn sau 8 giây
      persist: a.severity == Severity.critical,
      duration: const Duration(seconds: 8),
      showCloseIcon: true,
      closeIconColor: Colors.black,
      content: Text('${a.title}: ${a.message ?? ''}', style: const TextStyle(color: Colors.black)),
      action: SnackBarAction(label: 'XEM', textColor: Colors.black, onPressed: () => setState(() => _index = 4)),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final farm = context.watch<FarmStore>();
    final wide = MediaQuery.sizeOf(context).width >= 900;
    final dest = _destinations[_index];
    final alertCount = farm.activeAlerts.where((a) => !a.acknowledged).length;

    Widget icon(int i, bool selected) {
      final d = _destinations[i];
      final ic = Icon(selected ? d.selected : d.icon);
      if (i == 4 && alertCount > 0) return Badge(label: Text('$alertCount'), backgroundColor: Scada.critical, child: ic);
      return ic;
    }

    final body = Column(children: [
      ConnectionBanner(dashboard: farm.dashboard, serverError: farm.serverError?.message),
      Expanded(child: dest.page),
    ]);

    final appBar = AppBar(
      title: Row(children: [
        const Icon(Icons.agriculture, color: Scada.accent),
        const SizedBox(width: 10),
        Text(dest.label.toUpperCase()),
      ]),
      actions: [
        _LinkIndicator(farm: farm),
        if (!wide) ...[
          IconButton(tooltip: 'Lịch sử', icon: const Icon(Icons.history), onPressed: () => setState(() => _index = 5)),
          IconButton(tooltip: 'Cài đặt', icon: const Icon(Icons.settings), onPressed: () => setState(() => _index = 6)),
        ],
        const SizedBox(width: 8),
      ],
    );

    if (wide) {
      return Scaffold(
        appBar: appBar,
        body: Row(children: [
          NavigationRail(
            selectedIndex: _index,
            labelType: NavigationRailLabelType.all,
            onDestinationSelected: (i) => setState(() => _index = i),
            destinations: [
              for (var i = 0; i < _destinations.length; i++)
                NavigationRailDestination(
                    icon: icon(i, false), selectedIcon: icon(i, true), label: Text(_destinations[i].label)),
            ],
          ),
          const VerticalDivider(width: 1),
          Expanded(child: body),
        ]),
      );
    }

    return Scaffold(
      appBar: appBar,
      body: body,
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index < 5 ? _index : 0,
        indicatorColor: _index < 5 ? null : Colors.transparent,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: [
          for (var i = 0; i < 5; i++)
            NavigationDestination(icon: icon(i, false), selectedIcon: icon(i, true), label: _destinations[i].label),
        ],
      ),
    );
  }
}

/// Đèn kết nối trên thanh tiêu đề: máy chủ / trang trại.
class _LinkIndicator extends StatelessWidget {
  final FarmStore farm;
  const _LinkIndicator({required this.farm});

  @override
  Widget build(BuildContext context) {
    final d = farm.dashboard;
    final Color color;
    final String label;
    if (farm.serverError != null) {
      color = Scada.critical;
      label = 'MẤT MÁY CHỦ';
    } else if (d == null) {
      color = Scada.off;
      label = 'ĐANG TẢI';
    } else {
      color = Scada.connectionColor(d.connection);
      label = switch (d.connection) {
        ConnectionStatus.online => 'ONLINE',
        ConnectionStatus.plcLinkLost => 'MẤT PLC',
        ConnectionStatus.offline => 'OFFLINE',
      };
    }
    return Tooltip(
      message: farm.realtimeConnected ? 'Đang nhận dữ liệu thời gian thực' : 'Đang hỏi định kỳ (WebSocket mất)',
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          StatusLamp(color: color, size: 10),
          const SizedBox(width: 6),
          Text(label, style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 12)),
          if (!farm.realtimeConnected) ...[
            const SizedBox(width: 4),
            Icon(Icons.sync_problem, size: 14, color: Theme.of(context).hintColor),
          ],
        ]),
      ),
    );
  }
}
