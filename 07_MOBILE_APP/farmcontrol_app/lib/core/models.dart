// Mô hình dữ liệu, khớp với JSON của backend Spring Boot (09_BACKEND/README.md).

DateTime? _date(dynamic v) => v == null ? null : DateTime.parse(v as String).toLocal();
double _d(dynamic v) => v == null ? 0 : (v as num).toDouble();
int _i(dynamic v) => v == null ? 0 : (v as num).toInt();

enum Role { owner, operator, viewer }

Role roleFrom(String s) => Role.values.firstWhere((r) => r.name.toUpperCase() == s, orElse: () => Role.viewer);

extension RoleX on Role {
  String get api => name.toUpperCase();
  String get label => switch (this) {
        Role.owner => 'Chủ trang trại',
        Role.operator => 'Người vận hành',
        Role.viewer => 'Chỉ xem',
      };
  bool get canControl => this != Role.viewer;
  bool get isOwner => this == Role.owner;
}

class AppUser {
  final int id;
  final String username;
  final String? fullName;
  final Role role;
  final bool enabled;

  AppUser({required this.id, required this.username, this.fullName, required this.role, required this.enabled});

  factory AppUser.fromJson(Map<String, dynamic> j) => AppUser(
        id: _i(j['id']),
        username: j['username'] as String,
        fullName: j['fullName'] as String?,
        role: roleFrom(j['role'] as String),
        enabled: j['enabled'] as bool? ?? true,
      );

  String get displayName => (fullName == null || fullName!.isEmpty) ? username : fullName!;
}

enum ConnectionStatus { online, plcLinkLost, offline }

ConnectionStatus _conn(String? s) => switch (s) {
      'ONLINE' => ConnectionStatus.online,
      'PLC_LINK_LOST' => ConnectionStatus.plcLinkLost,
      _ => ConnectionStatus.offline,
    };

enum DeviceState { on, off, unknown }

class SensorValues {
  final double coopTemp, airHumidity, waterTemp, waterLevel, soilHumidity;
  final int lightLux, co2Ppm, nh3Ppm;
  final bool tempSensorOk, dataValid;

  SensorValues.fromJson(Map<String, dynamic> j)
      : coopTemp = _d(j['coopTemp']),
        airHumidity = _d(j['airHumidity']),
        waterTemp = _d(j['waterTemp']),
        waterLevel = _d(j['waterLevel']),
        soilHumidity = _d(j['soilHumidity']),
        lightLux = _i(j['lightLux']),
        co2Ppm = _i(j['co2Ppm']),
        nh3Ppm = _i(j['nh3Ppm']),
        tempSensorOk = j['tempSensorOk'] as bool? ?? false,
        dataValid = j['dataValid'] as bool? ?? false;
}

class DeviceView {
  final String id; // PUMP_1, FAN_1, FAN_2, LIGHT_1, VALVE_1
  final String name;
  final String plcOutput;
  final DeviceState state;
  final DateTime? onSince;

  DeviceView.fromJson(Map<String, dynamic> j)
      : id = j['id'] as String,
        name = j['name'] as String,
        plcOutput = j['plcOutput'] as String,
        state = switch (j['state']) { 'ON' => DeviceState.on, 'OFF' => DeviceState.off, _ => DeviceState.unknown },
        onSince = _date(j['onSince']);
}

class Dashboard {
  final ConnectionStatus connection;
  final bool dataFresh;
  final DateTime? lastUpdate;
  final String gatewayMode;
  final SensorValues? sensors;
  final List<DeviceView> devices;
  final String mode; // STOP AUTO MANUAL UNKNOWN
  final String plcState; // STOP RUN ERROR UNKNOWN
  final int errorCode, alarmCode, plcUptime;
  final bool remoteEnabled;
  final int activeAlerts, unacknowledgedAlerts;

  Dashboard.fromJson(Map<String, dynamic> j)
      : connection = _conn(j['connection'] as String?),
        dataFresh = j['dataFresh'] as bool? ?? false,
        lastUpdate = _date(j['lastUpdate']),
        gatewayMode = j['gatewayMode'] as String? ?? '',
        sensors = j['sensors'] == null ? null : SensorValues.fromJson(j['sensors'] as Map<String, dynamic>),
        devices = (j['devices'] as List? ?? []).map((e) => DeviceView.fromJson(e as Map<String, dynamic>)).toList(),
        mode = j['mode'] as String? ?? 'UNKNOWN',
        plcState = j['plcState'] as String? ?? 'UNKNOWN',
        errorCode = _i(j['errorCode']),
        alarmCode = _i(j['alarmCode']),
        plcUptime = _i(j['plcUptime']),
        remoteEnabled = j['remoteEnabled'] as bool? ?? false,
        activeAlerts = _i(j['activeAlerts']),
        unacknowledgedAlerts = _i(j['unacknowledgedAlerts']);

  DeviceView? device(String id) {
    for (final d in devices) {
      if (d.id == id) return d;
    }
    return null;
  }

  bool get online => connection == ConnectionStatus.online;

  String get modeLabel => switch (mode) {
        'AUTO' => 'TỰ ĐỘNG',
        'MANUAL' => 'THỦ CÔNG',
        'STOP' => 'DỪNG',
        _ => 'KHÔNG RÕ',
      };
}

enum Severity { info, warning, critical }

Severity _sev(String? s) => switch (s) {
      'CRITICAL' => Severity.critical,
      'WARNING' => Severity.warning,
      _ => Severity.info,
    };

class FarmAlert {
  final int id;
  final String type;
  final Severity severity;
  final bool active;
  final String title;
  final String? message;
  final double? value;
  final DateTime raisedAt;
  final DateTime? resolvedAt, acknowledgedAt;
  final String? acknowledgedBy;

  FarmAlert.fromJson(Map<String, dynamic> j)
      : id = _i(j['id']),
        type = j['type'] as String,
        severity = _sev(j['severity'] as String?),
        active = j['status'] == 'ACTIVE',
        title = j['title'] as String,
        message = j['message'] as String?,
        value = j['value'] == null ? null : _d(j['value']),
        raisedAt = _date(j['raisedAt'])!,
        resolvedAt = _date(j['resolvedAt']),
        acknowledgedAt = _date(j['acknowledgedAt']),
        acknowledgedBy = j['acknowledgedBy'] as String?;

  bool get acknowledged => acknowledgedAt != null;
}

class FarmCommand {
  final int id;
  final String type;
  final String typeName;
  final String status; // SENT ACCEPTED EXECUTED REJECTED FAILED EXPIRED TIMEOUT BLOCKED
  final String? message;
  final String requestedBy;
  final DateTime createdAt;
  final DateTime? completedAt, confirmedAt;

  FarmCommand.fromJson(Map<String, dynamic> j)
      : id = _i(j['id']),
        type = j['type'] as String,
        typeName = j['typeName'] as String? ?? j['type'] as String,
        status = j['status'] as String,
        message = j['message'] as String?,
        requestedBy = j['requestedBy'] as String? ?? '',
        createdAt = _date(j['createdAt'])!,
        completedAt = _date(j['completedAt']),
        confirmedAt = _date(j['confirmedAt']);

  bool get inFlight => status == 'PENDING' || status == 'SENT' || status == 'ACCEPTED';
  bool get success => status == 'EXECUTED';

  String get statusLabel => switch (status) {
        'PENDING' || 'SENT' => 'Đã gửi, chờ PLC',
        'ACCEPTED' => 'PLC đã nhận',
        'EXECUTED' => confirmedAt != null ? 'Đã thực hiện ✓' : 'Đã thực hiện',
        'REJECTED' => 'PLC từ chối',
        'FAILED' => 'Lỗi',
        'EXPIRED' => 'Hết hạn',
        'TIMEOUT' => 'Không phản hồi',
        'BLOCKED' => 'Bị chặn',
        _ => status,
      };
}

class Stat {
  final double avg, min, max;
  Stat.fromJson(Map<String, dynamic> j)
      : avg = _d(j['avg']),
        min = _d(j['min']),
        max = _d(j['max']);
}

class HistoryPoint {
  final DateTime time;
  final Stat? coopTemp, airHumidity, waterLevel, waterTemp;

  HistoryPoint.fromJson(Map<String, dynamic> j)
      : time = _date(j['time'])!,
        coopTemp = _stat(j['coopTemp']),
        airHumidity = _stat(j['airHumidity']),
        waterLevel = _stat(j['waterLevel']),
        waterTemp = _stat(j['waterTemp']);

  static Stat? _stat(dynamic v) => v == null ? null : Stat.fromJson(v as Map<String, dynamic>);
}

class RuntimeSegment {
  final DateTime start, end;
  RuntimeSegment.fromJson(Map<String, dynamic> j)
      : start = _date(j['start'])!,
        end = _date(j['end'])!;
}

class DeviceRuntime {
  final String device, deviceName;
  final int onSeconds, starts;
  final List<RuntimeSegment> segments;

  DeviceRuntime.fromJson(Map<String, dynamic> j)
      : device = j['device'] as String,
        deviceName = j['deviceName'] as String,
        onSeconds = _i(j['onSeconds']),
        starts = _i(j['starts']),
        segments = (j['segments'] as List).map((e) => RuntimeSegment.fromJson(e as Map<String, dynamic>)).toList();
}

class DeviceEvent {
  final int id;
  final String device, deviceName, source;
  final bool turnedOn;
  final String? username;
  final DateTime occurredAt;

  DeviceEvent.fromJson(Map<String, dynamic> j)
      : id = _i(j['id']),
        device = j['device'] as String,
        deviceName = j['deviceName'] as String,
        source = j['source'] as String,
        turnedOn = j['turnedOn'] as bool,
        username = j['username'] as String?,
        occurredAt = _date(j['occurredAt'])!;
}

class Setting {
  final String key, unit, description;
  final double value, defaultValue, min, max;

  Setting.fromJson(Map<String, dynamic> j)
      : key = j['key'] as String,
        unit = j['unit'] as String? ?? '',
        description = j['description'] as String? ?? '',
        value = _d(j['value']),
        defaultValue = _d(j['defaultValue']),
        min = _d(j['min']),
        max = _d(j['max']);
}

class PageResult<T> {
  final List<T> items;
  final int page, totalPages, totalItems;
  PageResult(this.items, this.page, this.totalPages, this.totalItems);

  factory PageResult.fromJson(Map<String, dynamic> j, T Function(Map<String, dynamic>) f) => PageResult(
        (j['items'] as List).map((e) => f(e as Map<String, dynamic>)).toList(),
        _i(j['page']),
        _i(j['totalPages']),
        _i(j['totalItems']),
      );

  bool get hasMore => page + 1 < totalPages;
}
