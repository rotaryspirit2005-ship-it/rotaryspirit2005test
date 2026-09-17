"""Geometry for the garden / garage box shading model.

Local coordinate frame (right-handed, meters):
  - Origin: the corner of the garden where it meets the house, at the
    "left" end of the veranda as seen when standing in the house looking
    out into the garden.
  - +Y: points away from the house, into the garden (the direction the
    garden's "depth" is measured along).
  - +X: 90 degrees clockwise from +Y (i.e. to the right when facing out
    into the garden).
  - +Z: straight up.

The house itself is modeled as an infinite opaque wall in the plane Y=0
(so the veranda, which sits right on that boundary, can only ever receive
direct sun when the sun's horizontal direction has a positive Y
component -- otherwise the sun is physically behind the house wall).

The garage is modeled as a single axis-aligned-in-its-own-frame
rectangular box (a "direct cuboid"), optionally rotated in plan.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

from .solar_position import SunPosition


@dataclass(frozen=True)
class Box:
    """A rectangular cuboid (the garage) placed in the garden.

    x, y: position (in meters) of the box's near-bottom corner *before*
        rotation is applied, measured from the garden origin.
    width: extent along the box's own local X axis (meters).
    depth: extent along the box's own local Y axis (meters).
    height: extent along Z (meters).
    rotation_deg: rotation of the box footprint about its (x, y) corner,
        clockwise, in degrees. 0 means the box's edges are parallel to the
        garden axes.
    """

    x: float
    y: float
    width: float
    depth: float
    height: float
    rotation_deg: float = 0.0

    def contains_point_plan(self, px: float, py: float) -> bool:
        """True if the (px, py) plan point falls within the box footprint."""
        lx, ly = self._to_local(px, py)
        return 0.0 <= lx <= self.width and 0.0 <= ly <= self.depth

    def _to_local(self, px: float, py: float) -> tuple[float, float]:
        dx = px - self.x
        dy = py - self.y
        theta = math.radians(-self.rotation_deg)
        lx = dx * math.cos(theta) - dy * math.sin(theta)
        ly = dx * math.sin(theta) + dy * math.cos(theta)
        return lx, ly

    def footprint_corners(self) -> list[tuple[float, float]]:
        """Corners of the box footprint in garden (world) coordinates."""
        theta = math.radians(self.rotation_deg)
        cos_t, sin_t = math.cos(theta), math.sin(theta)
        local_corners = [
            (0.0, 0.0),
            (self.width, 0.0),
            (self.width, self.depth),
            (0.0, self.depth),
        ]
        world = []
        for lx, ly in local_corners:
            wx = self.x + lx * cos_t - ly * sin_t
            wy = self.y + lx * sin_t + ly * cos_t
            world.append((wx, wy))
        return world


def sun_direction_vector(sun: SunPosition) -> tuple[float, float, float]:
    """Unit vector pointing FROM a ground point TOWARD the sun.

    Expressed in the garden local frame described above, given the sun's
    compass azimuth (degrees clockwise from true north) and its garden
    orientation is already baked into `sun` via the caller having rotated
    the raw compass azimuth into garden-relative bearing (see
    `simulation.py`). Here we just do the standard spherical -> cartesian
    conversion for a garden-relative bearing where 0 deg = straight into
    the garden (+Y) and 90 deg = to the right (+X).
    """
    alt_rad = math.radians(sun.altitude_deg)
    bearing_rad = math.radians(sun.azimuth_deg)
    horizontal = math.cos(alt_rad)
    dx = horizontal * math.sin(bearing_rad)
    dy = horizontal * math.cos(bearing_rad)
    dz = math.sin(alt_rad)
    return dx, dy, dz


def ray_intersects_box(
    origin: tuple[float, float, float],
    direction: tuple[float, float, float],
    box: Box,
    max_distance: float = 100000.0,
) -> bool:
    """Slab-method ray/AABB test, with the ray rotated into box-local space.

    Returns True if the ray from `origin` along `direction` (both in
    garden/world coordinates) hits the box at any distance in
    (epsilon, max_distance).
    """
    theta = math.radians(-box.rotation_deg)
    cos_t, sin_t = math.cos(theta), math.sin(theta)

    ox, oy, oz = origin
    dx, dy, dz = direction

    lox = (ox - box.x) * cos_t - (oy - box.y) * sin_t
    loy = (ox - box.x) * sin_t + (oy - box.y) * cos_t
    ldx = dx * cos_t - dy * sin_t
    ldy = dx * sin_t + dy * cos_t

    t_min = 1e-6
    t_max = max_distance

    bounds = [
        (lox, ldx, 0.0, box.width),
        (loy, ldy, 0.0, box.depth),
        (oz, dz, 0.0, box.height),
    ]

    for o_comp, d_comp, lo, hi in bounds:
        if abs(d_comp) < 1e-12:
            if o_comp < lo or o_comp > hi:
                return False
            continue
        t1 = (lo - o_comp) / d_comp
        t2 = (hi - o_comp) / d_comp
        if t1 > t2:
            t1, t2 = t2, t1
        t_min = max(t_min, t1)
        t_max = min(t_max, t2)
        if t_min > t_max:
            return False

    return t_max >= t_min


def convex_hull(points: list[tuple[float, float]]) -> list[tuple[float, float]]:
    """Andrew's monotone-chain convex hull. Small inputs only (<=8 points)."""
    pts = sorted(set(points))
    if len(pts) <= 2:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower: list[tuple[float, float]] = []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)

    upper: list[tuple[float, float]] = []
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)

    return lower[:-1] + upper[:-1]


def project_shadow_polygon(
    box: Box, sun: SunPosition, min_altitude_deg: float = 2.0
) -> list[tuple[float, float]] | None:
    """Shadow footprint the box casts on the (flat) ground plane.

    Returns None when the sun is too low (or below) the horizon, since the
    projection then stretches toward infinity and isn't meaningful to draw.
    """
    if sun.altitude_deg < min_altitude_deg:
        return None

    dx, dy, dz = sun_direction_vector(sun)
    if dz <= 1e-6:
        return None

    base_corners = box.footprint_corners()
    top_shadow_points = []
    for x, y in base_corners:
        t = box.height / dz
        sx = x - t * dx
        sy = y - t * dy
        top_shadow_points.append((sx, sy))

    return convex_hull(base_corners + top_shadow_points)


def is_point_sunlit(
    point: tuple[float, float, float],
    sun: SunPosition,
    box: Box | None,
) -> bool:
    """True if a garden/veranda point receives direct sun.

    Accounts for: the sun being above the horizon, the house wall at Y=0
    blocking sun coming from "behind" the veranda, and (optionally) the
    garage box occluding the sun.
    """
    if sun.altitude_deg <= 0:
        return False

    direction = sun_direction_vector(sun)
    if direction[1] <= 1e-9:
        # Sun's horizontal component points back toward/along the house
        # wall; the house itself blocks it.
        return False

    if box is not None and ray_intersects_box(point, direction, box):
        return False

    return True
