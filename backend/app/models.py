from __future__ import annotations

from datetime import date as date_type

from pydantic import BaseModel, Field, model_validator


class GarageSpec(BaseModel):
    """The rectangular garage/box to test, in the garden's local frame."""

    enabled: bool = True
    x: float = Field(0.0, description="Box corner offset along the garden's X axis (m)")
    y: float = Field(0.0, description="Box corner offset along the garden's Y axis, i.e. distance from the house (m)")
    width: float = Field(6.0, gt=0, le=100, description="Box extent along its own X axis (m)")
    depth: float = Field(6.0, gt=0, le=100, description="Box extent along its own Y axis (m)")
    height: float = Field(2.5, gt=0, le=50, description="Box height (m)")
    rotation_deg: float = Field(0.0, ge=-360, le=360, description="Footprint rotation, clockwise (deg)")


class SimulationRequest(BaseModel):
    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    date: date_type = Field(..., description="Local calendar date to simulate")

    garden_width: float = Field(..., gt=0, le=200, description="Garden extent along X (m)")
    garden_depth: float = Field(..., gt=0, le=200, description="Garden extent along Y, away from the house (m)")
    garden_orientation_deg: float = Field(
        180.0,
        ge=0,
        le=360,
        description=(
            "Compass bearing (deg, clockwise from true north) of the direction "
            "pointing from the house out into the garden. 180 = veranda faces south."
        ),
    )
    veranda_height: float = Field(1.0, ge=0, le=10, description="Height above ground at which sunlight into the veranda is sampled (m)")

    garage: GarageSpec = Field(default_factory=GarageSpec)

    sample_points: int = Field(11, ge=2, le=101, description="Number of points sampled along the veranda")
    time_step_minutes: int = Field(15, ge=1, le=120, description="Time resolution of the simulation")

    @model_validator(mode="after")
    def _check_garage_within_bounds(self) -> "SimulationRequest":
        # Not a hard error if the garage extends past the fence -- gardens
        # aren't always perfectly rectangular in real life -- but keep the
        # numbers sane so a typo doesn't produce a nonsensical simulation.
        if self.garage.width > 5 * self.garden_width and self.garage.depth > 5 * self.garden_depth:
            raise ValueError("garage dimensions are implausibly large relative to the garden")
        return self


class SunSample(BaseModel):
    time_local: str
    altitude_deg: float
    azimuth_deg: float


class TimeStepResult(BaseModel):
    time_local: str
    sun_altitude_deg: float
    sun_azimuth_deg: float
    sunlit_fraction_without_garage: float
    sunlit_fraction_with_garage: float
    sunlit_points_without_garage: list[bool]
    sunlit_points_with_garage: list[bool]
    irradiance_shortwave_wm2: float
    irradiance_direct_wm2: float
    irradiance_diffuse_wm2: float
    effective_irradiance_without_garage_wm2: float
    effective_irradiance_with_garage_wm2: float
    shadow_polygon: list[list[float]] | None = None


class DailySummary(BaseModel):
    sunrise_local: str | None
    sunset_local: str | None
    sunlight_hours_without_garage: float
    sunlight_hours_with_garage: float
    sunlight_hours_lost: float
    irradiance_without_garage_wh_m2: float
    irradiance_with_garage_wh_m2: float
    irradiance_lost_percent: float


class SimulationResponse(BaseModel):
    latitude: float
    longitude: float
    date: str
    utc_offset_hours: float
    irradiance_source: str
    garden_width: float
    garden_depth: float
    veranda_sample_x: list[float]
    garage_footprint: list[list[float]]
    timeline: list[TimeStepResult]
    summary: DailySummary
