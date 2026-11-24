import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../core/models.dart';
import '../theme/scada_theme.dart';

final _time = DateFormat('HH:mm:ss');
final _dateTime = DateFormat('HH:mm dd/MM/yyyy');
final _short = DateFormat('HH:mm dd/MM');

String fmtTime(DateTime? t) => t == null ? '--' : _time.format(t);
String fmtDateTime(DateTime? t) => t == null ? '--' : _dateTime.format(t);
String fmtShort(DateTime? t) => t == null ? '--' : _short.format(t);

String fmtDuration(Duration d) {
  if (d.inDays > 0) return '${d.inDays} ngày ${d.inHours % 24} giờ';
  if (d.inHours > 0) return '${d.inHours} giờ ${d.inMinutes % 60} phút';
  if (d.inMinutes > 0) return '${d.inMinutes} phút';
  return '${d.inSeconds} giây';
}

String timeAgo(DateTime? t) {
  if (t == null) return 'chưa có dữ liệu';
  final d = DateTime.now().difference(t);
  if (d.inSeconds < 10) return 'vừa xong';
  return '${fmtDuration(d)} trước';
}

/// Khung panel có tiêu đề kiểu SCADA.
class Panel extends StatelessWidget {
  final String? title;
  final IconData? icon;
  final Widget child;
  final Widget? trailing;
  final EdgeInsets padding;

  const Panel({super.key, this.title, this.icon, required this.child, this.trailing,
      this.padding = const EdgeInsets.all(14)});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: padding,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (title != null) ...[
              Row(children: [
                if (icon != null) ...[Icon(icon, size: 18, color: Scada.accent), const SizedBox(width: 8)],
                Expanded(
                  child: Text(title!.toUpperCase(),
                      style: Theme.of(context).textTheme.labelLarge?.copyWith(
                          letterSpacing: 1.1, fontWeight: FontWeight.w700, color: Theme.of(context).hintColor)),
                ),
                ?trailing,
              ]),
              const SizedBox(height: 12),
            ],
            child,
          ],
        ),
      ),
    );
  }
}

/// Đèn trạng thái tròn có viền sáng.
class StatusLamp extends StatelessWidget {
  final Color color;
  final double size;
  final bool glow;
  const StatusLamp({super.key, required this.color, this.size = 12, this.glow = true});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        boxShadow: glow ? [BoxShadow(color: color.withValues(alpha: 0.6), blurRadius: size * 0.8)] : null,
      ),
    );
  }
}

/// Nhãn trạng thái dạng viên thuốc.
class StatusChip extends StatelessWidget {
  final String label;
  final Color color;
  final IconData? icon;
  const StatusChip({super.key, required this.label, required this.color, this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.6)),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        if (icon != null) ...[Icon(icon, size: 14, color: color), const SizedBox(width: 4)],
        Text(label, style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 12, letterSpacing: 0.5)),
      ]),
    );
  }
}

/// Thanh báo kết nối hiển thị trên mọi màn hình khi không ONLINE.
class ConnectionBanner extends StatelessWidget {
  final Dashboard? dashboard;
  final String? serverError;
  const ConnectionBanner({super.key, required this.dashboard, this.serverError});

  @override
  Widget build(BuildContext context) {
    String? text;
    Color color = Scada.critical;
    IconData icon = Icons.cloud_off;
    if (serverError != null) {
      text = 'Mất kết nối máy chủ: $serverError';
    } else if (dashboard == null) {
      return const SizedBox.shrink();
    } else if (dashboard!.connection == ConnectionStatus.offline) {
      text = 'Mất kết nối trang trại (${timeAgo(dashboard!.lastUpdate)}). PLC vẫn tự động vận hành. '
          'Trạng thái thiết bị không xác định.';
    } else if (dashboard!.connection == ConnectionStatus.plcLinkLost) {
      text = 'Gateway không đọc được PLC (RS485). Trạng thái thiết bị không xác định.';
      color = Scada.warning;
      icon = Icons.link_off;
    } else if (dashboard!.gatewayMode == 'simulator') {
      text = 'Dữ liệu GIẢ LẬP – backend đang chạy chế độ simulator, không phải trang trại thật.';
      color = Scada.info;
      icon = Icons.science_outlined;
    }
    if (text == null) return const SizedBox.shrink();
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      color: color.withValues(alpha: 0.18),
      child: Row(children: [
        Icon(icon, size: 18, color: color),
        const SizedBox(width: 8),
        Expanded(child: Text(text, style: TextStyle(color: color, fontWeight: FontWeight.w600, fontSize: 13))),
      ]),
    );
  }
}

class EmptyState extends StatelessWidget {
  final IconData icon;
  final String text;
  const EmptyState({super.key, required this.icon, required this.text});

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 48, color: Theme.of(context).hintColor),
            const SizedBox(height: 12),
            Text(text, textAlign: TextAlign.center, style: TextStyle(color: Theme.of(context).hintColor)),
          ]),
        ),
      );
}

void showError(BuildContext context, Object e) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text('$e'), backgroundColor: Scada.critical));
}

void showInfo(BuildContext context, String text, {Color? color}) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(text), backgroundColor: color));
}

/// Bố cục lưới tự co giãn theo chiều rộng màn hình.
class ResponsiveGrid extends StatelessWidget {
  final List<Widget> children;
  final double minItemWidth;
  final double spacing;
  const ResponsiveGrid({super.key, required this.children, this.minItemWidth = 160, this.spacing = 12});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (context, c) {
      final cols = (c.maxWidth / minItemWidth).floor().clamp(1, 6);
      final w = (c.maxWidth - spacing * (cols - 1)) / cols;
      return Wrap(
        spacing: spacing,
        runSpacing: spacing,
        children: [for (final child in children) SizedBox(width: w, child: child)],
      );
    });
  }
}
