"""Phép tính địa lý nhỏ: khoảng cách mét, phương vị, khoảng cách điểm tới tia nhìn.

Tọa độ luôn (lon, lat) WGS84. Phạm vi vài chục mét nên dùng mặt phẳng tiếp tuyến
quanh camera; không so độ kinh/vĩ trực tiếp với ngưỡng mét.
"""

import math

R = 6_371_008.8


def local_xy(lon0, lat0, lon, lat):
    """Điểm (lon, lat) trong hệ mét (đông, bắc) gốc tại (lon0, lat0)."""
    x = math.radians(lon - lon0) * math.cos(math.radians((lat + lat0) / 2)) * R
    y = math.radians(lat - lat0) * R
    return x, y


def distance_m(lon1, lat1, lon2, lat2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 2 * R * math.asin(math.sqrt(a))


def bearing_deg(lon1, lat1, lon2, lat2):
    """Phương vị từ điểm 1 tới điểm 2, Bắc=0, thuận chiều kim đồng hồ, [0, 360)."""
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dl = math.radians(lon2 - lon1)
    y = math.sin(dl) * math.cos(p2)
    x = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return math.degrees(math.atan2(y, x)) % 360


def ray_offsets(cam_lon, cam_lat, ray_bearing, lon, lat):
    """(khoảng dọc tia, khoảng vuông góc tới tia) theo mét. Dọc tia < 0 là phía sau camera."""
    x, y = local_xy(cam_lon, cam_lat, lon, lat)
    b = math.radians(ray_bearing)
    dx, dy = math.sin(b), math.cos(b)
    return x * dx + y * dy, abs(x * dy - y * dx)
