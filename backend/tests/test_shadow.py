import math

from app.shadow import Box, convex_hull, is_point_sunlit, project_shadow_polygon, ray_intersects_box
from app.solar_position import SunPosition


def test_ray_intersects_box_low_sun_blocked():
    box = Box(x=0, y=5, width=4, depth=4, height=3)
    origin = (2.0, 0.0, 1.0)
    sun = SunPosition(altitude_deg=15.0, azimuth_deg=0.0)  # garden-relative bearing 0 = straight out
    assert ray_intersects_box(origin, _direction(sun), box) is True


def test_ray_intersects_box_misses_when_outside_x_range():
    box = Box(x=0, y=5, width=4, depth=4, height=3)
    origin = (20.0, 0.0, 1.0)
    sun = SunPosition(altitude_deg=15.0, azimuth_deg=0.0)
    assert ray_intersects_box(origin, _direction(sun), box) is False


def test_ray_intersects_box_overhead_sun_does_not_hit_box_further_into_garden():
    box = Box(x=0, y=5, width=4, depth=4, height=3)
    origin = (2.0, 0.0, 1.0)
    sun = SunPosition(altitude_deg=90.0, azimuth_deg=0.0)
    assert ray_intersects_box(origin, _direction(sun), box) is False


def test_ray_intersects_box_with_rotation():
    # Box footprint rotated 90 deg about its reference corner (0,0):
    # occupies x in [-2, 0], y in [0, 4] (see derivation in code review notes).
    box = Box(x=0, y=0, width=4, depth=2, height=3, rotation_deg=90)

    straight_up = (0.0, 0.0, 1.0)
    assert ray_intersects_box((-1.0, -1.0, 1.0), straight_up, box) is False
    assert ray_intersects_box((-1.0, 2.0, 1.0), straight_up, box) is True


def test_is_point_sunlit_house_blocks_sun_from_behind():
    sun_behind_house = SunPosition(altitude_deg=20.0, azimuth_deg=190.0)  # bearing > 90 from garden axis
    point = (2.0, 0.0, 1.0)
    assert is_point_sunlit(point, sun_behind_house, None) is False


def test_is_point_sunlit_below_horizon():
    sun_down = SunPosition(altitude_deg=-5.0, azimuth_deg=0.0)
    assert is_point_sunlit((2.0, 0.0, 1.0), sun_down, None) is False


def test_is_point_sunlit_garage_casts_shadow_but_not_outside_its_width():
    box = Box(x=0, y=5, width=4, depth=4, height=3)
    sun = SunPosition(altitude_deg=15.0, azimuth_deg=0.0)

    shaded_point = (2.0, 0.0, 1.0)
    clear_point = (20.0, 0.0, 1.0)

    assert is_point_sunlit(shaded_point, sun, box) is False
    assert is_point_sunlit(clear_point, sun, box) is True
    # Without the garage, the same point that was shaded is sunlit.
    assert is_point_sunlit(shaded_point, sun, None) is True


def test_convex_hull_of_shadow_projection_is_axis_aligned_rectangle():
    box = Box(x=0, y=5, width=4, depth=4, height=3)
    sun = SunPosition(altitude_deg=45.0, azimuth_deg=0.0)

    hull = project_shadow_polygon(box, sun)
    xs = sorted({round(p[0], 3) for p in hull})
    ys = sorted({round(p[1], 3) for p in hull})
    assert xs == [0.0, 4.0]
    assert ys == [2.0, 9.0]


def test_project_shadow_polygon_none_when_sun_too_low():
    box = Box(x=0, y=5, width=4, depth=4, height=3)
    sun = SunPosition(altitude_deg=0.5, azimuth_deg=0.0)
    assert project_shadow_polygon(box, sun) is None


def test_convex_hull_simple_square():
    points = [(0, 0), (0, 2), (2, 2), (2, 0), (1, 1)]
    hull = convex_hull(points)
    assert set(hull) == {(0, 0), (0, 2), (2, 2), (2, 0)}


def _direction(sun: SunPosition):
    alt_rad = math.radians(sun.altitude_deg)
    bearing_rad = math.radians(sun.azimuth_deg)
    horizontal = math.cos(alt_rad)
    return (
        horizontal * math.sin(bearing_rad),
        horizontal * math.cos(bearing_rad),
        math.sin(alt_rad),
    )
