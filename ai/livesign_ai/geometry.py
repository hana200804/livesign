"""Góc nhìn của biển trong ảnh Mapillary, tách từ notebook PoC (00_visibility_poc.ipynb).

Quy ước: góc tính bằng độ so với hướng camera (compass_angle), dương = bên phải.
`focal` là tiêu cự Mapillary chuẩn hóa theo cạnh dài của ảnh; thiếu thì dùng 0.8.
"""

import math

DEFAULT_FOCAL = 0.8


def _focal(focal):
    # NaN (từ pandas) hoặc 0 / None đều coi như thiếu.
    return focal if focal and focal == focal else DEFAULT_FOCAL


def box_angle(cx, w, h, pano, focal=None):
    """Góc của điểm có hoành độ `cx` (px) trong ảnh rộng `w`, cao `h`."""
    if pano:
        return (cx / w - 0.5) * 360
    return math.degrees(math.atan((cx - w / 2) / (_focal(focal) * max(w, h))))


def expected_x(off_deg, w, h, pano, focal=None):
    """Hoành độ (px) mà biển lệch `off_deg` độ so với hướng camera phải xuất hiện."""
    if pano:
        return ((off_deg / 360 + 0.5) % 1) * w
    return w / 2 + _focal(focal) * max(w, h) * math.tan(math.radians(off_deg))


def angle_diff(a, b):
    """Chênh lệch nhỏ nhất giữa hai góc, trong khoảng [0, 180]."""
    return abs((a - b + 540) % 360 - 180)
