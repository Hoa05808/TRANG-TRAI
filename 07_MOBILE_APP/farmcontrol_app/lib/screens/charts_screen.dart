import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../core/api_client.dart';
import '../core/session_store.dart';
import '../core/models.dart';
import '../theme/scada_theme.dart';
import '../widgets/common.dart';

enum _Range { hour, day, week, month }

extension on _Range {
  String get api => name.toUpperCase();
  String get label => switch (this) {
        _Range.hour => '1 giờ',
        _Range.day => 'Ngày',
        _Range.week => 'Tuần',
        _Range.month => 'Tháng',
      };
  Duration get length => switch (this) {
        _Range.hour => const Duration(hours: 1),
        _Range.day => const Duration(days: 1),
        _Range.week => const Duration(days: 7),
        _Range.month => const Duration(days: 30),
      };
}

class _Metric {
  final String label, unit;
  final Color color;
  final Stat? Function(HistoryPoint) pick;
  const _Metric(this.label, this.unit, this.color, this.pick);
}

final _metrics = [
  _Metric('Nhiệt độ chuồng', '°C', Scada.critical, (p) => p.coopTemp),
  _Metric('Độ ẩm', '%', Scada.info, (p) => p.airHumidity),
  _Metric('Mực nước', '%', Scada.accent, (p) => p.waterLevel),
  _Metric('Nhiệt độ nước', '°C', Scada.warning, (p) => p.waterTemp),
];

/// 04 · Biểu đồ nhiệt độ, độ ẩm và hoạt động thiết bị theo ngày / tuần / tháng.
class ChartsScreen extends StatefulWidget {
  const ChartsScreen({super.key});

  @override
  State<ChartsScreen> createState() => _ChartsScreenState();
}

class _ChartsScreenState extends State<ChartsScreen> {
  _Range _range = _Range.day;
  int _metric = 0;
  List<HistoryPoint>? _points;
  List<DeviceRuntime>? _runtime;
  String? _error;
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final api = context.read<SessionStore>().api;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final now = DateTime.now();
      final runtimeFrom = now.subtract(_range == _Range.month ? const Duration(days: 31) : _range.length);
      final results = await Future.wait([
        api.get('/api/sensors/history', query: {'range': _range.api}),
        api.get('/api/history/device-runtime', query: {'from': runtimeFrom, 'to': now}),
      ]);
      final h = results[0] as Map<String, dynamic>;
      setState(() {
        _points = (h['points'] as List).map((e) => HistoryPoint.fromJson(e as Map<String, dynamic>)).toList();
        _runtime = (results[1] as List).map((e) => DeviceRuntime.fromJson(e as Map<String, dynamic>)).toList();
      });
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final m = _metrics[_metric];
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(padding: const EdgeInsets.all(14), children: [
        Wrap(spacing: 12, runSpacing: 10, crossAxisAlignment: WrapCrossAlignment.center, children: [
          SegmentedButton<_Range>(
            segments: [for (final r in _Range.values) ButtonSegment(value: r, label: Text(r.label))],
            selected: {_range},
            onSelectionChanged: (s) {
              setState(() => _range = s.first);
              _load();
            },
          ),
          if (_loading) const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
        ]),
        const SizedBox(height: 12),
        Wrap(spacing: 8, runSpacing: 8, children: [
          for (var i = 0; i < _metrics.length; i++)
            ChoiceChip(
              label: Text(_metrics[i].label),
              selected: _metric == i,
              onSelected: (_) => setState(() => _metric = i),
            ),
        ]),
        const SizedBox(height: 12),
        if (_error != null) Panel(child: Text(_error!, style: const TextStyle(color: Scada.critical))),
        Panel(
          title: '${m.label} (${m.unit})',
          icon: Icons.show_chart,
          trailing: Text('Đường: trung bình · Vùng: min–max', style: Theme.of(context).textTheme.bodySmall),
          child: SizedBox(height: 280, child: _buildChart(m)),
        ),
        const SizedBox(height: 14),
        Panel(
          title: 'Hoạt động thiết bị',
          icon: Icons.timeline,
          child: _runtime == null
              ? const SizedBox(height: 60)
              : Column(children: [for (final r in _runtime!) _RuntimeRow(runtime: r, range: _range)]),
        ),
      ]),
    );
  }

  Widget _buildChart(_Metric m) {
    final pts = _points;
    if (pts == null) return const Center(child: CircularProgressIndicator());
    final data = [for (final p in pts) if (m.pick(p) != null) (p.time, m.pick(p)!)];
    if (data.isEmpty) {
      return const EmptyState(icon: Icons.insights, text: 'Chưa có dữ liệu trong khoảng này.\nBackend lưu mẫu mỗi 60 giây.');
    }
    double x(DateTime t) => t.millisecondsSinceEpoch.toDouble();
    final minY = data.map((e) => e.$2.min).reduce((a, b) => a < b ? a : b);
    final maxY = data.map((e) => e.$2.max).reduce((a, b) => a > b ? a : b);
    final pad = ((maxY - minY) * 0.15).clamp(1.0, 20.0);
    final fmt = switch (_range) {
      _Range.hour || _Range.day => DateFormat('HH:mm'),
      _Range.week => DateFormat('E dd/MM', 'vi'),
      _Range.month => DateFormat('dd/MM'),
    };
    final now = DateTime.now();
    final minX = x(now.subtract(_range.length));
    final maxX = x(now);
    final grid = Scada.border(context);

    LineChartBarData band(double Function(Stat) f) => LineChartBarData(
          spots: [for (final e in data) FlSpot(x(e.$1), f(e.$2))],
          color: Colors.transparent,
          barWidth: 0,
          dotData: const FlDotData(show: false),
        );

    return LineChart(LineChartData(
      minX: minX,
      maxX: maxX,
      minY: (minY - pad).floorToDouble(),
      maxY: (maxY + pad).ceilToDouble(),
      clipData: const FlClipData.all(),
      gridData: FlGridData(
        drawVerticalLine: false,
        getDrawingHorizontalLine: (_) => FlLine(color: grid, strokeWidth: 1),
      ),
      borderData: FlBorderData(show: false),
      betweenBarsData: [BetweenBarsData(fromIndex: 0, toIndex: 1, color: m.color.withValues(alpha: 0.15))],
      lineBarsData: [
        band((s) => s.min),
        band((s) => s.max),
        LineChartBarData(
          spots: [for (final e in data) FlSpot(x(e.$1), e.$2.avg)],
          color: m.color,
          barWidth: 2.5,
          isCurved: true,
          preventCurveOverShooting: true,
          dotData: FlDotData(show: data.length < 30),
        ),
      ],
      titlesData: FlTitlesData(
        topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
        rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
        leftTitles: AxisTitles(
          sideTitles: SideTitles(
            showTitles: true,
            reservedSize: 40,
            getTitlesWidget: (v, meta) => Text(v.toStringAsFixed(0), style: const TextStyle(fontSize: 11)),
          ),
        ),
        bottomTitles: AxisTitles(
          sideTitles: SideTitles(
            showTitles: true,
            reservedSize: 28,
            interval: (maxX - minX) / 4,
            getTitlesWidget: (v, meta) {
              if (v == meta.min || v == meta.max) return const SizedBox.shrink();
              return Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(fmt.format(DateTime.fromMillisecondsSinceEpoch(v.toInt())),
                    style: const TextStyle(fontSize: 11)),
              );
            },
          ),
        ),
      ),
      lineTouchData: LineTouchData(
        touchTooltipData: LineTouchTooltipData(
          getTooltipItems: (spots) => [
            for (final s in spots)
              s.barIndex == 2
                  ? LineTooltipItem(
                      '${DateFormat('HH:mm dd/MM').format(DateTime.fromMillisecondsSinceEpoch(s.x.toInt()))}\n'
                      '${s.y.toStringAsFixed(1)} ${m.unit}',
                      TextStyle(color: m.color, fontWeight: FontWeight.w700),
                    )
                  : null,
          ],
        ),
      ),
    ));
  }
}

/// Dải thời gian chạy của một thiết bị.
class _RuntimeRow extends StatelessWidget {
  final DeviceRuntime runtime;
  final _Range range;
  const _RuntimeRow({required this.runtime, required this.range});

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final from = now.subtract(range == _Range.month ? const Duration(days: 31) : range.length);
    final total = now.difference(from).inSeconds.toDouble();
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(children: [
        SizedBox(width: 130, child: Text(runtime.deviceName, overflow: TextOverflow.ellipsis)),
        Expanded(
          child: LayoutBuilder(builder: (context, c) {
            return Container(
              height: 18,
              decoration: BoxDecoration(color: Scada.panelHi(context), borderRadius: BorderRadius.circular(4)),
              child: Stack(children: [
                for (final s in runtime.segments)
                  Positioned(
                    left: c.maxWidth * (s.start.difference(from).inSeconds.clamp(0, total) / total),
                    width: (c.maxWidth * (s.end.difference(s.start).inSeconds / total)).clamp(1.5, c.maxWidth),
                    top: 0,
                    bottom: 0,
                    child: Container(color: Scada.running.withValues(alpha: 0.85)),
                  ),
              ]),
            );
          }),
        ),
        SizedBox(
          width: 120,
          child: Text('${fmtDuration(Duration(seconds: runtime.onSeconds))} · ${runtime.starts} lần',
              textAlign: TextAlign.right, style: Theme.of(context).textTheme.bodySmall),
        ),
      ]),
    );
  }
}
