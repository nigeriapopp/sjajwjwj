"""Send a concept through the safety interlock to the SIMULATED transmitter.

  python3 src/neuro_pulse_tx.py --vector_id 0x4F9A --intensity 0.02
  python3 src/neuro_pulse_tx.py --text "dinner now" --species canine

Nothing is emitted: SimulatedPulse only records the command.
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from nscb import concepts, drivers, engine, safety  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--vector_id", type=lambda s: int(s, 0))
    g.add_argument("--text")
    ap.add_argument("--species", default="canine", choices=sorted(engine.SPECIES_PROFILES))
    ap.add_argument("--intensity", type=float, default=0.02)
    ap.add_argument("--animal_state", default="calm", choices=sorted(drivers.STATE_BANDS))
    a = ap.parse_args()

    if a.vector_id is not None:
        name, vec = concepts.preset(a.vector_id)
    else:
        name, vec = a.text, engine.encode_text(a.text)
        if not vec.any():
            sys.exit(f"no known concepts in {a.text!r}")

    mesh = drivers.SimulatedMesh(a.animal_state, seed=1)
    baseline = mesh.read()
    tx = drivers.SimulatedPulse()
    lock = safety.SafetyInterlock(tx, baseline_hr=baseline.vitals.heart_rate_bpm)
    pattern = engine.decode_to_species(vec, a.species, name=name)
    try:
        lock.send(vec, a.intensity, mesh.read().vitals, pattern)
        print("[SIMULATED] would send:\n" + json.dumps(tx.sent[-1], indent=2))
    except safety.SafetyTrip as e:
        print(f"BLOCKED by safety interlock: {e}")
        sys.exit(2)


if __name__ == "__main__":
    main()
