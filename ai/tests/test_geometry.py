import math

import pytest

from livesign_ai.geometry import angle_diff, box_angle, expected_x

W, H = 3840, 2160


@pytest.mark.parametrize("off_deg", [-35.0, -8.6, 0.0, 8.6, 30.0])
def test_perspective_round_trip(off_deg):
    x = expected_x(off_deg, W, H, pano=False, focal=0.348)
    assert box_angle(x, W, H, pano=False, focal=0.348) == pytest.approx(off_deg)


@pytest.mark.parametrize("off_deg", [-179.0, -59.6, 0.0, 90.0, 179.0])
def test_pano_round_trip(off_deg):
    x = expected_x(off_deg, 7680, 3840, pano=True)
    assert box_angle(x, 7680, 3840, pano=True) == pytest.approx(off_deg)


@pytest.mark.parametrize("missing", [None, 0, math.nan])
def test_missing_focal_uses_default(missing):
    assert expected_x(10, W, H, False, missing) == pytest.approx(expected_x(10, W, H, False, 0.8))


def test_center_is_zero():
    assert box_angle(W / 2, W, H, pano=False, focal=0.5) == 0


@pytest.mark.parametrize("a, b, d", [(350, 10, 20), (10, 350, 20), (0, 180, 180), (-170, 170, 20), (45, 45, 0)])
def test_angle_diff(a, b, d):
    assert angle_diff(a, b) == pytest.approx(d)
