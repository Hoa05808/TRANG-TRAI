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
