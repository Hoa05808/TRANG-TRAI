package vn.farmcontrol.user;

/**
 * OWNER    : chủ trang trại - toàn quyền, quản lý tài khoản và thông số.
 * OPERATOR : người vận hành - giám sát, điều khiển thiết bị, xác nhận cảnh báo.
 * VIEWER   : chỉ xem.
 */
public enum Role {
    OWNER, OPERATOR, VIEWER
}
