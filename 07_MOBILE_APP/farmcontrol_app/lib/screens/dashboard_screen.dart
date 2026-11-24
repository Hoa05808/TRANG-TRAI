import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/farm_store.dart';
import '../core/models.dart';
import '../theme/scada_theme.dart';
import '../widgets/common.dart';
import '../widgets/gauges.dart';

/// 01 · Tổng quan trang trại.
class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final farm = context.watch<FarmStore>();
    final d = farm.dashboard;
    if (d == null) {
      return farm.serverError != null
          ? EmptyState(icon: Icons.cloud_off, text: farm.serverError!.message)
          : const Center(child: CircularProgressIndicator());
    }
    final s = d.sensors;
    final stale = !d.dataFresh;
    return RefreshIndicator(
      onRefresh: farm.refresh,
      child: ListView(padding: const EdgeInsets.all(14), children: [
        ResponsiveGrid(minItemWidth: 170, children: [
          _StatusTile(
            label: 'Kết nối',
            value: switch (d.connection) {
              ConnectionStatus.online => 'ONLINE',
              ConnectionStatus.plcLinkLost => 'MẤT PLC',
              ConnectionStatus.offline => 'OFFLINE',
            },
            color: Scada.connectionColor(d.connection),
            sub: 'Cập nhật ${timeAgo(d.lastUpdate)}',
          ),
          _StatusTile(
            label: 'Chế độ',
            value: d.modeLabel,
            color: d.online ? Scada.accent : Scada.off,
            sub: d.remoteEnabled ? 'Cho phép điều khiển từ xa' : 'Khóa điều khiển từ xa',
          ),
          _StatusTile(
            label: 'PLC FX3U',
            value: d.plcState == 'UNKNOWN' ? 'KHÔNG RÕ' : d.plcState,
            color: switch (d.plcState) { 'RUN' => Scada.running, 'ERROR' => Scada.critical, _ => Scada.off },
            sub: d.errorCode != 0 ? 'Mã lỗi ${d.errorCode}' : 'Chạy ${fmtDuration(Duration(seconds: d.plcUptime))}',
          ),
          _StatusTile(
            label: 'Cảnh báo',
            value: '${d.activeAlerts}',
            color: d.activeAlerts == 0 ? Scada.running : Scada.critical,
            sub: d.unacknowledgedAlerts > 0 ? '${d.unacknowledgedAlerts} chưa xác nhận' : 'Đã xác nhận hết',
          ),
        ]),
        const SizedBox(height: 14),
        if (s != null)
          Panel(
            title: 'Môi trường chuồng',
            icon: Icons.thermostat,
            trailing: stale ? const StatusChip(label: 'DỮ LIỆU CŨ', color: Scada.warning) : null,
            child: ResponsiveGrid(minItemWidth: 150, children: [
              RadialGauge(label: 'Nhiệt độ chuồng', value: s.coopTemp, unit: '°C', min: 15, max: 45,
                  warnAt: 32, alarmAt: 35, dimmed: stale),
              RadialGauge(label: 'Độ ẩm không khí', value: s.airHumidity, unit: '%', min: 0, max: 100,
                  warnAt: 80, alarmAt: 85, dimmed: stale),
              TankGauge(label: 'Mực nước bể', percent: s.waterLevel, lowAt: 20, dimmed: stale),
              RadialGauge(label: 'Nhiệt độ nước', value: s.waterTemp, unit: '°C', min: 10, max: 45,
                  warnAt: 33, alarmAt: 36, dimmed: stale),
            ]),
          ),
        const SizedBox(height: 14),
        Panel(
          title: 'Thiết bị',
          icon: Icons.precision_manufacturing_outlined,
          child: ResponsiveGrid(minItemWidth: 150, children: [
            for (final dev in d.devices) _DeviceTile(device: dev),
          ]),
        ),
        const SizedBox(height: 14),
        Panel(
          title: 'Cảnh báo đang diễn ra',
          icon: Icons.warning_amber_rounded,
          child: farm.activeAlerts.isEmpty
              ? const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8),
                  child: Row(children: [
                    StatusLamp(color: Scada.running),
                    SizedBox(width: 10),
                    Text('Hệ thống bình thường'),
                  ]),
                )
              : Column(children: [
                  for (final a in farm.activeAlerts.take(4))
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: StatusLamp(color: Scada.severityColor(a.severity), size: 14),
                      title: Text(a.title, style: const TextStyle(fontWeight: FontWeight.w600)),
                      subtitle: Text(a.message ?? '', maxLines: 2, overflow: TextOverflow.ellipsis),
                      trailing: Text(fmtTime(a.raisedAt), style: Theme.of(context).textTheme.bodySmall),
                    ),
                ]),
        ),
      ]),
    );
  }
}

class _StatusTile extends StatelessWidget {
  final String label, value, sub;
  final Color color;
  const _StatusTile({required this.label, required this.value, required this.color, required this.sub});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(border: Border(left: BorderSide(color: color, width: 4))),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label.toUpperCase(),
              style: TextStyle(fontSize: 11, letterSpacing: 1, color: Theme.of(context).hintColor)),
          const SizedBox(height: 6),
          Row(children: [
            StatusLamp(color: color),
            const SizedBox(width: 8),
            Flexible(
              child: Text(value,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: color)),
            ),
          ]),
          const SizedBox(height: 4),
          Text(sub, maxLines: 1, overflow: TextOverflow.ellipsis, style: Theme.of(context).textTheme.bodySmall),
        ]),
      ),
    );
  }
}

IconData deviceIcon(String id) => switch (id) {
      'PUMP_1' => Icons.water_drop,
      'FAN_1' || 'FAN_2' => Icons.mode_fan_off_outlined,
      'LIGHT_1' => Icons.lightbulb,
      'VALVE_1' => Icons.plumbing,
      _ => Icons.power,
    };

String deviceStateLabel(DeviceView d) => switch (d.state) {
      DeviceState.on => d.id == 'VALVE_1' ? 'ĐANG MỞ' : 'ĐANG CHẠY',
      DeviceState.off => d.id == 'VALVE_1' ? 'ĐÓNG' : 'TẮT',
      DeviceState.unknown => 'KHÔNG RÕ',
    };

class _DeviceTile extends StatelessWidget {
  final DeviceView device;
  const _DeviceTile({required this.device});

  @override
  Widget build(BuildContext context) {
    final color = Scada.deviceColor(device.state);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Scada.panelHi(context),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: device.state == DeviceState.on ? color : Scada.border(context)),
      ),
      child: Row(children: [
        Icon(deviceIcon(device.id), color: color, size: 30),
        const SizedBox(width: 10),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(device.name, maxLines: 1, overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w600)),
            Text(deviceStateLabel(device),
                style: TextStyle(color: color, fontWeight: FontWeight.w800, fontSize: 12)),
            Text(device.onSince != null ? 'từ ${fmtTime(device.onSince)}' : device.plcOutput,
                style: Theme.of(context).textTheme.bodySmall),
          ]),
        ),
      ]),
    );
  }
}
