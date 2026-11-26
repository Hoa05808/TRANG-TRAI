package vn.farmcontrol.plc;

public final class PlcEnums {

    private PlcEnums() {
    }

    /** D105 STATUS_OPERATION_MODE: 0 = Stop, 1 = Auto, 2 = Manual, 3 = Remote (công tắc REMOTE trên tủ). */
    public enum OperationMode {
        STOP, AUTO, MANUAL, REMOTE, UNKNOWN;

        public static OperationMode fromRegister(int v) {
            return switch (v) {
                case 0 -> STOP;
                case 1 -> AUTO;
                case 2 -> MANUAL;
                case 3 -> REMOTE;
                default -> UNKNOWN;
            };
        }
    }

    /** Mô tả mã lỗi D107 do PLC ghi. */
    public static String errorText(int code) {
        return switch (code) {
            case 0 -> "Không lỗi";
            case 1 -> "Cảm biến mực nước mâu thuẫn (thấp và cao cùng báo)";
            case 2 -> "Đang dừng khẩn cấp";
            default -> "Mã lỗi " + code;
        };
    }

    /** Mô tả mã cảnh báo D108 do PLC ghi (mã ưu tiên cao nhất đang có). */
    public static String alarmText(int code) {
        return switch (code) {
            case 0 -> "Không cảnh báo";
            case 3 -> "Bơm chạy quá thời gian cho phép (D4), PLC đã dừng bơm. Nhấn START tại tủ để xóa";
            case 4 -> "Nhiệt độ chuồng vượt ngưỡng cảnh báo D6";
            case 5 -> "Mực nước thấp nhưng bơm không chạy";
            case 6 -> "Cửa chuồng đang mở";
            default -> "Mã cảnh báo " + code;
        };
    }

    /** D106 STATUS_PLC_STATE: 0 = Stop, 1 = Run, 2 = Error. */
    public enum PlcState {
        STOP, RUN, ERROR, UNKNOWN;

        public static PlcState fromRegister(int v) {
            return switch (v) {
                case 0 -> STOP;
                case 1 -> RUN;
                case 2 -> ERROR;
                default -> UNKNOWN;
            };
        }
    }
}
