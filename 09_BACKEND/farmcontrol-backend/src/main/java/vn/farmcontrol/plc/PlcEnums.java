package vn.farmcontrol.plc;

public final class PlcEnums {

    private PlcEnums() {
    }

    /** D105 STATUS_OPERATION_MODE: 0 = Stop, 1 = Auto, 2 = Manual. */
    public enum OperationMode {
        STOP, AUTO, MANUAL, UNKNOWN;

        public static OperationMode fromRegister(int v) {
            return switch (v) {
                case 0 -> STOP;
                case 1 -> AUTO;
                case 2 -> MANUAL;
                default -> UNKNOWN;
            };
        }
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
