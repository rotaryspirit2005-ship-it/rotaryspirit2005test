import asyncio
from datetime import date, datetime, timedelta, timezone

import pytest

from app import simulation, weather
from app.models import GarageSpec, SimulationRequest
from app.simulation import run_simulation
from app.solar_position import SunPosition


def _fake_series(target_date: date) -> weather.IrradianceSeries:
    hours = []
    for h in range(24):
        # Simple daytime bump between 06:00-18:00 local (utc_offset 0 for the test).
        daylight = 6 <= h <= 18
        hours.append(
            weather.HourlyIrradiance(
                time_utc=datetime(target_date.year, target_date.month, target_date.day, h, tzinfo=timezone.utc),
                shortwave_wm2=500.0 if daylight else 0.0,
                direct_wm2=400.0 if daylight else 0.0,
                diffuse_wm2=100.0 if daylight else 0.0,
            )
        )
    return weather.IrradianceSeries(hours=hours, source="fake", utc_offset_hours=0.0)


@pytest.fixture(autouse=True)
def _patch_weather(monkeypatch):
    async def fake_fetch(lat, lon, target_date):
        return _fake_series(target_date)

    monkeypatch.setattr(weather, "fetch_irradiance", fake_fetch)


def test_garage_reduces_sunlight_reaching_veranda(monkeypatch):
    # Pin the sun low in the sky and pointed straight into the garden for
    # every timestep, so the garage's shadow is guaranteed to reach the
    # veranda regardless of the real date/location's actual sun path (that
    # astronomy is covered separately in test_solar_position.py).
    monkeypatch.setattr(
        simulation, "sun_position", lambda utc_dt, lat, lon: SunPosition(altitude_deg=20.0, azimuth_deg=180.0)
    )

    req = SimulationRequest(
        latitude=35.681,
        longitude=139.767,
        date=date(2024, 6, 21),
        garden_width=10,
        garden_depth=10,
        garden_orientation_deg=180,
        veranda_height=1.0,
        garage=GarageSpec(enabled=True, x=2, y=3, width=5, depth=5, height=3),
        sample_points=11,
        time_step_minutes=30,
    )

    resp = asyncio.run(run_simulation(req))

    assert resp.summary.sunlight_hours_with_garage <= resp.summary.sunlight_hours_without_garage
    assert resp.summary.sunlight_hours_lost >= 0
    assert resp.summary.irradiance_with_garage_wh_m2 <= resp.summary.irradiance_without_garage_wh_m2
    assert len(resp.garage_footprint) == 4

    # At least one timestep should show a strictly lower sunlit fraction with the garage present.
    assert any(
        t.sunlit_fraction_with_garage < t.sunlit_fraction_without_garage for t in resp.timeline
    )


def test_no_garage_matches_without_garage_metrics():
    req = SimulationRequest(
        latitude=35.681,
        longitude=139.767,
        date=date(2024, 6, 21),
        garden_width=10,
        garden_depth=10,
        garage=GarageSpec(enabled=False),
        sample_points=5,
        time_step_minutes=60,
    )

    resp = asyncio.run(run_simulation(req))

    assert resp.garage_footprint == []
    for t in resp.timeline:
        assert t.sunlit_fraction_with_garage == t.sunlit_fraction_without_garage
        assert t.shadow_polygon is None
    assert resp.summary.sunlight_hours_lost == 0


def test_house_wall_blocks_sun_from_north_side_in_midsummer_early_morning():
    # Very early morning in midsummer Tokyo, the sun can be nearly due
    # north-east of a south-facing garden; check the model doesn't award
    # sunlight that would have to pass through the house.
    req = SimulationRequest(
        latitude=35.681,
        longitude=139.767,
        date=date(2024, 6, 21),
        garden_width=10,
        garden_depth=10,
        garden_orientation_deg=180,
        garage=GarageSpec(enabled=False),
        sample_points=3,
        time_step_minutes=10,
    )
    resp = asyncio.run(run_simulation(req))
    for t in resp.timeline:
        if t.sun_altitude_deg > 0:
            relative = (t.sun_azimuth_deg - 180) % 360
            if relative > 90 and relative < 270:
                assert t.sunlit_fraction_without_garage == 0.0
