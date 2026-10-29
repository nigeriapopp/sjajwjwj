"""Translate dog signals into English.

  # real dog audio (needs a trained model)
  python3 src/translator_core.py --audio path/to/bark.wav

  # simulated brain signal (no real hardware is used)
  python3 src/translator_core.py --simulate alert
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from nscb import bark, drivers, engine  # noqa: E402


def from_audio(path: str, model_path: str) -> str:
    if not os.path.exists(model_path):
        sys.exit("No model. Run: python3 src/fetch_dog_data.py && "
                 "python3 src/train_cross_species_weights.py")
    m = bark.BarkModel.load(model_path)
    r = m.interpret(bark.features(bark.load_wav(path)))
    return (f"{r['meaning']}\n  arousal={r['arousal']} valence={r['valence']} "
            f"confidence={r['confidence']}")


def from_simulation(state: str, seed: int) -> str:
    train = [(s, drivers.SimulatedMesh(s, seed=i).read()) for i in range(20)
             for s in drivers.STATE_BANDS]
    dec = engine.StateDecoder().fit(train)
    label, conf = dec.predict(drivers.SimulatedMesh(state, seed=seed + 1000).read())
    return f"[SIMULATED SIGNAL] {engine.describe_state(label, conf)} (state={label}, conf={conf})"


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--audio")
    g.add_argument("--simulate", choices=sorted(drivers.STATE_BANDS))
    ap.add_argument("--model", default="models/bark_model.json")
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()
    print(from_audio(a.audio, a.model) if a.audio else from_simulation(a.simulate, a.seed))


if __name__ == "__main__":
    main()
