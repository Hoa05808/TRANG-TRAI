import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/api_client.dart';
import '../core/farm_store.dart';
import '../core/models.dart';
import '../core/session_store.dart';
import '../theme/scada_theme.dart';
import '../widgets/common.dart';
import 'dashboard_screen.dart' show deviceIcon, deviceStateLabel;

/// Nhóm điều khiển khớp với mã lệnh D203 của PLC.
class _ControlGroup {
  final String title;
  final List<String> devices;
  final String onCommand, offCommand;
  final String onLabel, offLabel;
  const _ControlGroup(this.title, this.devices, this.onCommand, this.offCommand, this.onLabel, this.offLabel);
}

const _groups = [
  _ControlGroup('Máy bơm nước', ['PUMP_1'], 'PUMP_ON', 'PUMP_OFF', 'BẬT', 'TẮT'),
  _ControlGroup('Quạt thông gió', ['FAN_1', 'FAN_2'], 'FAN_ON', 'FAN_OFF', 'BẬT', 'TẮT'),
  _ControlGroup('Đèn chiếu sáng', ['LIGHT_1'], 'LIGHT_ON', 'LIGHT_OFF', 'BẬT', 'TẮT'),
  _ControlGroup('Van cấp nước', ['VALVE_1'], 'VALVE_OPEN', 'VALVE_CLOSE', 'MỞ', 'ĐÓNG'),
];

/// 02 · Điều khiển thiết bị.
class ControlScreen extends StatelessWidget {
  const ControlScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final farm = context.watch<FarmStore>();
    final user = context.watch<SessionStore>().user;
    final d = farm.dashboard;
    if (d == null) return const Center(child: CircularProgressIndicator());

    final String? lockReason = !(user?.role.canControl ?? false)
        ? 'Tài khoản "Chỉ xem" không được điều khiển thiết bị.'
        : d.connection == ConnectionStatus.offline
            ? 'Mất kết nối trang trại – không thể gửi lệnh. PLC vẫn chạy tự động.'
            : d.connection == ConnectionStatus.plcLinkLost
                ? 'Gateway không giao tiếp được với PLC.'
                : d.plcState != 'RUN'
                    ? 'PLC không ở trạng thái RUN.'
                    : !d.remoteEnabled
                        ? 'PLC đang khóa điều khiển từ xa. Chuyển công tắc tại tủ sang REMOTE.'
                        : null;

    return ListView(padding: const EdgeInsets.all(14), children: [
      Panel(
        title: 'Chế độ vận hành',
        icon: Icons.tune,
        child: Wrap(spacing: 10, runSpacing: 10, crossAxisAlignment: WrapCrossAlignment.center, children: [
          StatusChip(label: d.modeLabel, color: d.online ? Scada.accent : Scada.off, icon: Icons.settings_suggest),
          StatusChip(
            label: d.remoteEnabled ? 'REMOTE: CHO PHÉP' : 'REMOTE: KHÓA',
            color: d.remoteEnabled ? Scada.running : Scada.warning,
            icon: d.remoteEnabled ? Icons.lock_open : Icons.lock,
          ),
          Text('Lệnh từ app chỉ là yêu cầu; PLC quyết định theo liên động an toàn.',
              style: Theme.of(context).textTheme.bodySmall),
        ]),
      ),
      if (lockReason != null) ...[
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: Scada.warning.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: Scada.warning.withValues(alpha: 0.5)),
          ),
          child: Row(children: [
            const Icon(Icons.lock_outline, color: Scada.warning),
            const SizedBox(width: 10),
            Expanded(child: Text(lockReason, style: const TextStyle(color: Scada.warning, fontWeight: FontWeight.w600))),
          ]),
        ),
      ],
      const SizedBox(height: 12),
      ResponsiveGrid(minItemWidth: 300, children: [
        for (final g in _groups) _ControlCard(group: g, dashboard: d, locked: lockReason != null),
      ]),
    ]);
  }
}

class _ControlCard extends StatelessWidget {
  final _ControlGroup group;
  final Dashboard dashboard;
  final bool locked;
  const _ControlCard({required this.group, required this.dashboard, required this.locked});

  Future<void> _send(BuildContext context, String command, String verb) async {
    final farm = context.read<FarmStore>();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('${verb[0].toUpperCase()}${verb.substring(1)} ${group.title.toLowerCase()}?'),
        content: const Text('Lệnh sẽ được gửi tới PLC qua Gateway. PLC có thể từ chối nếu không thỏa liên động an toàn.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Hủy')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(verb.toUpperCase())),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    try {
      await farm.sendCommand(command);
    } on ApiException catch (e) {
      if (context.mounted) showError(context, e.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    final farm = context.watch<FarmStore>();
    final devices = group.devices.map(dashboard.device).whereType<DeviceView>().toList();
    final anyOn = devices.any((x) => x.state == DeviceState.on);
    final unknown = devices.any((x) => x.state == DeviceState.unknown);
    final pending = farm.pendingFor([group.onCommand, group.offCommand]);
    final busy = pending != null || farm.anyCommandInFlight;
    final color = unknown ? Scada.off : (anyOn ? Scada.running : Scada.off);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            Icon(deviceIcon(group.devices.first), color: color, size: 34),
            const SizedBox(width: 12),
            Expanded(
              child: Text(group.title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
            ),
            StatusLamp(color: color, size: 16, glow: anyOn && !unknown),
          ]),
          const SizedBox(height: 10),
          for (final dev in devices)
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Row(children: [
                Expanded(child: Text('${dev.name} (${dev.plcOutput})', style: Theme.of(context).textTheme.bodySmall)),
                Text(deviceStateLabel(dev),
                    style: TextStyle(color: Scada.deviceColor(dev.state), fontWeight: FontWeight.w800, fontSize: 12)),
                if (dev.onSince != null)
                  Text('  ${fmtDuration(DateTime.now().difference(dev.onSince!))}',
                      style: Theme.of(context).textTheme.bodySmall),
              ]),
            ),
          const SizedBox(height: 8),
          if (pending != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(children: [
                const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                const SizedBox(width: 8),
                Text('${pending.typeName}: ${pending.statusLabel}', style: const TextStyle(color: Scada.accent)),
              ]),
            ),
          Row(children: [
            Expanded(
              child: FilledButton(
                style: FilledButton.styleFrom(backgroundColor: Scada.running, foregroundColor: Colors.black),
                onPressed: locked || busy ? null : () => _send(context, group.onCommand, group.onLabel.toLowerCase()),
                child: Text(group.onLabel, style: const TextStyle(fontWeight: FontWeight.w800)),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: OutlinedButton(
                onPressed: locked || busy ? null : () => _send(context, group.offCommand, group.offLabel.toLowerCase()),
                child: Text(group.offLabel, style: const TextStyle(fontWeight: FontWeight.w800)),
              ),
            ),
          ]),
        ]),
      ),
    );
  }
}
