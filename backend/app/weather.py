"""Client for external solar irradiance data (Open-Meteo).

Open-Meteo (https://open-meteo.com) is used because it is free, requires
no API key, and exposes hourly shortwave/direct/diffuse radiation for
both recent history and forecast, backed by weather models -- exactly the
kind of "actual sunshine conditions" data this app needs to combine with
the purely-geometric sun-position/shadow calculation.

If the external service is unreachable (offline dev environment, network
policy, outage, ...) we fall back to a simple clear-sky estimate so the
rest of the app keeps working; the response tells the caller which
happened via `IrradianceSeries.source`.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone

import httpx

FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive"

HOURLY_VARS = "shortwave_radiation,direct_radiation,diffuse_radiation"


@dataclass(frozen=True)
class HourlyIrradiance:
    time_utc: datetime
    shortwave_wm2: float
    direct_wm2: float
    diffuse_wm2: float


@dataclass(frozen=True)
class IrradianceSeries:
    hours: list[HourlyIrradiance]
    source: str  # "open-meteo-forecast" | "open-meteo-archive" | "clear-sky-estimate"
    utc_offset_hours: float


async def fetch_irradiance(lat: float, lon: float, target_date: date) -> IrradianceSeries:
    today = datetime.now(timezone.utc).date()
    diff_days = (target_date - today).days

    if -90 <= diff_days <= 15:
        url, source = FORECAST_URL, "open-meteo-forecast"
    else:
        url, source = ARCHIVE_URL, "open-meteo-archive"

    params = {
        "latitude": lat,
        "longitude": lon,
        "hourly": HOURLY_VARS,
        "start_date": target_date.isoformat(),
        "end_date": target_date.isoformat(),
        # "auto" resolves to the IANA timezone at (lat, lon); Open-Meteo then
        # returns hourly.time as local wall-clock strings alongside the
        # resolved utc_offset_seconds, which saves us from bundling a
        # separate timezone-lookup dataset just to convert local<->UTC.
        "timezone": "auto",
    }

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.get(url, params=params)
            resp.raise_for_status()
            data = resp.json()

        utc_offset_seconds = float(data.get("utc_offset_seconds", 0))
        utc_offset_hours = utc_offset_seconds / 3600.0

        hourly = data["hourly"]
        times = hourly["time"]
        sw = hourly["shortwave_radiation"]
        direct = hourly["direct_radiation"]
        diffuse = hourly["diffuse_radiation"]

        hours = []
        for t, s, d, df in zip(times, sw, direct, diffuse):
            local_naive = datetime.fromisoformat(t)
            time_utc = local_naive.replace(tzinfo=timezone.utc) - timedelta(
                hours=utc_offset_hours
            )
            hours.append(
                HourlyIrradiance(
                    time_utc=time_utc,
                    shortwave_wm2=float(s) if s is not None else 0.0,
                    direct_wm2=float(d) if d is not None else 0.0,
                    diffuse_wm2=float(df) if df is not None else 0.0,
                )
            )
        if not hours:
            raise ValueError("empty hourly series from Open-Meteo")
        return IrradianceSeries(hours=hours, source=source, utc_offset_hours=utc_offset_hours)
    except Exception:
        return _clear_sky_fallback(lat, lon, target_date)


def _clear_sky_fallback(lat: float, lon: float, target_date: date) -> IrradianceSeries:
    """Very rough clear-sky model used only when the external API call fails.

    Uses a simple sinusoidal approximation of direct-normal-ish irradiance
    scaled by sun elevation, plus a flat diffuse component. This is *not*
    meant to be meteorologically accurate -- it exists purely so the app
    degrades gracefully instead of failing outright when offline.
    """
    from .solar_position import sun_position

    utc_offset_hours = round(lon / 15.0)

    hours = []
    for hour in range(24):
        dt_utc = datetime(
            target_date.year, target_date.month, target_date.day, hour, 0, tzinfo=timezone.utc
        ) - timedelta(hours=utc_offset_hours)
        sp = sun_position(dt_utc, lat, lon)
        if sp.altitude_deg > 0:
            sin_alt = math.sin(math.radians(sp.altitude_deg))
            direct = max(0.0, 900.0 * sin_alt ** 1.2)
            diffuse = max(0.0, 100.0 * sin_alt)
        else:
            direct = 0.0
            diffuse = 0.0
        hours.append(
            HourlyIrradiance(
                time_utc=dt_utc,
                shortwave_wm2=direct + diffuse,
                direct_wm2=direct,
                diffuse_wm2=diffuse,
            )
        )
    return IrradianceSeries(
        hours=hours, source="clear-sky-estimate", utc_offset_hours=float(utc_offset_hours)
    )


def interpolate(series: IrradianceSeries, at_utc: datetime) -> HourlyIrradiance:
    """Linearly interpolate the hourly series to an arbitrary UTC instant."""
    hours = series.hours
    if at_utc <= hours[0].time_utc:
        return hours[0]
    if at_utc >= hours[-1].time_utc:
        return hours[-1]

    for i in range(len(hours) - 1):
        a, b = hours[i], hours[i + 1]
        if a.time_utc <= at_utc <= b.time_utc:
            span = (b.time_utc - a.time_utc).total_seconds()
            frac = 0.0 if span == 0 else (at_utc - a.time_utc).total_seconds() / span
            return HourlyIrradiance(
                time_utc=at_utc,
                shortwave_wm2=a.shortwave_wm2 + frac * (b.shortwave_wm2 - a.shortwave_wm2),
                direct_wm2=a.direct_wm2 + frac * (b.direct_wm2 - a.direct_wm2),
                diffuse_wm2=a.diffuse_wm2 + frac * (b.diffuse_wm2 - a.diffuse_wm2),
            )
    return hours[-1]
