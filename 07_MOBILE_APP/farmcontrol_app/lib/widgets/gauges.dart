import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme/scada_theme.dart';

/// Đồng hồ cung tròn kiểu SCADA, đổi màu theo ngưỡng cảnh báo.
class RadialGauge extends StatelessWidget {
  final String label;
  final double value;
  final String unit;
  final double min, max;
  final double? warnAt, alarmAt;
  final bool dimmed;

  const RadialGauge({
    super.key,
    required this.label,
    required this.value,
    required this.unit,
    required this.min,
    required this.max,
    this.warnAt,
    this.alarmAt,
    this.dimmed = false,
  });

  Color get color {
    if (dimmed) return Scada.off;
    if (alarmAt != null && value >= alarmAt!) return Scada.critical;
    if (warnAt != null && value >= warnAt!) return Scada.warning;
    return Scada.running;
  }

  @override
  Widget build(BuildContext context) {
    final frac = ((value - min) / (max - min)).clamp(0.0, 1.0);
    return Column(children: [
      SizedBox(
        height: 110,
        child: CustomPaint(
          painter: _ArcPainter(frac, color, Scada.border(context)),
          child: Center(
            child: Padding(
              padding: const EdgeInsets.only(top: 18),
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                Text(value.toStringAsFixed(1),
                    style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800, color: color)),
                Text(unit, style: TextStyle(color: Theme.of(context).hintColor)),
              ]),
            ),
          ),
        ),
      ),
      Text(label, textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w600)),
    ]);
  }
}

class _ArcPainter extends CustomPainter {
  final double frac;
  final Color color, track;
  _ArcPainter(this.frac, this.color, this.track);

  @override
  void paint(Canvas canvas, Size size) {
    final r = math.min(size.width / 2, size.height) - 10;
    final rect = Rect.fromCircle(center: Offset(size.width / 2, size.height - 12), radius: r);
    const start = math.pi * 0.85;
    const sweep = math.pi * 1.3;
    final base = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 10
      ..strokeCap = StrokeCap.round
      ..color = track;
    canvas.drawArc(rect, start, sweep, false, base);
    canvas.drawArc(rect, start, sweep * frac, false, base..color = color);
  }

  @override
  bool shouldRepaint(_ArcPainter old) => old.frac != frac || old.color != color || old.track != track;
}

/// Bể nước dạng cột đứng.
class TankGauge extends StatelessWidget {
  final String label;
  final double percent;
  final double lowAt;
  final bool dimmed;

  const TankGauge({super.key, required this.label, required this.percent, required this.lowAt, this.dimmed = false});

  @override
  Widget build(BuildContext context) {
    final p = (percent / 100).clamp(0.0, 1.0);
    final color = dimmed ? Scada.off : (percent <= lowAt ? Scada.critical : Scada.info);
    return Column(children: [
      SizedBox(
        height: 110,
        child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          Container(
            width: 46,
            height: 96,
            decoration: BoxDecoration(
              border: Border.all(color: Scada.border(context), width: 2),
              borderRadius: BorderRadius.circular(6),
            ),
            alignment: Alignment.bottomCenter,
            padding: const EdgeInsets.all(3),
            child: FractionallySizedBox(
              heightFactor: p,
              widthFactor: 1,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.75),
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
            ),
          ),
          const SizedBox(width: 12),
          Text('${percent.toStringAsFixed(1)}%',
              style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, color: color)),
        ]),
      ),
      Text(label, style: const TextStyle(fontWeight: FontWeight.w600)),
    ]);
  }
}
