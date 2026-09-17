"""Ties together sun position, external irradiance data, and the
box-shadow geometry into a single day-long simulation."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

from . import weather
from .models import DailySummary, SimulationRequest, SimulationResponse, TimeStepResult
from .shadow import Box, is_point_sunlit, project_shadow_polygon
from .solar_position import SunPosition, sun_position


def _garden_relative_sun(
    utc_dt: datetime, lat: float, lon: float, garden_orientation_deg: float
) -> SunPosition:
    """Sun position with azimuth re-expressed as a garden-relative bearing.

    0 deg = straight out into the garden (away from the house), 90 deg =
    to the right when facing out into the garden, matching the convention
    used throughout `shadow.py`.
    """
    raw = sun_position(utc_dt, lat, lon)
    relative_bearing = (raw.azimuth_deg - garden_orientation_deg) % 360.0
    return SunPosition(altitude_deg=raw.altitude_deg, azimuth_deg=relative_bearing)


def _veranda_sample_points(req: SimulationRequest) -> list[tuple[float, float, float]]:
    n = req.sample_points
    if n == 1:
        xs = [req.garden_width / 2.0]
    else:
        xs = [req.garden_width * i / (n - 1) for i in range(n)]
    return [(x, 0.0, req.veranda_height) for x in xs]


async def run_simulation(req: SimulationRequest) -> SimulationResponse:
    series = await weather.fetch_irradiance(req.latitude, req.longitude, req.date)
    utc_offset_hours = series.utc_offset_hours

    box = None
    if req.garage.enabled:
        g = req.garage
        box = Box(
            x=g.x, y=g.y, width=g.width, depth=g.depth, height=g.height,
            rotation_deg=g.rotation_deg,
        )

    sample_points = _veranda_sample_points(req)

    step = timedelta(minutes=req.time_step_minutes)
    local_midnight = datetime(req.date.year, req.date.month, req.date.day, 0, 0, 0)
    n_steps = int(timedelta(days=1) / step)

    timeline: list[TimeStepResult] = []
    dt_hours = req.time_step_minutes / 60.0

    sunlight_hours_without = 0.0
    sunlight_hours_with = 0.0
    irradiance_without_wh = 0.0
    irradiance_with_wh = 0.0
    first_sunlit_local: str | None = None
    last_sunlit_local: str | None = None

    for i in range(n_steps + 1):
        local_dt = local_midnight + i * step
        utc_dt = (local_dt - timedelta(hours=utc_offset_hours)).replace(tzinfo=timezone.utc)

        sun_rel = _garden_relative_sun(utc_dt, req.latitude, req.longitude, req.garden_orientation_deg)

        if sun_rel.altitude_deg > 0:
            local_str = local_dt.strftime("%H:%M")
            if first_sunlit_local is None:
                first_sunlit_local = local_str
            last_sunlit_local = local_str

        points_without = [is_point_sunlit(p, sun_rel, None) for p in sample_points]
        points_with = [is_point_sunlit(p, sun_rel, box) for p in sample_points]
        frac_without = sum(points_without) / len(sample_points)
        frac_with = sum(points_with) / len(sample_points)

        irr = weather.interpolate(series, utc_dt)
        effective_without = irr.diffuse_wm2 + irr.direct_wm2 * frac_without
        effective_with = irr.diffuse_wm2 + irr.direct_wm2 * frac_with

        sunlight_hours_without += frac_without * dt_hours
        sunlight_hours_with += frac_with * dt_hours
        irradiance_without_wh += effective_without * dt_hours
        irradiance_with_wh += effective_with * dt_hours

        shadow_polygon = None
        if box is not None:
            poly = project_shadow_polygon(box, sun_rel)
            if poly is not None:
                shadow_polygon = [[round(x, 3), round(y, 3)] for x, y in poly]

        timeline.append(
            TimeStepResult(
                time_local=local_dt.strftime("%H:%M"),
                sun_altitude_deg=round(sun_rel.altitude_deg, 3),
                sun_azimuth_deg=round((sun_rel.azimuth_deg + req.garden_orientation_deg) % 360, 3),
                sunlit_fraction_without_garage=round(frac_without, 4),
                sunlit_fraction_with_garage=round(frac_with, 4),
                sunlit_points_without_garage=points_without,
                sunlit_points_with_garage=points_with,
                irradiance_shortwave_wm2=round(irr.shortwave_wm2, 2),
                irradiance_direct_wm2=round(irr.direct_wm2, 2),
                irradiance_diffuse_wm2=round(irr.diffuse_wm2, 2),
                effective_irradiance_without_garage_wm2=round(effective_without, 2),
                effective_irradiance_with_garage_wm2=round(effective_with, 2),
                shadow_polygon=shadow_polygon,
            )
        )

    irradiance_lost_percent = 0.0
    if irradiance_without_wh > 1e-9:
        irradiance_lost_percent = (
            (irradiance_without_wh - irradiance_with_wh) / irradiance_without_wh * 100.0
        )

    summary = DailySummary(
        sunrise_local=first_sunlit_local,
        sunset_local=last_sunlit_local,
        sunlight_hours_without_garage=round(sunlight_hours_without, 2),
        sunlight_hours_with_garage=round(sunlight_hours_with, 2),
        sunlight_hours_lost=round(sunlight_hours_without - sunlight_hours_with, 2),
        irradiance_without_garage_wh_m2=round(irradiance_without_wh, 1),
        irradiance_with_garage_wh_m2=round(irradiance_with_wh, 1),
        irradiance_lost_percent=round(irradiance_lost_percent, 1),
    )

    footprint = box.footprint_corners() if box is not None else []

    return SimulationResponse(
        latitude=req.latitude,
        longitude=req.longitude,
        date=req.date.isoformat(),
        utc_offset_hours=utc_offset_hours,
        irradiance_source=series.source,
        garden_width=req.garden_width,
        garden_depth=req.garden_depth,
        veranda_sample_x=[round(p[0], 3) for p in sample_points],
        garage_footprint=[[round(x, 3), round(y, 3)] for x, y in footprint],
        timeline=timeline,
        summary=summary,
    )
