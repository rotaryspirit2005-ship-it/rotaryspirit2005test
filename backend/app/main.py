from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from .models import SimulationRequest, SimulationResponse
from .simulation import run_simulation

app = FastAPI(
    title="Garden Sunlight Simulator",
    description=(
        "Simulates how much direct sunlight reaches a house's veranda "
        "throughout the day, and how a garage (or any rectangular "
        "structure) placed in the garden would change that, using real "
        "solar-position astronomy and external solar-irradiance data."
    ),
    version="0.1.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.post("/api/simulate", response_model=SimulationResponse)
async def simulate(req: SimulationRequest) -> SimulationResponse:
    return await run_simulation(req)


@app.get("/api/health")
async def health() -> dict:
    return {"status": "ok"}


_frontend_dir = Path(__file__).resolve().parent.parent.parent / "frontend"
if _frontend_dir.is_dir():
    app.mount("/", StaticFiles(directory=str(_frontend_dir), html=True), name="frontend")
