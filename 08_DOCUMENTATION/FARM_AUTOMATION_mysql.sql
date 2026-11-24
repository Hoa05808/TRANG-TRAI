-- =========================================================
-- FARM_AUTOMATION
-- MySQL Community Server 8.0+
-- Database for Mitsubishi FX3U + ESP32 Gateway + Spring Boot + Flutter
-- Run this script in MySQL Workbench or mysql CLI.
-- =========================================================

CREATE DATABASE IF NOT EXISTS FARM_AUTOMATION
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE FARM_AUTOMATION;

-- 1. Roles and user accounts
CREATE TABLE IF NOT EXISTS vai_tro (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ma_vai_tro VARCHAR(40) NOT NULL,
    ten_vai_tro VARCHAR(100) NOT NULL,
    mo_ta VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vai_tro_ma (ma_vai_tro)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tai_khoan (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    vai_tro_id BIGINT UNSIGNED NOT NULL,
    ten_dang_nhap VARCHAR(80) NOT NULL,
    email VARCHAR(150) NULL,
    so_dien_thoai VARCHAR(30) NULL,
    mat_khau_hash VARCHAR(255) NOT NULL,
    ho_ten VARCHAR(150) NOT NULL,
    trang_thai ENUM('ACTIVE','LOCKED','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    lan_dang_nhap_cuoi DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tai_khoan_username (ten_dang_nhap),
    UNIQUE KEY uk_tai_khoan_email (email),
    CONSTRAINT fk_tai_khoan_vai_tro
      FOREIGN KEY (vai_tro_id) REFERENCES vai_tro(id)
      ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 2. Farm, zones and gateways
CREATE TABLE IF NOT EXISTS trang_trai (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ma_trang_trai VARCHAR(50) NOT NULL,
    ten_trang_trai VARCHAR(150) NOT NULL,
    dia_chi VARCHAR(255) NULL,
    mo_ta TEXT NULL,
    trang_thai ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trang_trai_ma (ma_trang_trai)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS khu_vuc (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trang_trai_id BIGINT UNSIGNED NOT NULL,
    ma_khu_vuc VARCHAR(50) NOT NULL,
    ten_khu_vuc VARCHAR(150) NOT NULL,
    loai_khu_vuc VARCHAR(80) NULL,
    mo_ta TEXT NULL,
    trang_thai ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    UNIQUE KEY uk_khu_vuc_ma (trang_trai_id, ma_khu_vuc),
    CONSTRAINT fk_khu_vuc_trang_trai
      FOREIGN KEY (trang_trai_id) REFERENCES trang_trai(id)
      ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gateway (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    khu_vuc_id BIGINT UNSIGNED NOT NULL,
    ma_gateway VARCHAR(80) NOT NULL,
    ten_gateway VARCHAR(150) NOT NULL,
    loai_thiet_bi VARCHAR(80) NOT NULL DEFAULT 'ESP32',
    mqtt_client_id VARCHAR(120) NULL,
    dia_chi_ip VARCHAR(45) NULL,
    trang_thai_ket_noi ENUM('ONLINE','OFFLINE','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
    heartbeat_cuoi DATETIME NULL,
    firmware_version VARCHAR(40) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gateway_ma (ma_gateway),
    UNIQUE KEY uk_gateway_mqtt_client (mqtt_client_id),
    CONSTRAINT fk_gateway_khu_vuc
      FOREIGN KEY (khu_vuc_id) REFERENCES khu_vuc(id)
      ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 3. PLC and controllable equipment
CREATE TABLE IF NOT EXISTS plc (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    gateway_id BIGINT UNSIGNED NOT NULL,
    ma_plc VARCHAR(80) NOT NULL,
    ten_plc VARCHAR(150) NOT NULL,
    hang_san_xuat VARCHAR(80) NOT NULL DEFAULT 'Mitsubishi',
    model VARCHAR(80) NULL,
    giao_thuc VARCHAR(40) NOT NULL DEFAULT 'MODBUS_RTU',
    dia_chi_slave INT UNSIGNED NULL,
    trang_thai_ket_noi ENUM('ONLINE','OFFLINE','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
    heartbeat_cuoi DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plc_ma (ma_plc),
    CONSTRAINT fk_plc_gateway
      FOREIGN KEY (gateway_id) REFERENCES gateway(id)
      ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS thiet_bi (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plc_id BIGINT UNSIGNED NOT NULL,
    khu_vuc_id BIGINT UNSIGNED NOT NULL,
    ma_thiet_bi VARCHAR(80) NOT NULL,
    ten_thiet_bi VARCHAR(150) NOT NULL,
    loai_thiet_bi ENUM('PUMP','FAN','LIGHT','VALVE','BUZZER','OTHER') NOT NULL,
    plc_output_address VARCHAR(20) NULL,
    feedback_address VARCHAR(20) NULL,
    che_do_ho_tro SET('AUTO','MANUAL','REMOTE') NOT NULL DEFAULT 'AUTO,MANUAL,REMOTE',
    trang_thai ENUM('ACTIVE','INACTIVE','MAINTENANCE') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_thiet_bi_ma (ma_thiet_bi),
    KEY idx_thiet_bi_khu_vuc (khu_vuc_id),
    CONSTRAINT fk_thiet_bi_plc
      FOREIGN KEY (plc_id) REFERENCES plc(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_thiet_bi_khu_vuc
      FOREIGN KEY (khu_vuc_id) REFERENCES khu_vuc(id)
      ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 4. Sensor catalog and sensor measurements
CREATE TABLE IF NOT EXISTS loai_cam_bien (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ma_cam_bien VARCHAR(50) NOT NULL,
    ten_cam_bien VARCHAR(120) NOT NULL,
    don_vi VARCHAR(30) NULL,
    mo_ta VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_loai_cam_bien_ma (ma_cam_bien)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cam_bien (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plc_id BIGINT UNSIGNED NOT NULL,
    khu_vuc_id BIGINT UNSIGNED NOT NULL,
    loai_cam_bien_id BIGINT UNSIGNED NOT NULL,
    ma_cam_bien VARCHAR(80) NOT NULL,
    ten_cam_bien VARCHAR(150) NOT NULL,
    plc_register VARCHAR(20) NULL,
    he_so_nhan DECIMAL(12,4) NOT NULL DEFAULT 1.0000,
    do_lech DECIMAL(12,4) NOT NULL DEFAULT 0.0000,
    nguong_thap DECIMAL(14,4) NULL,
    nguong_cao DECIMAL(14,4) NULL,
    trang_thai ENUM('ACTIVE','INACTIVE','FAULT') NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cam_bien_ma (ma_cam_bien),
    KEY idx_cam_bien_khu_vuc (khu_vuc_id),
    CONSTRAINT fk_cam_bien_plc
      FOREIGN KEY (plc_id) REFERENCES plc(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_cam_bien_khu_vuc
      FOREIGN KEY (khu_vuc_id) REFERENCES khu_vuc(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_cam_bien_loai
      FOREIGN KEY (loai_cam_bien_id) REFERENCES loai_cam_bien(id)
      ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS du_lieu_cam_bien (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cam_bien_id BIGINT UNSIGNED NOT NULL,
    gia_tri DECIMAL(14,4) NOT NULL,
    thoi_diem_do DATETIME(3) NOT NULL,
    chat_luong ENUM('GOOD','UNCERTAIN','BAD') NOT NULL DEFAULT 'GOOD',
    nguon_du_lieu ENUM('PLC','GATEWAY','MANUAL') NOT NULL DEFAULT 'PLC',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sensor_time (cam_bien_id, thoi_diem_do),
    KEY idx_sensor_time_global (thoi_diem_do),
    CONSTRAINT fk_du_lieu_cam_bien
      FOREIGN KEY (cam_bien_id) REFERENCES cam_bien(id)
      ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 5. Latest equipment state snapshots (history can be added later)
CREATE TABLE IF NOT EXISTS trang_thai_thiet_bi (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    thiet_bi_id BIGINT UNSIGNED NOT NULL,
    dang_bat BOOLEAN NOT NULL DEFAULT FALSE,
    che_do ENUM('AUTO','MANUAL','REMOTE','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
    co_loi BOOLEAN NOT NULL DEFAULT FALSE,
    ma_loi VARCHAR(40) NULL,
    cap_nhat_luc DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trang_thai_thiet_bi (thiet_bi_id),
    CONSTRAINT fk_trang_thai_thiet_bi
      FOREIGN KEY (thiet_bi_id) REFERENCES thiet_bi(id)
      ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS lich_su_thiet_bi (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    thiet_bi_id BIGINT UNSIGNED NOT NULL,
    dang_bat BOOLEAN NOT NULL,
    che_do ENUM('AUTO','MANUAL','REMOTE','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
    nguon_thay_doi ENUM('PLC_AUTO','LOCAL','APP','SYSTEM') NOT NULL,
    thoi_diem DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_device_history (thiet_bi_id, thoi_diem),
    CONSTRAINT fk_lich_su_thiet_bi
      FOREIGN KEY (thiet_bi_id) REFERENCES thiet_bi(id)
      ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 6. Remote commands and command acknowledgements
CREATE TABLE IF NOT EXISTS lenh_dieu_khien (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ma_lenh VARCHAR(40) NOT NULL,
    thiet_bi_id BIGINT UNSIGNED NOT NULL,
    tai_khoan_id BIGINT UNSIGNED NOT NULL,
    hanh_dong ENUM('ON','OFF','OPEN','CLOSE','SET_AUTO','SET_MANUAL','SET_REMOTE') NOT NULL,
    gia_tri JSON NULL,
    trang_thai ENUM('PENDING','SENT','RECEIVED','COMPLETED','REJECTED','TIMEOUT','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    ly_do_tu_choi VARCHAR(255) NULL,
    tao_luc DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    gui_luc DATETIME(3) NULL,
    xac_nhan_luc DATETIME(3) NULL,
    hoan_thanh_luc DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lenh_ma (ma_lenh),
    KEY idx_lenh_status_time (trang_thai, tao_luc),
    KEY idx_lenh_device_time (thiet_bi_id, tao_luc),
    CONSTRAINT fk_lenh_thiet_bi
      FOREIGN KEY (thiet_bi_id) REFERENCES thiet_bi(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_lenh_tai_khoan
      FOREIGN KEY (tai_khoan_id) REFERENCES tai_khoan(id)
      ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

-- 7. Alarms and acknowledgements
CREATE TABLE IF NOT EXISTS canh_bao (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ma_canh_bao VARCHAR(50) NOT NULL,
    khu_vuc_id BIGINT UNSIGNED NOT NULL,
    cam_bien_id BIGINT UNSIGNED NULL,
    thiet_bi_id BIGINT UNSIGNED NULL,
    muc_do ENUM('INFO','WARNING','CRITICAL') NOT NULL DEFAULT 'WARNING',
    tieu_de VARCHAR(180) NOT NULL,
    noi_dung TEXT NOT NULL,
    gia_tri_thuc_te DECIMAL(14,4) NULL,
    nguong DECIMAL(14,4) NULL,
    trang_thai ENUM('ACTIVE','ACKNOWLEDGED','RESOLVED') NOT NULL DEFAULT 'ACTIVE',
    xay_ra_luc DATETIME(3) NOT NULL,
    xac_nhan_boi BIGINT UNSIGNED NULL,
    xac_nhan_luc DATETIME(3) NULL,
    ket_thuc_luc DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_alarm_status_time (trang_thai, xay_ra_luc),
    CONSTRAINT fk_canh_bao_khu_vuc
      FOREIGN KEY (khu_vuc_id) REFERENCES khu_vuc(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_canh_bao_cam_bien
      FOREIGN KEY (cam_bien_id) REFERENCES cam_bien(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_canh_bao_thiet_bi
      FOREIGN KEY (thiet_bi_id) REFERENCES thiet_bi(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_canh_bao_tai_khoan
      FOREIGN KEY (xac_nhan_boi) REFERENCES tai_khoan(id)
      ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

-- 8. Configurable operating thresholds and audit trail
CREATE TABLE IF NOT EXISTS cai_dat_he_thong (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    khu_vuc_id BIGINT UNSIGNED NOT NULL,
    ma_cai_dat VARCHAR(80) NOT NULL,
    gia_tri VARCHAR(255) NOT NULL,
    don_vi VARCHAR(30) NULL,
    mo_ta VARCHAR(255) NULL,
    cap_nhat_boi BIGINT UNSIGNED NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cai_dat_khu_vuc_ma (khu_vuc_id, ma_cai_dat),
    CONSTRAINT fk_cai_dat_khu_vuc
      FOREIGN KEY (khu_vuc_id) REFERENCES khu_vuc(id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_cai_dat_tai_khoan
      FOREIGN KEY (cap_nhat_boi) REFERENCES tai_khoan(id)
      ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS nhat_ky_he_thong (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tai_khoan_id BIGINT UNSIGNED NULL,
    hanh_dong VARCHAR(100) NOT NULL,
    doi_tuong VARCHAR(100) NULL,
    doi_tuong_id BIGINT UNSIGNED NULL,
    noi_dung JSON NULL,
    dia_chi_ip VARCHAR(45) NULL,
    thoi_diem DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_time (thoi_diem),
    KEY idx_audit_user_time (tai_khoan_id, thoi_diem),
    CONSTRAINT fk_nhat_ky_tai_khoan
      FOREIGN KEY (tai_khoan_id) REFERENCES tai_khoan(id)
      ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

-- 9. Initial role and sensor-type reference data
INSERT INTO vai_tro (ma_vai_tro, ten_vai_tro, mo_ta)
VALUES
    ('ADMIN', 'Quản trị viên', 'Quản lý toàn bộ hệ thống'),
    ('OPERATOR', 'Người vận hành', 'Theo dõi và điều khiển thiết bị được cấp quyền'),
    ('VIEWER', 'Người xem', 'Chỉ xem dữ liệu và trạng thái')
AS new
ON DUPLICATE KEY UPDATE
    ten_vai_tro = new.ten_vai_tro,
    mo_ta = new.mo_ta;

INSERT INTO loai_cam_bien (ma_cam_bien, ten_cam_bien, don_vi, mo_ta)
VALUES
    ('AIR_TEMP', 'Nhiệt độ không khí', '°C', 'Dữ liệu nhiệt độ không khí'),
    ('AIR_HUMIDITY', 'Độ ẩm không khí', '%', 'Độ ẩm tương đối'),
    ('WATER_TEMP', 'Nhiệt độ nước', '°C', 'Nhiệt độ nước'),
    ('WATER_LEVEL', 'Mực nước', NULL, 'Giá trị mực nước hoặc trạng thái mức'),
    ('SOIL_HUMIDITY', 'Độ ẩm đất', '%', 'Độ ẩm đất'),
    ('LIGHT', 'Cường độ ánh sáng', NULL, 'Đơn vị phụ thuộc cảm biến'),
    ('CO2', 'Nồng độ CO2', 'ppm', 'Nồng độ CO2'),
    ('NH3', 'Nồng độ NH3', 'ppm', 'Nồng độ NH3')
AS new
ON DUPLICATE KEY UPDATE
    ten_cam_bien = new.ten_cam_bien,
    don_vi = new.don_vi,
    mo_ta = new.mo_ta;
-- No user account is created here: Spring Boot should store a BCrypt password hash.
-- No real device/register mapping is seeded until the actual FX3U model and wiring are confirmed.

-- =========================================================
-- 10. Bảng dùng bởi backend Spring Boot (09_BACKEND/farmcontrol-backend)
--     Backend chỉ KIỂM TRA cấu trúc (ddl-auto=validate), không tự tạo bảng.
--     Khi sửa entity Java phải sửa phần này cho khớp, rồi chạy lại file.
--     Thời gian lưu theo UTC.
-- =========================================================

CREATE TABLE IF NOT EXISTS app_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100),
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    CONSTRAINT uk_user_username UNIQUE (username)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS refresh_token (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Mẫu dữ liệu cảm biến đã quy đổi đơn vị (D50=325 -> 32.5), lưu mỗi 60 giây
CREATE TABLE IF NOT EXISTS sensor_reading (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    recorded_at   DATETIME(3) NOT NULL,
    coop_temp     DOUBLE,
    air_humidity  DOUBLE,
    water_temp    DOUBLE,
    water_level   DOUBLE,
    soil_humidity DOUBLE,
    light_lux     INT,
    co2_ppm       INT,
    nh3_ppm       INT,
    data_valid    BOOLEAN     NOT NULL DEFAULT TRUE,
    KEY idx_sensor_reading_time (recorded_at)
) ENGINE=InnoDB;

-- Lệnh điều khiển từ xa và vòng đời (SENT/ACCEPTED/EXECUTED/REJECTED/FAILED/EXPIRED/TIMEOUT/BLOCKED)
CREATE TABLE IF NOT EXISTS device_command (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_type      VARCHAR(30)  NOT NULL,
    plc_code          INT          NOT NULL,
    command_value     INT          NOT NULL DEFAULT 0,
    request_id        INT          NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    result_code       INT,
    message           VARCHAR(255),
    requested_by_id   BIGINT,
    requested_by_name VARCHAR(50)  NOT NULL,
    created_at        DATETIME(3)  NOT NULL,
    sent_at           DATETIME(3),
    acked_at          DATETIME(3),
    completed_at      DATETIME(3),
    confirmed_at      DATETIME(3),
    expires_at        DATETIME(3)  NOT NULL,
    KEY idx_command_status (status),
    KEY idx_command_created (created_at),
    KEY idx_command_request (request_id)
) ENGINE=InnoDB;

-- Lịch sử bật/tắt thiết bị theo telemetry thực tế (source = REMOTE hoặc PLC)
CREATE TABLE IF NOT EXISTS device_event (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    device      VARCHAR(20) NOT NULL,
    turned_on   BOOLEAN     NOT NULL,
    source      VARCHAR(20) NOT NULL,
    command_id  BIGINT,
    username    VARCHAR(50),
    occurred_at DATETIME(3) NOT NULL,
    KEY idx_device_event_time (occurred_at),
    KEY idx_device_event_device (device, occurred_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS alert (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type      VARCHAR(40)  NOT NULL,
    severity        VARCHAR(10)  NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    title           VARCHAR(120) NOT NULL,
    message         VARCHAR(500),
    alert_value     DOUBLE,
    raised_at       DATETIME(3)  NOT NULL,
    last_seen_at    DATETIME(3)  NOT NULL,
    resolved_at     DATETIME(3),
    acknowledged_at DATETIME(3),
    acknowledged_by VARCHAR(50),
    KEY idx_alert_status (status, alert_type),
    KEY idx_alert_raised (raised_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS app_setting (
    setting_key   VARCHAR(60)  NOT NULL PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    updated_by    VARCHAR(50)
) ENGINE=InnoDB;

-- Token Firebase Cloud Messaging của điện thoại (giai đoạn 6)
CREATE TABLE IF NOT EXISTS push_token (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    token        VARCHAR(512) NOT NULL,
    platform     VARCHAR(10)  NOT NULL,
    created_at   DATETIME(3)  NOT NULL,
    last_seen_at DATETIME(3)  NOT NULL,
    CONSTRAINT uk_push_token UNIQUE (token),
    CONSTRAINT fk_push_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB;

SELECT 'FARM_AUTOMATION database created successfully' AS ket_qua;
SELECT * FROM vai_tro;

SELECT * FROM loai_cam_bien;
