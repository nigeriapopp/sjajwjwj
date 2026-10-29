"""Encoder/decoder between text, brain-state features and the concept space."""
from __future__ import annotations

import json
import re

import numpy as np

from . import concepts
from .drivers import BAND_CENTERS_HZ, SAMPLE_RATE_HZ, Frame

# ---------- text <-> concepts ----------

LEXICON = {
    "food": {"food": 1.0}, "eat": {"food": 1.0}, "dinner": {"food": 1.0, "reward": 0.5},
    "treat": {"reward": 1.0, "food": 0.6}, "good": {"reward": 0.7},
    "now": {"imminent": 1.0}, "soon": {"imminent": 0.7},
    "sit": {"sit": 1.0}, "calm": {"calm": 1.0}, "easy": {"calm": 0.8},
    "play": {"play": 1.0}, "ball": {"play": 0.8},
    "home": {"home": 1.0}, "door": {"home": 0.6, "outside": 0.4},
    "walk": {"outside": 1.0}, "outside": {"outside": 1.0},
    "sleep": {"rest": 1.0}, "rest": {"rest": 1.0}, "bed": {"rest": 0.8, "home": 0.3},
}

PHRASES = {
    "rest": "Resting / low arousal.",
    "calm": "Calm and settled.",
    "alert": "Alert — something has its attention.",
    "play": "Excited, wants to play.",
    "food": "Interested in food.",
}


def encode_text(text: str) -> np.ndarray:
    v = np.zeros(concepts.DIM)
    for word in re.findall(r"[a-z]+", text.lower()):
        for name, w in LEXICON.get(word, {}).items():
            v[concepts.INDEX[name]] = max(v[concepts.INDEX[name]], w)
    return v


# ---------- brain signals -> state ----------

def band_features(frame: Frame) -> np.ndarray:
    """Relative power per band, averaged over channels."""
    spec = np.abs(np.fft.rfft(frame.samples, axis=1)) ** 2
    freqs = np.fft.rfftfreq(frame.samples.shape[1], 1 / SAMPLE_RATE_HZ)
    edges = [0.5, 4, 8, 13, 30, 50]
    power = np.array([spec[:, (freqs >= lo) & (freqs < hi)].sum(axis=1).mean()
                      for lo, hi in zip(edges[:-1], edges[1:])])
    return power / power.sum()


class StateDecoder:
    """Nearest-centroid classifier over band features, with a confidence score."""

    def __init__(self, centroids: dict[str, np.ndarray] | None = None):
        self.centroids = centroids or {}

    def fit(self, labelled: list[tuple[str, Frame]]) -> "StateDecoder":
        groups: dict[str, list] = {}
        for label, fr in labelled:
            groups.setdefault(label, []).append(band_features(fr))
        self.centroids = {k: np.mean(v, axis=0) for k, v in groups.items()}
        return self

    def predict(self, frame: Frame) -> tuple[str, float]:
        if not self.centroids:
            raise RuntimeError("decoder is not trained")
        f = band_features(frame)
        dists = {k: float(np.linalg.norm(f - c)) for k, c in self.centroids.items()}
        ranked = sorted(dists, key=dists.get)
        best, second = ranked[0], ranked[1] if len(ranked) > 1 else ranked[0]
        conf = 1.0 - dists[best] / (dists[second] + 1e-9) if best != second else 1.0
        return best, round(conf, 3)

    def save(self, path: str) -> None:
        with open(path, "w") as f:
            json.dump({k: v.tolist() for k, v in self.centroids.items()}, f, indent=2)

    @classmethod
    def load(cls, path: str) -> "StateDecoder":
        with open(path) as f:
            return cls({k: np.array(v) for k, v in json.load(f).items()})


def describe_state(state: str, confidence: float) -> str:
    phrase = PHRASES.get(state, state)
    hedge = "" if confidence >= 0.3 else " (low confidence)"
    return f"{phrase}{hedge}"


# ---------- concepts -> species stimulus pattern ----------

# Which sensory channel each species profile favours. Weights are
# placeholders for the simulation, not physiological values.
SPECIES_PROFILES = {
    "canine": {"olfactory": 0.6, "auditory": 0.25, "visual": 0.15},
    "avian": {"visual": 0.55, "spatial": 0.3, "auditory": 0.15},
    "feline": {"auditory": 0.4, "visual": 0.35, "olfactory": 0.25},
}


def decode_to_species(concept: np.ndarray, species: str, name: str = "custom") -> dict:
    if species not in SPECIES_PROFILES:
        raise KeyError(f"unknown species profile: {species}")
    profile = SPECIES_PROFILES[species]
    active = concepts.describe(concept, threshold=0.01)
    return {
        "name": name,
        "species": species,
        "concept": active,
        "channels": {ch: round(w * float(concept.max(initial=0)), 3)
                     for ch, w in profile.items()},
    }
