"""The shared concept space: a small set of named primitives.

A "concept vector" is a weight per primitive (0..1). Both the human side
(text) and the animal side (brain-state features) are mapped into this space.
"""
import numpy as np

PRIMITIVES = (
    "food", "reward", "imminent", "calm", "sit", "play",
    "home", "alert", "stranger", "owner", "outside", "rest",
)
INDEX = {name: i for i, name in enumerate(PRIMITIVES)}
DIM = len(PRIMITIVES)

# Primitives the transmitter must never send. They touch fear, pain or
# survival drives, which the spec requires the bridge to stay away from.
BLOCKED_PRIMITIVES = frozenset({"alert", "stranger"})

# Named presets, addressable by a 16-bit id like the spec's 0x4F9A.
PRESETS = {
    0x4F9A: ("sit_calm", {"sit": 1.0, "calm": 1.0}),
    0x1A01: ("food_soon", {"food": 1.0, "imminent": 0.8, "reward": 0.6}),
    0x2B02: ("go_home", {"home": 1.0, "outside": 0.3}),
    0x3C03: ("play", {"play": 1.0, "reward": 0.4}),
    0x5D04: ("rest", {"rest": 1.0, "calm": 0.7}),
}


def vector(weights: dict) -> np.ndarray:
    v = np.zeros(DIM)
    for name, w in weights.items():
        if name not in INDEX:
            raise KeyError(f"unknown primitive: {name}")
        v[INDEX[name]] = float(np.clip(w, 0.0, 1.0))
    return v


def describe(v: np.ndarray, threshold: float = 0.3) -> list[tuple[str, float]]:
    order = np.argsort(-v)
    return [(PRIMITIVES[i], round(float(v[i]), 2)) for i in order if v[i] >= threshold]


def preset(vector_id: int) -> tuple[str, np.ndarray]:
    if vector_id not in PRESETS:
        raise KeyError(f"unknown vector id: {vector_id:#06x}")
    name, weights = PRESETS[vector_id]
    return name, vector(weights)
