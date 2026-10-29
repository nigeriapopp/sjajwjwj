"""Safety interlock. Every transmission must pass through `SafetyInterlock`.

Rules (from the spec):
  * at least 500 ms between transmissions
  * heart rate more than 15% above baseline -> link severed
  * hard intensity cap, tissue temperature and stress limits
  * blocked primitives (fear/survival-related) can never be sent
Once severed, the link stays down until a human calls `reset()`.
"""
from __future__ import annotations

import time
from dataclasses import dataclass, field

import numpy as np

from . import concepts
from .drivers import PulseTransmitter, Vitals

MIN_INTERVAL_S = 0.5
MAX_HR_RISE = 0.15
MAX_INTENSITY = 0.05
MAX_TEMP_RISE_C = 0.5
MAX_STRESS = 0.4


class SafetyTrip(RuntimeError):
    pass


@dataclass
class SafetyInterlock:
    transmitter: PulseTransmitter
    baseline_hr: float
    baseline_temp_c: float = 38.5
    clock: callable = time.monotonic
    severed: bool = False
    reason: str = ""
    _last_tx: float = field(default=-1e9)
    events: list[str] = field(default_factory=list)

    def _sever(self, reason: str) -> None:
        self.severed = True
        self.reason = reason
        self.events.append(f"SEVERED: {reason}")
        raise SafetyTrip(reason)

    def check_vitals(self, v: Vitals) -> None:
        if v.heart_rate_bpm > self.baseline_hr * (1 + MAX_HR_RISE):
            self._sever(f"heart rate {v.heart_rate_bpm:.0f} bpm > baseline "
                        f"{self.baseline_hr:.0f} +{MAX_HR_RISE:.0%}")
        if v.tissue_temp_c - self.baseline_temp_c > MAX_TEMP_RISE_C:
            self._sever(f"tissue temperature rise {v.tissue_temp_c - self.baseline_temp_c:.2f} C")
        if v.stress_index > MAX_STRESS:
            self._sever(f"stress index {v.stress_index:.2f} > {MAX_STRESS}")

    def send(self, concept: np.ndarray, intensity: float, vitals: Vitals,
             pattern: dict) -> None:
        if self.severed:
            raise SafetyTrip(f"link severed ({self.reason}); human reset required")
        self.check_vitals(vitals)
        blocked = [p for p in concepts.BLOCKED_PRIMITIVES
                   if concept[concepts.INDEX[p]] > 0]
        if blocked:
            raise SafetyTrip(f"blocked primitives in concept: {sorted(blocked)}")
        if not 0 < intensity <= MAX_INTENSITY:
            raise SafetyTrip(f"intensity {intensity} outside (0, {MAX_INTENSITY}]")
        now = self.clock()
        if now - self._last_tx < MIN_INTERVAL_S:
            raise SafetyTrip(f"rate limit: {now - self._last_tx:.3f}s since last pulse "
                             f"(< {MIN_INTERVAL_S}s)")
        self._last_tx = now
        self.transmitter.emit({**pattern, "intensity": intensity})
        self.events.append(f"sent {pattern.get('name', '?')} @ {intensity}")

    def reset(self, operator: str) -> None:
        if not operator:
            raise ValueError("reset needs a named human operator")
        self.events.append(f"reset by {operator} (was: {self.reason})")
        self.severed = False
        self.reason = ""
