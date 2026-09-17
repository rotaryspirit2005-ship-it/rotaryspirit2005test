"""Sun position (altitude/azimuth) calculation.

Implements the low-precision solar position algorithm described in
Jean Meeus, "Astronomical Algorithms", as popularized by the NOAA Solar
Calculator. Accuracy is on the order of ~0.01 degrees for dates between
1800 and 2100, which is far more than needed for a garden shading study.

All calculations are performed against a timezone-aware UTC datetime so
that no timezone-offset bookkeeping is required inside the trig -- the
caller is responsible for converting local times to UTC before calling in,
and back to local time for display.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import datetime, timezone


@dataclass(frozen=True)
class SunPosition:
    altitude_deg: float
    azimuth_deg: float

    @property
    def is_up(self) -> bool:
        return self.altitude_deg > 0.0


def _julian_day(dt_utc: datetime) -> float:
    if dt_utc.tzinfo is None:
        raise ValueError("dt_utc must be timezone-aware")
    dt_utc = dt_utc.astimezone(timezone.utc)

    year = dt_utc.year
    month = dt_utc.month
    day = (
        dt_utc.day
        + dt_utc.hour / 24.0
        + dt_utc.minute / 1440.0
        + dt_utc.second / 86400.0
    )

    if month <= 2:
        year -= 1
        month += 12

    a = math.floor(year / 100)
    b = 2 - a + math.floor(a / 4)

    jd = (
        math.floor(365.25 * (year + 4716))
        + math.floor(30.6001 * (month + 1))
        + day
        + b
        - 1524.5
    )
    return jd


def sun_position(dt_utc: datetime, lat_deg: float, lon_deg: float) -> SunPosition:
    """Compute apparent solar altitude/azimuth for a UTC time and location.

    lat_deg: latitude, positive north.
    lon_deg: longitude, positive east.
    """
    jd = _julian_day(dt_utc)
    jc = (jd - 2451545.0) / 36525.0

    geom_mean_long_sun = (280.46646 + jc * (36000.76983 + jc * 0.0003032)) % 360.0
    geom_mean_anom_sun = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
    eccent_earth_orbit = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)

    m_rad = math.radians(geom_mean_anom_sun)
    sun_eq_of_ctr = (
        math.sin(m_rad) * (1.914602 - jc * (0.004817 + 0.000014 * jc))
        + math.sin(2 * m_rad) * (0.019993 - 0.000101 * jc)
        + math.sin(3 * m_rad) * 0.000289
    )

    sun_true_long = geom_mean_long_sun + sun_eq_of_ctr

    omega = 125.04 - 1934.136 * jc
    sun_app_long = sun_true_long - 0.00569 - 0.00478 * math.sin(math.radians(omega))

    mean_obliq_ecliptic = 23.0 + (
        26.0 + (21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60.0
    ) / 60.0
    obliq_corr = mean_obliq_ecliptic + 0.00256 * math.cos(math.radians(omega))

    app_long_rad = math.radians(sun_app_long)
    obliq_corr_rad = math.radians(obliq_corr)

    sun_declin = math.degrees(
        math.asin(math.sin(obliq_corr_rad) * math.sin(app_long_rad))
    )

    y = math.tan(math.radians(obliq_corr / 2.0)) ** 2
    l0_rad = math.radians(geom_mean_long_sun)
    eq_of_time = 4 * math.degrees(
        y * math.sin(2 * l0_rad)
        - 2 * eccent_earth_orbit * math.sin(m_rad)
        + 4 * eccent_earth_orbit * y * math.sin(m_rad) * math.cos(2 * l0_rad)
        - 0.5 * y * y * math.sin(4 * l0_rad)
        - 1.25 * eccent_earth_orbit * eccent_earth_orbit * math.sin(2 * m_rad)
    )

    dt_utc = dt_utc.astimezone(timezone.utc)
    minutes_since_midnight_utc = (
        dt_utc.hour * 60.0 + dt_utc.minute + dt_utc.second / 60.0
    )

    true_solar_time = (minutes_since_midnight_utc + eq_of_time + 4 * lon_deg) % 1440.0

    hour_angle = true_solar_time / 4.0 - 180.0
    if true_solar_time / 4.0 < 0:
        hour_angle = true_solar_time / 4.0 + 180.0

    lat_rad = math.radians(lat_deg)
    decl_rad = math.radians(sun_declin)
    ha_rad = math.radians(hour_angle)

    cos_zenith = math.sin(lat_rad) * math.sin(decl_rad) + math.cos(lat_rad) * math.cos(
        decl_rad
    ) * math.cos(ha_rad)
    cos_zenith = max(-1.0, min(1.0, cos_zenith))
    zenith = math.degrees(math.acos(cos_zenith))

    elevation = 90.0 - zenith
    elevation += _atmospheric_refraction_correction(elevation)

    sin_zenith = math.sin(math.radians(zenith))
    if abs(sin_zenith) < 1e-6:
        azimuth = 180.0
    else:
        cos_az_arg = (
            math.sin(lat_rad) * math.cos(math.radians(zenith)) - math.sin(decl_rad)
        ) / (math.cos(lat_rad) * sin_zenith)
        cos_az_arg = max(-1.0, min(1.0, cos_az_arg))
        az = math.degrees(math.acos(cos_az_arg))
        if hour_angle > 0:
            azimuth = (az + 180.0) % 360.0
        else:
            azimuth = (540.0 - az) % 360.0

    return SunPosition(altitude_deg=elevation, azimuth_deg=azimuth)


def _atmospheric_refraction_correction(elevation_deg: float) -> float:
    """Approximate atmospheric refraction correction, in degrees.

    Based on the formula used by the NOAA solar calculator. Only really
    matters within a few degrees of the horizon; returns 0 once the sun is
    comfortably above it.
    """
    if elevation_deg > 85.0:
        return 0.0

    tan_e = math.tan(math.radians(elevation_deg))
    if elevation_deg > 5.0:
        correction = 58.1 / tan_e - 0.07 / (tan_e ** 3) + 0.000086 / (tan_e ** 5)
    elif elevation_deg > -0.575:
        correction = (
            1735.0
            + elevation_deg
            * (
                -518.2
                + elevation_deg * (103.4 + elevation_deg * (-12.79 + elevation_deg * 0.711))
            )
        )
    else:
        correction = -20.774 / tan_e

    return correction / 3600.0
