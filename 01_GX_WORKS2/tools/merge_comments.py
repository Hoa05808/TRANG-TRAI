"""Gộp comment mới vào file comment GX Works2 xuất ra, GIỮ NGUYÊN comment cũ.

    python -X utf8 tools/merge_comments.py COMMENT.csv COMMENT_FIXED.csv

Chỉ thêm thiết bị CHƯA có comment. Thiết bị đã có comment không bị đổi.
"""
import re
import sys

NEW = {
    "X007": "REMOTE_KEY - Công tắc REMOTE",
    "M36": "REMOTE_PUMP_LATCH_giữ bơm từ xa",
    "M37": "HMI_BUZZER_SILENCE_nút tắt còi",
    "M38": "BUZZER_SILENCED_còi đã tắt",
    "T0": "PUMP_MAX_TIMER_bơm quá giờ D4",
    "T2": "GATEWAY_WDT_mất gateway 30s",
    "T3": "ALARM_TEMP_DELAY_trễ nhiệt cao",
    "T4": "ALARM_WATER_DELAY_trễ nước thấp",
    "T5": "ALARM_DOOR_DELAY_trễ cửa mở",
    "D70": "PUMP_TIMER_SET_đặt T0 = D4x10",
    "D72": "ALARM_TIMER_SET_đặt T3-T5=D5x10",
    "D211": "GATEWAY_HB_LAST_D202 lần trước",
    "D311": "RUNTIME_MIN_COUNT_đếm phút->D309",
    # --- Cho ăn (bổ sung 26/11/2026) ---
    "X010": "FISH_FEED_OK - Thùng cá còn TA",
    "X011": "DUCK_FEED_OK - Thùng vịt còn TA",
    "X012": "CHICKEN_FEED_OK - Thùng gà có TA",
    "Y010": "FISH_FEEDER - Máy cho cá ăn",
    "Y011": "FISH_FEED_VALVE - Van TA cá",
    "Y012": "DUCK_FEED_VALVE - Van TA vịt",
    "Y013": "CHICKEN_FEED_VALVE - Van TA gà",
    "M200": "FISH_FEED_TIME_đến giờ cá ăn",
    "M201": "DUCK_FEED_TIME_đến giờ vịt ăn",
    "M202": "CHICKEN_FEED_TIME_đến giờ gà ăn",
    "M203": "FEED_ENABLE_cho phép cho ăn",
    "M210": "FISH_FEED_CYCLE_đang cho cá ăn",
    "M211": "DUCK_FEED_CYCLE_đang cho vịt ăn",
    "M212": "CHICKEN_FEED_CYCLE_đang cho gà",
    "M213": "FISH_VALVE_DONE_máy chạy thêm",
    "M219": "MIDNIGHT_xóa đếm lần cho ăn",
    "M220": "FISH_FEED_LOW_thùng cá sắp hết",
    "M221": "DUCK_FEED_LOW_thùng vịt sắp hết",
    "M222": "CHICKEN_FEED_LOW_thùng gà hết",
    "M224": "FEED_LOW_ANY_có thùng sắp hết",
    "M230": "REMOTE_FISH_START_cho cá ăn",
    "M231": "REMOTE_FISH_STOP_dừng cá",
    "M232": "REMOTE_DUCK_START_cho vịt ăn",
    "M233": "REMOTE_DUCK_STOP_dừng vịt",
    "M234": "REMOTE_CHICKEN_START_cho gà ăn",
    "M235": "REMOTE_CHICKEN_STOP_dừng gà",
    "M240": "HMI_FISH_START_nút cho cá ăn",
    "M241": "HMI_FISH_STOP_nút dừng cá",
    "M242": "HMI_DUCK_START_nút cho vịt ăn",
    "M243": "HMI_DUCK_STOP_nút dừng vịt",
    "M244": "HMI_CHICKEN_START_nút cho gà",
    "M245": "HMI_CHICKEN_STOP_nút dừng gà",
    "T10": "FISH_VALVE_DELAY_trễ mở van 3s",
    "T11": "FISH_VALVE_TIMER_van cá D410",
    "T12": "FISH_RUNON_TIMER_máy chạy D411",
    "T13": "DUCK_VALVE_TIMER_van vịt D412",
    "T14": "CHICKEN_VALVE_TIMER_van gà D413",
    "T15": "FISH_LOW_DELAY_trễ 30s",
    "T16": "DUCK_LOW_DELAY_trễ 30s",
    "T17": "CHICKEN_LOW_DELAY_trễ 30s",
    "D74": "FISH_VALVE_SET_D410x10",
    "D76": "FISH_RUNON_SET_D411x10",
    "D78": "DUCK_VALVE_SET_D412x10",
    "D80": "CHICKEN_VALVE_SET_D413x10",
    "D82": "CLOCK_HHMM_giờ PLC dạng HHMM",
    "D110": "STATUS_FISH_FEEDER_máy cá",
    "D111": "STATUS_FISH_VALVE_van cá",
    "D112": "STATUS_DUCK_VALVE_van vịt",
    "D113": "STATUS_CHICKEN_VALVE_van gà",
    "D114": "FEED_LOW_BITS_1cá 2vịt 4gà",
    "D115": "FISH_FEED_TODAY_lần hôm nay",
    "D116": "DUCK_FEED_TODAY_lần hôm nay",
    "D117": "CHICKEN_FEED_TODAY_lần hôm nay",
    "D118": "FEED_ENABLE_1=cho phép cho ăn",
    "D119": "PLC_CLOCK_HHMM_giờ PLC",
    "D320": "STAT_FISH_FEED_tổng lần cá ăn",
    "D321": "STAT_DUCK_FEED_tổng lần vịt ăn",
    "D322": "STAT_CHICKEN_FEED_tổng lần gà",
    "D400": "FISH_TIME_1_giờ cá ăn 1 HHMM",
    "D401": "FISH_TIME_2_giờ cá ăn 2 HHMM",
    "D402": "DUCK_TIME_1_giờ vịt ăn 1 HHMM",
    "D403": "DUCK_TIME_2_giờ vịt ăn 2 HHMM",
    "D404": "CHICKEN_TIME_1_giờ gà 1 HHMM",
    "D405": "CHICKEN_TIME_2_giờ gà 2 HHMM",
    "D406": "CHICKEN_TIME_3_giờ gà 3 HHMM",
    "D410": "FISH_VALVE_SEC_giây mở van cá",
    "D411": "FISH_RUNON_SEC_giây máy chạy",
    "D412": "DUCK_VALVE_SEC_giây van vịt",
    "D413": "CHICKEN_VALVE_SEC_giây van gà",
    # --- Van nước Y4 xuống máng gà ---
    "M246": "HMI_WATER_OPEN_nút mở van nước",
    "M247": "HMI_WATER_CLOSE_nút đóng van",
    "T18": "WATER_VALVE_TIMER_van nước D414",
    "D84": "WATER_VALVE_SET_D414x10",
    "D414": "WATER_VALVE_SEC_giây van nước gà",
}
ORDER = "XYMSTCD"  # thứ tự nhóm thiết bị như GX Works2


def key(dev):
    m = re.match(r"^([A-Z]+)(\d+)$", dev)
    kind, num = m.group(1), m.group(2)
    base = 8 if kind in ("X", "Y") else 10
    return (ORDER.index(kind[0]) if kind[0] in ORDER else 99, int(num, base))


def main(src, dst):
    raw = open(src, encoding="utf-16", newline="").read()
    nl = "\r\n" if "\r\n" in raw else "\n"  # giữ đúng kiểu xuống dòng của file GX Works2 xuất
    lines = raw.splitlines()
    head, body = lines[:2], [l for l in lines[2:] if l.strip()]
    have = {re.match(r'^"([^"]+)"', l).group(1): l for l in body}
    for dev, text in NEW.items():
        if len(text) > 32:
            raise ValueError(f"{dev}: comment dài {len(text)} ký tự, tối đa 32")
        if dev not in have:
            have[dev] = f'"{dev}"\t"{text}"'
    out = head + [have[d] for d in sorted(have, key=key)]
    with open(dst, "wb") as f:
        f.write(b"\xff\xfe" + (nl.join(out) + nl).encode("utf-16-le"))
    print(f"Cũ: {len(body)}  Thêm: {len(have) - len(body)}  Tổng: {len(have)} -> {dst}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
