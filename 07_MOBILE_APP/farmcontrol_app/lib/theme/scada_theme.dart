import 'package:flutter/material.dart';

import '../core/models.dart';

/// Bảng màu kiểu SCADA công nghiệp.
/// Xanh lá = đang chạy · Xám = tắt · Vàng = cảnh báo · Đỏ = lỗi nghiêm trọng.
class Scada {
  static const running = Color(0xFF22C55E);
  static const off = Color(0xFF7A8794);
  static const warning = Color(0xFFF5B700);
  static const critical = Color(0xFFEF4444);
  static const info = Color(0xFF38BDF8);
  static const accent = Color(0xFF22B8CF);

  // Nền tối xanh đen
  static const darkBg = Color(0xFF0A141D);
  static const darkPanel = Color(0xFF111F2B);
  static const darkPanelHi = Color(0xFF16293A);
  static const darkBorder = Color(0xFF213A4F);

  // Nền sáng
  static const lightBg = Color(0xFFF1F5F9);
  static const lightPanel = Colors.white;
  static const lightBorder = Color(0xFFD6DEE6);

  static Color deviceColor(DeviceState s) => switch (s) {
        DeviceState.on => running,
        DeviceState.off => off,
        DeviceState.unknown => off.withValues(alpha: 0.5),
      };

  static Color severityColor(Severity s) => switch (s) {
        Severity.critical => critical,
        Severity.warning => warning,
        Severity.info => info,
      };

  static Color connectionColor(ConnectionStatus c) => switch (c) {
        ConnectionStatus.online => running,
        ConnectionStatus.plcLinkLost => warning,
        ConnectionStatus.offline => critical,
      };

  static Color border(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? darkBorder : lightBorder;

  static Color panelHi(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? darkPanelHi : const Color(0xFFF8FAFC);

  static ThemeData theme(Brightness brightness) {
    final dark = brightness == Brightness.dark;
    final scheme = ColorScheme.fromSeed(
      seedColor: accent,
      brightness: brightness,
      surface: dark ? darkPanel : lightPanel,
      primary: accent,
      error: critical,
    );
    final base = ThemeData(useMaterial3: true, colorScheme: scheme, brightness: brightness);
    return base.copyWith(
      scaffoldBackgroundColor: dark ? darkBg : lightBg,
      appBarTheme: AppBarTheme(
        backgroundColor: dark ? darkBg : lightBg,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        titleTextStyle: base.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700, letterSpacing: 0.5),
      ),
      cardTheme: CardThemeData(
        color: dark ? darkPanel : lightPanel,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(10),
          side: BorderSide(color: dark ? darkBorder : lightBorder),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: dark ? darkPanel : lightPanel,
        indicatorColor: accent.withValues(alpha: 0.2),
        labelTextStyle: WidgetStatePropertyAll(base.textTheme.labelSmall),
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: dark ? darkPanel : lightPanel,
        indicatorColor: accent.withValues(alpha: 0.2),
      ),
      dividerTheme: DividerThemeData(color: dark ? darkBorder : lightBorder, space: 1),
      inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
      snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
    );
  }
}
