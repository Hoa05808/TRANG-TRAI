import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/farm_store.dart';
import '../theme/scada_theme.dart';
import '../widgets/common.dart';

/// 03 · Dữ liệu cảm biến thời gian thực (D50–D59).
class SensorsScreen extends StatelessWidget {
  const SensorsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final farm = context.watch<FarmStore>();
    final d = farm.dashboard;
    final s = d?.sensors;
    if (d == null) return const Center(child: CircularProgressIndicator());
    if (s == null) return const EmptyState(icon: Icons.sensors_off, text: 'Chưa nhận được dữ liệu cảm biến từ trang trại.');

    final stale = !d.dataFresh;
    final rows = <_Item>[
      _Item('Nhiệt độ chuồng', Icons.thermostat, s.coopTemp.toStringAsFixed(1), '°C', 'D50'),
      _Item('Độ ẩm không khí', Icons.water_drop_outlined, s.airHumidity.toStringAsFixed(1), '%', 'D51'),
      _Item('Nhiệt độ nước', Icons.waves, s.waterTemp.toStringAsFixed(1), '°C', 'D52'),
      _Item('Mực nước bể', Icons.water, s.waterLevel.toStringAsFixed(1), '%', 'D53'),
      _Item('Độ ẩm đất', Icons.grass, s.soilHumidity.toStringAsFixed(1), '%', 'D54'),
      _Item('Ánh sáng', Icons.wb_sunny_outlined, '${s.lightLux}', 'lux', 'D55'),
      _Item('CO₂', Icons.co2, '${s.co2Ppm}', 'ppm', 'D56'),
      _Item('NH₃', Icons.science_outlined, '${s.nh3Ppm}', 'ppm', 'D57'),
    ];

    return RefreshIndicator(
      onRefresh: farm.refresh,
      child: ListView(padding: const EdgeInsets.all(14), children: [
        Panel(
          title: 'Chất lượng dữ liệu',
          icon: Icons.verified_outlined,
          child: Wrap(spacing: 10, runSpacing: 8, children: [
            StatusChip(
                label: s.tempSensorOk ? 'CẢM BIẾN NHIỆT OK' : 'LỖI CẢM BIẾN NHIỆT',
                color: s.tempSensorOk ? Scada.running : Scada.critical),
            StatusChip(
                label: s.dataValid ? 'DỮ LIỆU HỢP LỆ' : 'DỮ LIỆU KHÔNG HỢP LỆ',
                color: s.dataValid ? Scada.running : Scada.critical),
            StatusChip(
                label: stale ? 'DỮ LIỆU CŨ – ${timeAgo(d.lastUpdate)}' : 'CẬP NHẬT ${fmtTime(d.lastUpdate)}',
                color: stale ? Scada.warning : Scada.info),
          ]),
        ),
        const SizedBox(height: 12),
        ResponsiveGrid(minItemWidth: 200, children: [
          for (final item in rows) _SensorCard(item: item, dimmed: stale || !s.dataValid),
        ]),
      ]),
    );
  }
}

class _Item {
  final String name;
  final IconData icon;
  final String value, unit, register;
  _Item(this.name, this.icon, this.value, this.unit, this.register);
}

class _SensorCard extends StatelessWidget {
  final _Item item;
  final bool dimmed;
  const _SensorCard({required this.item, required this.dimmed});

  @override
  Widget build(BuildContext context) {
    final color = dimmed ? Scada.off : Scada.accent;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(children: [
          Icon(item.icon, size: 32, color: color),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(item.name, style: const TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 2),
              Text.rich(TextSpan(children: [
                TextSpan(text: item.value, style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, color: color)),
                TextSpan(text: ' ${item.unit}', style: TextStyle(color: Theme.of(context).hintColor)),
              ])),
            ]),
          ),
          Text(item.register, style: Theme.of(context).textTheme.labelSmall?.copyWith(color: Theme.of(context).hintColor)),
        ]),
      ),
    );
  }
}
