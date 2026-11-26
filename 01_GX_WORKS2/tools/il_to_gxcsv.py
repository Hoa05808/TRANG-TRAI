"""Chuyển chương trình IL (dạng text) thành file CSV để GX Works2 đọc (Edit > Read from CSV File).

Cách dùng:
    python il_to_gxcsv.py MAIN_FIXED_IL.txt MAIN_FIXED.csv
    python il_to_gxcsv.py MAIN_FIXED_IL.txt MAIN_FIXED_KHONG_TIEU_DE.csv --no-statements
    python il_to_gxcsv.py --check MAIN.csv      # kiểm tra cách tính step trên file GX Works2 xuất ra

Định dạng IL: mỗi dòng "LENH toán_hạng1 toán_hạng2 ...". Dòng bắt đầu bằng '#' là Line Statement
cho lệnh ngay sau. Phần sau ';' là ghi chú, bỏ qua. Chỉ hỗ trợ các lệnh FX3U dùng trong dự án.
"""
import re
import sys

ONE_STEP = {"LD", "LDI", "AND", "ANI", "OR", "ORI", "ANB", "ORB", "MPS", "MRD", "MPP", "END", "INV"}
PULSE = {"LDP", "LDF", "ANDP", "ANDF", "ORP", "ORF"}
CMP = re.compile(r"^(LD|AND|OR)(=|<>|>=|<=|>|<)$")
FUNC = {"MOV": 5, "INC": 3, "DEC": 3, "MUL": 7, "DIV": 7, "ADD": 7, "SUB": 7, "CMP": 7, "ZRST": 5}
OPERANDS = {"MOV": 2, "INC": 1, "DEC": 1, "MUL": 3, "DIV": 3, "ADD": 3, "SUB": 3, "CMP": 3, "ZRST": 2}


def dev_num(dev):
    m = re.match(r"^[A-Z]+(\d+)$", dev)
    return int(m.group(1)) if m else 0


def step_size(ins, ops):
    if ins in ONE_STEP:
        return 1
    if ins in PULSE:
        return 2
    if CMP.match(ins):
        return 5
    if ins == "OUT":
        d = ops[0]
        if d[0] in "TC":
            return 3
        if d.startswith("M") and dev_num(d) >= 1536:
            return 2
        return 1
    if ins in ("SET", "RST"):
        d = ops[0]
        if d[0] in "YS" or (d[0] == "M" and dev_num(d) < 1536):
            return 1
        if d[0] in "TC" or d[0] == "M":
            return 2
        return 3  # RST D
    if ins in FUNC:
        return FUNC[ins]
    raise ValueError(f"Lệnh chưa hỗ trợ: {ins}")


def expected_operands(ins):
    if ins in ONE_STEP and ins not in ("LD", "LDI", "AND", "ANI", "OR", "ORI"):
        return 0
    if CMP.match(ins):
        return 2
    if ins == "OUT":
        return None  # 1 hoặc 2 (timer)
    return OPERANDS.get(ins, 1)


def norm_dev(d):
    # X/Y viết 3 chữ số bát phân như GX Works2 xuất: X7 -> X007
    m = re.match(r"^([XY])([0-7]+)$", d)
    return f"{m.group(1)}{int(m.group(2)):03d}" if m else d


def parse_il(path):
    rows, stmt = [], ""
    with open(path, encoding="utf-8") as f:
        for n, raw in enumerate(f, 1):
            line = raw.split(";", 1)[0].strip()
            if raw.strip().startswith("#"):
                stmt = raw.strip()[1:].strip()
                if len(stmt) > 64:
                    raise ValueError(f"Dòng {n}: Line Statement dài {len(stmt)} ký tự, GX Works2 chỉ nhận tối đa 64")
                continue
            if not line:
                continue
            parts = line.split()
            ins, ops = parts[0].upper(), [norm_dev(p.upper()) for p in parts[1:]]
            exp = expected_operands(ins)
            if exp is not None and len(ops) != exp:
                raise ValueError(f"Dòng {n}: {ins} cần {exp} toán hạng, có {len(ops)}")
            if ins == "OUT" and not (len(ops) == 1 or (len(ops) == 2 and ops[0][0] in "TC")):
                raise ValueError(f"Dòng {n}: OUT sai toán hạng")
            rows.append((stmt, ins, ops))
            stmt = ""
    if not rows or rows[-1][1] != "END":
        raise ValueError("Chương trình phải kết thúc bằng END")
    return rows


def q(s):
    return '"' + s.replace('"', '""') + '"'


def write_csv(rows, out, project="TRANG TRAI", statements=True):
    lines = [q(project), q("PLC Information:") + "\t" + q("FXCPU FX3U/FX3UC"),
             "\t".join(q(h) for h in ["Step No.", "Line Statement", "Instruction", "I/O(Device)",
                                      "Blank", "PI Statement", "Note"])]
    step = 0
    for stmt, ins, ops in rows:
        first = ops[0] if ops else ""
        # GX Works2 bắt buộc Line Statement nằm trên một dòng riêng (không có lệnh / thiết bị)
        if stmt and statements:
            lines.append("\t".join(q(x) for x in ["", stmt, "", "", "", "", ""]))
        lines.append("\t".join(q(x) for x in [str(step), "", ins, first, "", "", ""]))
        for o in ops[1:]:
            lines.append("\t".join(q(x) for x in ["", "", "", o, "", "", ""]))
        step += step_size(ins, ops)
    data = ("\r\n".join(lines) + "\r\n").encode("utf-16-le")
    with open(out, "wb") as f:
        f.write(b"\xff\xfe" + data)
    return step


def check(path):
    """Đối chiếu cách tính step với số step GX Works2 ghi trong file CSV xuất ra."""
    text = open(path, "rb").read().decode("utf-16")
    recs = []
    for line in text.splitlines()[3:]:
        f = [c.strip('"') for c in line.split("\t")]
        if len(f) < 4:
            continue
        if f[0]:
            recs.append([int(f[0]), f[2], [f[3]] if f[3] else []])
        elif recs and f[3]:  # dòng toán hạng tiếp theo (bỏ qua dòng chỉ có Line Statement)
            recs[-1][2].append(f[3])
    bad = 0
    for a, b in zip(recs, recs[1:]):
        if a[0] + step_size(a[1], a[2]) != b[0]:
            bad += 1
            print(f"Lệch tại step {a[0]}: {a[1]} {a[2]}")
    print(f"{len(recs)} lệnh, {bad} chỗ lệch")
    return bad


if __name__ == "__main__":
    if sys.argv[1] == "--check":
        sys.exit(1 if check(sys.argv[2]) else 0)
    rows = parse_il(sys.argv[1])
    total = write_csv(rows, sys.argv[2], statements="--no-statements" not in sys.argv)
    print(f"Đã ghi {sys.argv[2]}: {len(rows)} lệnh, {total} step (FX3U tối đa 64000)")
