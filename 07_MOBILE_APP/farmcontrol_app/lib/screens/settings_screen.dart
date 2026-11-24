import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/api_client.dart';
import '../core/farm_store.dart';
import '../core/models.dart';
import '../core/push_service.dart';
import '../core/session_store.dart';
import '../theme/scada_theme.dart';
import '../widgets/common.dart';

/// 07 · Cài đặt: tài khoản, phân quyền, ngưỡng cảnh báo, kết nối.
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final session = context.watch<SessionStore>();
    final farm = context.watch<FarmStore>();
    final user = session.user!;
    return ListView(padding: const EdgeInsets.all(14), children: [
      Panel(
        title: 'Tài khoản',
        icon: Icons.person,
        child: Column(children: [
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const CircleAvatar(child: Icon(Icons.person)),
            title: Text(user.displayName, style: const TextStyle(fontWeight: FontWeight.w700)),
            subtitle: Text('${user.username} · ${user.role.label}'),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.password),
            title: const Text('Đổi mật khẩu'),
            onTap: () => _changePassword(context),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.logout, color: Scada.critical),
            title: const Text('Đăng xuất', style: TextStyle(color: Scada.critical)),
            onTap: () => session.logout(),
          ),
        ]),
      ),
      const SizedBox(height: 12),
      Panel(
        title: 'Giao diện & kết nối',
        icon: Icons.tune,
        child: Column(children: [
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Giao diện tối'),
            value: session.themeMode == ThemeMode.dark,
            onChanged: (v) => session.setThemeMode(v ? ThemeMode.dark : ThemeMode.light),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.dns_outlined),
            title: const Text('Máy chủ'),
            subtitle: Text(session.serverUrl),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: Icon(Icons.bolt, color: farm.realtimeConnected ? Scada.running : Scada.warning),
            title: const Text('Dữ liệu thời gian thực'),
            subtitle: Text(farm.realtimeConnected ? 'WebSocket đang kết nối' : 'Mất WebSocket – đang hỏi mỗi 10 giây'),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: Icon(Icons.notifications_active,
                color: PushService.instance.available ? Scada.running : Scada.off),
            title: const Text('Thông báo đẩy'),
            subtitle: Text(PushService.instance.statusMessage ?? 'Đang khởi tạo...'),
          ),
        ]),
      ),
      const SizedBox(height: 12),
      const _ThresholdPanel(),
      if (user.role.isOwner) ...[
        const SizedBox(height: 12),
        const _UsersPanel(),
      ],
      if (user.role.isOwner && farm.dashboard?.gatewayMode == 'simulator') ...[
        const SizedBox(height: 12),
        const _SimulatorPanel(),
      ],
      const SizedBox(height: 24),
    ]);
  }

  Future<void> _changePassword(BuildContext context) async {
    final api = context.read<SessionStore>().api;
    final cur = TextEditingController();
    final next = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Đổi mật khẩu'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(controller: cur, obscureText: true, decoration: const InputDecoration(labelText: 'Mật khẩu hiện tại')),
          const SizedBox(height: 12),
          TextField(controller: next, obscureText: true, decoration: const InputDecoration(labelText: 'Mật khẩu mới (≥ 8 ký tự)')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Hủy')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Lưu')),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    try {
      await api.post('/api/me/password', {'currentPassword': cur.text, 'newPassword': next.text});
      if (context.mounted) showInfo(context, 'Đã đổi mật khẩu. Các thiết bị khác sẽ phải đăng nhập lại.');
    } on ApiException catch (e) {
      if (context.mounted) showError(context, e.message);
    }
  }
}

/// Ngưỡng cảnh báo phía backend (chủ trang trại được sửa).
class _ThresholdPanel extends StatefulWidget {
  const _ThresholdPanel();

  @override
  State<_ThresholdPanel> createState() => _ThresholdPanelState();
}

class _ThresholdPanelState extends State<_ThresholdPanel> {
  List<Setting>? _settings;
  final Map<String, TextEditingController> _ctrl = {};
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    for (final c in _ctrl.values) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final list = (await context.read<SessionStore>().api.get('/api/settings') as List)
          .map((e) => Setting.fromJson(e as Map<String, dynamic>))
          .toList();
      setState(() {
        _settings = list;
        for (final s in list) {
          (_ctrl[s.key] ??= TextEditingController()).text = _fmt(s.value);
        }
      });
    } on ApiException catch (e) {
      if (mounted) showError(context, e.message);
    }
  }

  static String _fmt(double v) => v == v.roundToDouble() ? v.toStringAsFixed(0) : v.toString();

  Future<void> _save() async {
    final changes = <String, double>{};
    for (final s in _settings!) {
      final v = double.tryParse(_ctrl[s.key]!.text.replaceAll(',', '.'));
      if (v == null) {
        showError(context, '${s.description}: giá trị không hợp lệ');
        return;
      }
      if (v != s.value) changes[s.key] = v;
    }
    if (changes.isEmpty) return;
    setState(() => _saving = true);
    try {
      await context.read<SessionStore>().api.put('/api/settings', changes);
      await _load();
      if (mounted) showInfo(context, 'Đã lưu ${changes.length} thông số');
    } on ApiException catch (e) {
      if (mounted) showError(context, e.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final owner = context.watch<SessionStore>().user?.role.isOwner ?? false;
    final s = _settings;
    return Panel(
      title: 'Ngưỡng cảnh báo',
      icon: Icons.notifications_active_outlined,
      trailing: owner && s != null
          ? FilledButton.tonal(onPressed: _saving ? null : _save, child: const Text('Lưu'))
          : null,
      child: s == null
          ? const Center(child: Padding(padding: EdgeInsets.all(12), child: CircularProgressIndicator()))
          : Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Text('Ngưỡng ĐIỀU KHIỂN (D0–D9) vẫn nằm trong PLC. Đây là ngưỡng để app phát cảnh báo.',
                  style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 10),
              ResponsiveGrid(minItemWidth: 260, children: [
                for (final x in s)
                  TextField(
                    controller: _ctrl[x.key],
                    enabled: owner,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      labelText: x.description,
                      suffixText: x.unit,
                      helperText: '${_fmt(x.min)} – ${_fmt(x.max)} · mặc định ${_fmt(x.defaultValue)}',
                      isDense: true,
                    ),
                  ),
              ]),
            ]),
    );
  }
}

/// Quản lý tài khoản và phân quyền (chỉ chủ trang trại).
class _UsersPanel extends StatefulWidget {
  const _UsersPanel();

  @override
  State<_UsersPanel> createState() => _UsersPanelState();
}

class _UsersPanelState extends State<_UsersPanel> {
  List<AppUser>? _users;

  @override
  void initState() {
    super.initState();
    _load();
  }

  ApiClient get _api => context.read<SessionStore>().api;

  Future<void> _load() async {
    try {
      final list = (await _api.get('/api/users') as List).map((e) => AppUser.fromJson(e as Map<String, dynamic>)).toList();
      if (mounted) setState(() => _users = list);
    } on ApiException catch (e) {
      if (mounted) showError(context, e.message);
    }
  }

  Future<void> _run(Future<void> Function() action, String done) async {
    try {
      await action();
      await _load();
      if (mounted) showInfo(context, done);
    } on ApiException catch (e) {
      if (mounted) showError(context, e.message);
    }
  }

  Future<void> _create() async {
    final username = TextEditingController();
    final fullName = TextEditingController();
    final password = TextEditingController();
    var role = Role.operator;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: const Text('Thêm tài khoản'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(controller: username, decoration: const InputDecoration(labelText: 'Tên đăng nhập')),
            const SizedBox(height: 10),
            TextField(controller: fullName, decoration: const InputDecoration(labelText: 'Họ tên')),
            const SizedBox(height: 10),
            TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: 'Mật khẩu (≥ 8 ký tự)')),
            const SizedBox(height: 10),
            DropdownButtonFormField<Role>(
              initialValue: role,
              decoration: const InputDecoration(labelText: 'Vai trò'),
              items: [for (final r in Role.values) DropdownMenuItem(value: r, child: Text(r.label))],
              onChanged: (r) => setLocal(() => role = r ?? role),
            ),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Hủy')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Tạo')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    await _run(
        () => _api.post('/api/users', {
              'username': username.text.trim(),
              'fullName': fullName.text.trim(),
              'password': password.text,
              'role': role.api,
            }),
        'Đã tạo tài khoản ${username.text.trim()}');
  }

  @override
  Widget build(BuildContext context) {
    final me = context.watch<SessionStore>().user!;
    final users = _users;
    return Panel(
      title: 'Tài khoản & phân quyền',
      icon: Icons.admin_panel_settings_outlined,
      trailing: IconButton(tooltip: 'Thêm tài khoản', icon: const Icon(Icons.person_add_alt), onPressed: _create),
      child: users == null
          ? const Center(child: CircularProgressIndicator())
          : Column(children: [
              for (final u in users)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(Icons.account_circle, color: u.enabled ? Scada.accent : Scada.off),
                  title: Text(u.displayName),
                  subtitle: Text('${u.username} · ${u.role.label}${u.enabled ? '' : ' · ĐÃ KHÓA'}'),
                  trailing: u.id == me.id
                      ? const Text('(bạn)')
                      : PopupMenuButton<String>(
                          onSelected: (v) {
                            if (v == 'toggle') {
                              _run(() => _api.patch('/api/users/${u.id}', {'enabled': !u.enabled}),
                                  u.enabled ? 'Đã khóa ${u.username}' : 'Đã mở khóa ${u.username}');
                            } else {
                              _run(() => _api.patch('/api/users/${u.id}', {'role': v}), 'Đã đổi vai trò ${u.username}');
                            }
                          },
                          itemBuilder: (_) => [
                            for (final r in Role.values)
                              if (r != u.role) PopupMenuItem(value: r.api, child: Text('Đổi thành: ${r.label}')),
                            PopupMenuItem(value: 'toggle', child: Text(u.enabled ? 'Khóa tài khoản' : 'Mở khóa')),
                          ],
                        ),
                ),
            ]),
    );
  }
}

/// Tạo sự cố giả để thử cảnh báo (chỉ khi backend chạy chế độ simulator).
class _SimulatorPanel extends StatefulWidget {
  const _SimulatorPanel();

  @override
  State<_SimulatorPanel> createState() => _SimulatorPanelState();
}

class _SimulatorPanelState extends State<_SimulatorPanel> {
  static const _faults = {
    'NONE': 'Bình thường',
    'TEMP_HIGH': 'Nhiệt độ cao',
    'WATER_LOW': 'Mực nước thấp',
    'SENSOR_FAULT': 'Lỗi cảm biến',
    'PLC_ERROR': 'PLC lỗi',
    'PLC_LINK_LOST': 'Mất RS485',
    'GATEWAY_OFFLINE': 'Mất Gateway',
  };
  String _current = 'NONE';

  Future<void> _set(String f) async {
    try {
      final r = await context.read<SessionStore>().api.put('/api/simulator/fault?value=$f') as Map<String, dynamic>;
      setState(() => _current = r['fault'] as String);
    } on ApiException catch (e) {
      if (mounted) showError(context, e.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Panel(
      title: 'Bộ giả lập (thử nghiệm)',
      icon: Icons.science_outlined,
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('Tạo sự cố giả để kiểm tra cảnh báo và xử lý mất kết nối.', style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 10),
        Wrap(spacing: 8, runSpacing: 8, children: [
          for (final e in _faults.entries)
            ChoiceChip(label: Text(e.value), selected: _current == e.key, onSelected: (_) => _set(e.key)),
        ]),
      ]),
    );
  }
}
