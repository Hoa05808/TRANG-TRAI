import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/models.dart';
import '../core/session_store.dart';
import '../theme/scada_theme.dart';
import '../widgets/common.dart';
import '../widgets/paged_list.dart';

/// 06 · Lịch sử: ai điều khiển, thiết bị nào chạy, thời gian và kết quả.
class HistoryScreen extends StatelessWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final api = context.read<SessionStore>().api;
    return DefaultTabController(
      length: 2,
      child: Column(children: [
        const TabBar(tabs: [Tab(text: 'Lệnh điều khiển'), Tab(text: 'Thiết bị bật/tắt')]),
        Expanded(
          child: TabBarView(children: [
            PagedList<FarmCommand>(
              load: (page) async => PageResult.fromJson(
                  await api.get('/api/history/commands', query: {'page': page, 'size': 30}) as Map<String, dynamic>,
                  FarmCommand.fromJson),
              itemBuilder: (c) => _CommandTile(c),
              emptyText: 'Chưa có lệnh điều khiển nào trong 30 ngày qua.',
            ),
            PagedList<DeviceEvent>(
              load: (page) async => PageResult.fromJson(
                  await api.get('/api/history/device-events', query: {'page': page, 'size': 30})
                      as Map<String, dynamic>,
                  DeviceEvent.fromJson),
              itemBuilder: (e) => _EventTile(e),
              emptyText: 'Chưa có thay đổi trạng thái thiết bị trong 7 ngày qua.',
            ),
          ]),
        ),
      ]),
    );
  }
}

Color _commandColor(String status) => switch (status) {
      'EXECUTED' => Scada.running,
      'SENT' || 'ACCEPTED' || 'PENDING' => Scada.info,
      'REJECTED' || 'BLOCKED' || 'EXPIRED' || 'TIMEOUT' => Scada.warning,
      _ => Scada.critical,
    };

class _CommandTile extends StatelessWidget {
  final FarmCommand c;
  const _CommandTile(this.c);

  @override
  Widget build(BuildContext context) {
    final color = _commandColor(c.status);
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: color.withValues(alpha: 0.15),
          child: Icon(c.success ? Icons.check : Icons.send, color: color, size: 20),
        ),
        title: Text(c.typeName, style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Text([
          '${c.requestedBy} · ${fmtDateTime(c.createdAt)}',
          if (c.message != null) c.message!,
        ].join('\n')),
        isThreeLine: c.message != null,
        trailing: StatusChip(label: c.statusLabel, color: color),
      ),
    );
  }
}

class _EventTile extends StatelessWidget {
  final DeviceEvent e;
  const _EventTile(this.e);

  @override
  Widget build(BuildContext context) {
    final color = e.turnedOn ? Scada.running : Scada.off;
    final verb = e.device == 'VALVE_1' ? (e.turnedOn ? 'MỞ' : 'ĐÓNG') : (e.turnedOn ? 'BẬT' : 'TẮT');
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: StatusLamp(color: color, size: 14),
        title: Text('${e.deviceName} → $verb', style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text(e.source == 'REMOTE'
            ? 'Điều khiển từ app bởi ${e.username ?? '?'}'
            : 'PLC (tự động / tại tủ / HMI)'),
        trailing: Text(fmtDateTime(e.occurredAt), style: Theme.of(context).textTheme.bodySmall),
      ),
    );
  }
}
