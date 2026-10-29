"""Dog bark -> emotional state, trained on real recordings.

Features: log-mel and MFCC statistics plus a few prosodic measures, computed
with numpy only. Model: one softmax-regression head for arousal and one for
valence.
"""
from __future__ import annotations

import json
import wave

import numpy as np

SR = 16000
N_FFT = 512
HOP = 160
N_MELS = 40
N_MFCC = 13

AROUSAL = ("Low", "Medium", "High")
VALENCE = ("Negative", "Neutral", "Positive")


def load_wav(path: str) -> np.ndarray:
    with wave.open(path) as w:
        if w.getsampwidth() != 2:
            raise ValueError(f"{path}: expected 16-bit PCM")
        x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32)
        if w.getnchannels() > 1:
            x = x.reshape(-1, w.getnchannels()).mean(axis=1)
        sr = w.getframerate()
    if sr != SR:  # simple linear resample
        x = np.interp(np.linspace(0, len(x) - 1, int(len(x) * SR / sr)), np.arange(len(x)), x)
    return x / 32768.0


def _mel_filters() -> np.ndarray:
    def hz2mel(f): return 2595 * np.log10(1 + f / 700)
    def mel2hz(m): return 700 * (10 ** (m / 2595) - 1)
    pts = mel2hz(np.linspace(hz2mel(50), hz2mel(SR / 2), N_MELS + 2))
    bins = np.floor((N_FFT + 1) * pts / SR).astype(int)
    fb = np.zeros((N_MELS, N_FFT // 2 + 1))
    for m in range(1, N_MELS + 1):
        lo, c, hi = bins[m - 1], bins[m], bins[m + 1]
        fb[m - 1, lo:c] = (np.arange(lo, c) - lo) / max(c - lo, 1)
        fb[m - 1, c:hi] = (hi - np.arange(c, hi)) / max(hi - c, 1)
    return fb


_FB = _mel_filters()
_DCT = np.cos(np.pi / N_MELS * (np.arange(N_MELS) + 0.5)[None, :] * np.arange(N_MFCC)[:, None])


def features(x: np.ndarray) -> np.ndarray:
    if len(x) < N_FFT:
        x = np.pad(x, (0, N_FFT - len(x)))
    n = 1 + (len(x) - N_FFT) // HOP
    frames = np.stack([x[i * HOP:i * HOP + N_FFT] for i in range(n)]) * np.hanning(N_FFT)
    spec = np.abs(np.fft.rfft(frames, axis=1)) ** 2
    logmel = np.log(spec @ _FB.T + 1e-10)
    mfcc = logmel @ _DCT.T
    freqs = np.fft.rfftfreq(N_FFT, 1 / SR)
    centroid = (spec * freqs).sum(1) / (spec.sum(1) + 1e-10)
    rms = np.sqrt((frames ** 2).mean(1))
    zcr = (np.abs(np.diff(np.sign(frames), axis=1)) > 0).mean(1)
    loud = rms > 0.1 * rms.max()
    extra = np.array([
        np.log(len(x) / SR + 1e-3),            # duration
        loud.mean(),                           # fraction of time barking
        np.abs(np.diff(loud.astype(int))).sum() / (len(x) / SR),  # onsets per sec
        np.log(rms.max() + 1e-6),
    ])
    stats = lambda a: np.concatenate([a.mean(0), a.std(0)])
    return np.concatenate([
        stats(mfcc), stats(logmel[:, ::4]),
        [centroid.mean(), centroid.std(), zcr.mean(), zcr.std(), rms.std() / (rms.mean() + 1e-9)],
        extra,
    ])


class SoftmaxHead:
    def __init__(self, classes, l2=1e-2, lr=0.1, epochs=800):
        self.classes, self.l2, self.lr, self.epochs = tuple(classes), l2, lr, epochs

    def fit(self, X: np.ndarray, y: np.ndarray) -> "SoftmaxHead":
        k = len(self.classes)
        Y = np.eye(k)[y]
        # inverse-frequency class weights so rare classes are not ignored
        cw = len(y) / (k * np.maximum(np.bincount(y, minlength=k), 1))
        sw = cw[y][:, None]
        self.W = np.zeros((X.shape[1], k))
        self.b = np.zeros(k)
        for _ in range(self.epochs):
            P = self.proba(X)
            G = (P - Y) * sw / len(X)
            self.W -= self.lr * (X.T @ G + self.l2 * self.W)
            self.b -= self.lr * G.sum(0)
        return self

    def proba(self, X: np.ndarray) -> np.ndarray:
        z = X @ self.W + self.b
        z -= z.max(1, keepdims=True)
        e = np.exp(z)
        return e / e.sum(1, keepdims=True)

    def predict(self, X: np.ndarray) -> np.ndarray:
        return self.proba(X).argmax(1)


class BarkModel:
    def __init__(self):
        self.mu = self.sd = None
        self.heads: dict[str, SoftmaxHead] = {}

    def fit(self, X, y_arousal, y_valence, **kw) -> "BarkModel":
        self.mu, self.sd = X.mean(0), X.std(0) + 1e-8
        Z = (X - self.mu) / self.sd
        self.heads = {"arousal": SoftmaxHead(AROUSAL, **kw).fit(Z, y_arousal),
                      "valence": SoftmaxHead(VALENCE, **kw).fit(Z, y_valence)}
        return self

    def predict_proba(self, X) -> dict[str, np.ndarray]:
        Z = (np.atleast_2d(X) - self.mu) / self.sd
        return {k: h.proba(Z) for k, h in self.heads.items()}

    def interpret(self, x_feat: np.ndarray) -> dict:
        p = {k: v[0] for k, v in self.predict_proba(x_feat).items()}
        a, v = AROUSAL[p["arousal"].argmax()], VALENCE[p["valence"].argmax()]
        return {"arousal": a, "valence": v,
                "confidence": round(float(min(p["arousal"].max(), p["valence"].max())), 2),
                "meaning": PHRASES[(a, v)]}

    def save(self, path: str) -> None:
        with open(path, "w") as f:
            json.dump({"mu": self.mu.tolist(), "sd": self.sd.tolist(),
                       "heads": {k: {"W": h.W.tolist(), "b": h.b.tolist()}
                                 for k, h in self.heads.items()}}, f)

    @classmethod
    def load(cls, path: str) -> "BarkModel":
        with open(path) as f:
            d = json.load(f)
        m = cls()
        m.mu, m.sd = np.array(d["mu"]), np.array(d["sd"])
        for k, classes in (("arousal", AROUSAL), ("valence", VALENCE)):
            h = SoftmaxHead(classes)
            h.W, h.b = np.array(d["heads"][k]["W"]), np.array(d["heads"][k]["b"])
            m.heads[k] = h
        return m


# Plain-English reading of each arousal/valence combination. This is the
# most a bark can honestly tell you: how worked up the dog is and whether
# the feeling is good or bad, not a sentence.
PHRASES = {
    ("High", "Positive"): "Very excited and happy (play, greeting).",
    ("High", "Neutral"): "Highly aroused, unclear mood (alert, attention-seeking).",
    ("High", "Negative"): "Agitated or distressed (warning, fear, frustration).",
    ("Medium", "Positive"): "Happy and engaged.",
    ("Medium", "Neutral"): "Attentive, calling out.",
    ("Medium", "Negative"): "Uneasy or annoyed.",
    ("Low", "Positive"): "Relaxed and content.",
    ("Low", "Neutral"): "Calm, low-key vocalization.",
    ("Low", "Negative"): "Quietly unhappy (whining, sulking).",
}
