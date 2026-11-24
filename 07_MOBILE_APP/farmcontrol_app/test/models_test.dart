import 'package:farmcontrol_app/core/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Dashboard đọc đúng JSON từ backend và hiểu trạng thái UNKNOWN', () {
    final d = Dashboard.fromJson({
      'connection': 'OFFLINE',
      'dataFresh': false,
      'lastUpdate': '2026-11-24T07:30:00Z',
      'gatewayMode': 'mqtt',
      'sensors': {'coopTemp': 32.5, 'airHumidity': 75.0, 'waterLevel': 65.0, 'tempSensorOk': true, 'dataValid': true},
      'devices': [
        {'id': 'PUMP_1', 'name': 'Máy bơm nước', 'plcOutput': 'Y0', 'state': 'UNKNOWN', 'onSince': null},
      ],
      'mode': 'UNKNOWN',
      'plcState': 'UNKNOWN',
      'remoteEnabled': false,
      'activeAlerts': 1,
    });
    expect(d.online, isFalse);
    expect(d.connection, ConnectionStatus.offline);
    expect(d.sensors!.coopTemp, 32.5);
    expect(d.device('PUMP_1')!.state, DeviceState.unknown);
    expect(d.modeLabel, 'KHÔNG RÕ');
  });

  test('Lệnh: trạng thái đang chờ và hoàn tất', () {
    FarmCommand cmd(String status, {String? confirmed}) => FarmCommand.fromJson({
          'id': 1,
          'type': 'PUMP_ON',
          'typeName': 'Bật bơm',
          'status': status,
          'requestedBy': 'owner',
          'createdAt': '2026-11-24T07:30:00Z',
          'confirmedAt': confirmed,
        });
    expect(cmd('SENT').inFlight, isTrue);
    expect(cmd('ACCEPTED').inFlight, isTrue);
    expect(cmd('EXECUTED').inFlight, isFalse);
    expect(cmd('EXECUTED', confirmed: '2026-11-24T07:30:02Z').statusLabel, 'Đã thực hiện ✓');
    expect(cmd('TIMEOUT').success, isFalse);
  });

  test('Vai trò: chỉ xem không được điều khiển', () {
    expect(roleFrom('VIEWER').canControl, isFalse);
    expect(roleFrom('OPERATOR').canControl, isTrue);
    expect(roleFrom('OWNER').isOwner, isTrue);
  });
}
