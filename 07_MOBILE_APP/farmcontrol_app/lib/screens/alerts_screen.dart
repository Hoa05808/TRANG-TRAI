import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/api_client.dart';
import '../core/farm_store.dart';
import '../core/models.dart';
import '../core/session_store.dart';
import '../theme/scada_theme.dart';
import '../widgets/common.dart';
import '../widgets/paged_list.dart';

/// 05 · Cảnh báo: đang diễn ra và lịch sử.
class AlertsScreen extends StatelessWidget {
  const AlertsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final farm = context.watch<FarmStore>();
    final api = context.read<SessionStore>().api;
    return DefaultTabController(
      length: 2,
      child: Column(children: [
        TabBar(tabs: [
          Tab(text: 'Đang diễn ra (${farm.activeAlerts.length})'),
          const Tab(text: 'Lịch sử'),
        ]),
        Expanded(
          child: TabBarView(children: [
            farm.activeAlerts.isEmpty
                ? const EmptyState(icon: Icons.check_circle_outline, text: 'Không có cảnh báo. Hệ thống bình thường.')
                : ListView(padding: const EdgeInsets.all(12), children: [
                    for (final a in farm.activeAlerts) AlertCard(alert: a),
                  ]),
            PagedList<FarmAlert>(
              load: (page) async => PageResult.fromJson(
                  await api.get('/api/alerts', query: {'page': page, 'size': 30}) as Map<String, dynamic>,
                  FarmAlert.fromJson),
              itemBuilder: (a) => AlertCard(alert: a),
              emptyText: 'Chưa có cảnh báo nào trong 7 ngày qua.',
            ),
          ]),
        ),
      ]),
    );
  }
}

class AlertCard extends StatelessWidget {
  final FarmAlert alert;
  const AlertCard({super.key, required this.alert});

  @override
  Widget build(BuildContext context) {
    final color = alert.active ? Scada.severityColor(alert.severity) : Scada.off;
    final canAck = context.watch<SessionStore>().user?.role.canControl ?? false;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Card(
        child: Container(
          decoration: BoxDecoration(border: Border(left: BorderSide(color: color, width: 5))),
          padding: const EdgeInsets.all(12),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Icon(alert.severity == Severity.critical ? Icons.report : Icons.warning_amber_rounded, color: color),
              const SizedBox(width: 8),
              Expanded(child: Text(alert.title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15))),
              StatusChip(
                label: switch (alert.severity) {
                  Severity.critical => 'KHẨN CẤP',
                  Severity.warning => 'CẢNH BÁO',
                  Severity.info => 'THÔNG TIN',
                },
                color: color,
              ),
            ]),
            if (alert.message != null) ...[const SizedBox(height: 6), Text(alert.message!)],
            const SizedBox(height: 8),
            Wrap(spacing: 14, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
              Text('Xảy ra: ${fmtDateTime(alert.raisedAt)}', style: Theme.of(context).textTheme.bodySmall),
              if (alert.resolvedAt != null)
                Text('Kết thúc: ${fmtDateTime(alert.resolvedAt)}', style: Theme.of(context).textTheme.bodySmall),
              if (alert.acknowledged)
                Text('✓ ${alert.acknowledgedBy} xác nhận lúc ${fmtTime(alert.acknowledgedAt)}',
                    style: const TextStyle(color: Scada.running, fontSize: 12)),
              if (alert.active && !alert.acknowledged && canAck)
                TextButton.icon(
                  onPressed: () async {
                    try {
                      await context.read<FarmStore>().acknowledgeAlert(alert.id);
                    } on ApiException catch (e) {
                      if (context.mounted) showError(context, e.message);
                    }
                  },
                  icon: const Icon(Icons.done, size: 18),
                  label: const Text('Xác nhận đã biết'),
                ),
            ]),
          ]),
        ),
      ),
    );
  }
}
