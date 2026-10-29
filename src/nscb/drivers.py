"""Device interfaces plus simulated stand-ins.

`MeshReader` and `PulseTransmitter` are the seams where real hardware
drivers would go. The `Simulated*` classes generate synthetic signals and
log stimulation commands instead of emitting anything.
"""
from __future__ import annotations

import csv
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass

import numpy as np

SAMPLE_RATE_HZ = 256
CHANNELS = 4

# Synthetic brain states: relative power in delta/theta/alpha/beta/gamma.
# These are invented for the simulation, not measured from animals.
STATE_BANDS = {
    "rest":  [0.45, 0.25, 0.15, 0.10, 0.05],
    "calm":  [0.20, 0.25, 0.35, 0.15, 0.05],
    "alert": [0.08, 0.12, 0.15, 0.40, 0.25],
    "play":  [0.10, 0.20, 0.20, 0.30, 0.20],
    "food":  [0.12, 0.30, 0.18, 0.28, 0.12],
}
BAND_CENTERS_HZ = [2.0, 6.0, 10.0, 20.0, 40.0]


@dataclass
class Vitals:
    heart_rate_bpm: float
    tissue_temp_c: float
    stress_index: float  # 0..1


@dataclass
class Frame:
    timestamp: float
    samples: np.ndarray  # shape (CHANNELS, n)
    vitals: Vitals


class MeshReader(ABC):
    @abstractmethod
    def read(self, seconds: float = 1.0) -> Frame: ...


class PulseTransmitter(ABC):
    @abstractmethod
    def emit(self, pattern: dict) -> None: ...


class SimulatedMesh(MeshReader):
    """Generates multichannel signals for a chosen brain state."""

    def __init__(self, state: str = "calm", seed: int | None = None,
                 resting_hr: float = 80.0):
        self.state = state
        self.rng = np.random.default_rng(seed)
        self.resting_hr = resting_hr
        self.hr = resting_hr
        self.temp = 38.5

    def read(self, seconds: float = 1.0) -> Frame:
        n = int(SAMPLE_RATE_HZ * seconds)
        t = np.arange(n) / SAMPLE_RATE_HZ
        weights = np.array(STATE_BANDS[self.state])
        weights = weights * self.rng.uniform(0.85, 1.15, size=weights.shape)
        data = np.empty((CHANNELS, n))
        for ch in range(CHANNELS):
            sig = np.zeros(n)
            for w, f in zip(weights, BAND_CENTERS_HZ):
                f_j = f * self.rng.uniform(0.9, 1.1)
                sig += np.sqrt(w) * np.sin(2 * np.pi * f_j * t + self.rng.uniform(0, 2 * np.pi))
            data[ch] = sig * 20.0 + self.rng.normal(0, 2.0, n)  # microvolts
        target = self.resting_hr * (1.25 if self.state in ("alert", "play") else 1.0)
        self.hr += (target - self.hr) * 0.3 + self.rng.normal(0, 1.0)
        stress = 0.6 if self.state == "alert" else 0.15
        return Frame(time.time(), data, Vitals(self.hr, self.temp, stress))


class ReplayMesh(MeshReader):
    """Reads frames back from a CSV log written by `log_frames`."""

    def __init__(self, path: str):
        with open(path, newline="") as f:
            rows = list(csv.DictReader(f))
        if not rows:
            raise ValueError(f"empty log: {path}")
        self.rows = rows
        self.pos = 0

    def read(self, seconds: float = 1.0) -> Frame:
        n = int(SAMPLE_RATE_HZ * seconds)
        chunk = self.rows[self.pos:self.pos + n]
        if len(chunk) < n:
            raise EOFError("end of log")
        self.pos += n
        data = np.array([[float(r[f"ch{c}"]) for r in chunk] for c in range(CHANNELS)])
        last = chunk[-1]
        vitals = Vitals(float(last["hr"]), float(last["temp"]), float(last["stress"]))
        return Frame(float(chunk[0]["t"]), data, vitals)


def log_frames(path: str, frames: list[Frame]) -> None:
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["t"] + [f"ch{c}" for c in range(CHANNELS)] + ["hr", "temp", "stress"])
        for fr in frames:
            for i in range(fr.samples.shape[1]):
                w.writerow([fr.timestamp + i / SAMPLE_RATE_HZ]
                           + [round(float(fr.samples[c, i]), 3) for c in range(CHANNELS)]
                           + [round(fr.vitals.heart_rate_bpm, 1), fr.vitals.tissue_temp_c,
                              fr.vitals.stress_index])


class SimulatedPulse(PulseTransmitter):
    """Records what would have been sent. Emits nothing."""

    def __init__(self):
        self.sent: list[dict] = []

    def emit(self, pattern: dict) -> None:
        self.sent.append(pattern)
