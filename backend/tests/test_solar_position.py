from datetime import datetime, timedelta, timezone

from app.solar_position import sun_position


def _scan_day(lat: float, lon: float, day: datetime):
    """Return (time_of_max_altitude, max_altitude, azimuth_at_max)."""
    best = None
    t = day
    end = day + timedelta(days=1)
    while t < end:
        sp = sun_position(t, lat, lon)
        if best is None or sp.altitude_deg > best[1]:
            best = (t, sp.altitude_deg, sp.azimuth_deg)
        t += timedelta(minutes=2)
    return best


def test_tokyo_summer_solstice_noon_altitude_and_azimuth():
    lat, lon = 35.681, 139.767  # Tokyo Station
    day = datetime(2024, 6, 21, tzinfo=timezone.utc)
    _, max_alt, az_at_max = _scan_day(lat, lon, day)

    expected_max_alt = 90.0 - abs(lat - 23.44)
    assert abs(max_alt - expected_max_alt) < 1.0
    # Tokyo is well north of the Tropic of Cancer, so solar noon is due south.
    assert abs(az_at_max - 180.0) < 1.0


def test_tokyo_winter_solstice_noon_altitude():
    lat, lon = 35.681, 139.767
    day = datetime(2024, 12, 21, tzinfo=timezone.utc)
    _, max_alt, az_at_max = _scan_day(lat, lon, day)

    expected_max_alt = 90.0 - (lat + 23.44)
    assert abs(max_alt - expected_max_alt) < 1.0
    assert abs(az_at_max - 180.0) < 1.0


def test_azimuth_sweeps_from_east_to_west_over_the_day_midlatitude_summer():
    lat, lon = 35.681, 139.767
    day = datetime(2024, 6, 21, tzinfo=timezone.utc)

    morning = sun_position(day + timedelta(hours=1), lat, lon)  # ~10am JST
    noon = sun_position(day + timedelta(hours=2, minutes=45), lat, lon)  # ~11:45am JST, near solar noon
    evening = sun_position(day + timedelta(hours=9), lat, lon)  # ~6pm JST

    assert morning.azimuth_deg < 150.0
    assert 150.0 < noon.azimuth_deg < 210.0
    assert evening.azimuth_deg > 220.0


def test_sun_is_down_at_local_midnight():
    lat, lon = 35.681, 139.767
    midnight_utc = datetime(2024, 6, 21, 15, 0, tzinfo=timezone.utc)  # ~midnight JST
    sp = sun_position(midnight_utc, lat, lon)
    assert sp.altitude_deg < 0
    assert not sp.is_up


def test_equatorial_equinox_noon_altitude_near_90():
    lat, lon = 0.0, 0.0
    day = datetime(2024, 3, 20, tzinfo=timezone.utc)
    _, max_alt, _ = _scan_day(lat, lon, day)
    assert max_alt > 88.5
