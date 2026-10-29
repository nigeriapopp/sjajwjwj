import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))
from nscb import bark, concepts, drivers, engine, safety  # noqa: E402


class FakeClock:
    def __init__(self): self.t = 0.0
    def __call__(self): return self.t


def calm_vitals(hr=80.0): return drivers.Vitals(hr, 38.5, 0.1)


class SafetyTests(unittest.TestCase):
    def setUp(self):
        self.clock = FakeClock()
        self.tx = drivers.SimulatedPulse()
        self.lock = safety.SafetyInterlock(self.tx, baseline_hr=80.0, clock=self.clock)
        _, self.vec = concepts.preset(0x4F9A)

    def send(self, intensity=0.02, vitals=None):
        self.lock.send(self.vec, intensity, vitals or calm_vitals(), {"name": "t"})

    def test_normal_send(self):
        self.send()
        self.assertEqual(len(self.tx.sent), 1)

    def test_rate_limit_500ms(self):
        self.send()
        self.clock.t = 0.4
        with self.assertRaises(safety.SafetyTrip):
            self.send()
        self.clock.t = 0.6
        self.send()
        self.assertEqual(len(self.tx.sent), 2)

    def test_heart_rate_spike_severs_link_until_reset(self):
        with self.assertRaises(safety.SafetyTrip):
            self.send(vitals=calm_vitals(hr=93))  # > +15%
        self.assertTrue(self.lock.severed)
        self.clock.t = 10
        with self.assertRaises(safety.SafetyTrip):
            self.send()  # still severed with normal vitals
        self.lock.reset("operator")
        self.send()
        self.assertEqual(len(self.tx.sent), 1)

    def test_intensity_cap(self):
        with self.assertRaises(safety.SafetyTrip):
            self.send(intensity=0.5)
        self.assertEqual(self.tx.sent, [])

    def test_blocked_primitives(self):
        with self.assertRaises(safety.SafetyTrip):
            self.lock.send(concepts.vector({"alert": 1.0}), 0.02, calm_vitals(), {})

    def test_stress_and_temperature(self):
        for v in (drivers.Vitals(80, 38.5, 0.9), drivers.Vitals(80, 39.5, 0.1)):
            lock = safety.SafetyInterlock(drivers.SimulatedPulse(), baseline_hr=80, clock=self.clock)
            with self.assertRaises(safety.SafetyTrip):
                lock.send(self.vec, 0.02, v, {})


class EngineTests(unittest.TestCase):
    def test_encode_text(self):
        d = dict(concepts.describe(engine.encode_text("Dinner now!")))
        self.assertEqual(d["food"], 1.0)
        self.assertEqual(d["imminent"], 1.0)

    def test_simulated_state_decoder(self):
        train = [(s, drivers.SimulatedMesh(s, seed=i).read()) for i in range(15)
                 for s in drivers.STATE_BANDS]
        dec = engine.StateDecoder().fit(train)
        hits = sum(dec.predict(drivers.SimulatedMesh(s, seed=500 + i).read())[0] == s
                   for i in range(5) for s in drivers.STATE_BANDS)
        self.assertGreater(hits / (5 * len(drivers.STATE_BANDS)), 0.8)

    def test_csv_log_roundtrip(self):
        import tempfile
        f = drivers.SimulatedMesh("calm", seed=3).read()
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "log.csv")
            drivers.log_frames(p, [f])
            g = drivers.ReplayMesh(p).read()
        np.testing.assert_allclose(g.samples, f.samples, atol=1e-3)


class BarkTests(unittest.TestCase):
    def test_features_and_head_on_synthetic_audio(self):
        rng = np.random.default_rng(0)
        t = np.arange(8000) / bark.SR
        X = np.array([bark.features(np.sin(2 * np.pi * (300 if i % 2 else 1200) * t)
                                    + rng.normal(0, 0.05, t.size)) for i in range(40)])
        y = np.arange(40) % 2
        m = bark.BarkModel().fit(X, y, y)
        self.assertEqual((m.heads["arousal"].predict((X - m.mu) / m.sd) == y).mean(), 1.0)


if __name__ == "__main__":
    unittest.main()
