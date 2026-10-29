"""Train the bark-emotion model on real dog recordings and report accuracy.

  python3 src/fetch_dog_data.py            # once, downloads ~50 MB
  python3 src/train_cross_species_weights.py

Train and validation sets contain different dogs, so validation scores
measure how well the model generalizes to an unseen dog.
"""
import argparse
import csv
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from nscb import bark  # noqa: E402


def load_split(root: str, split: str):
    X, ya, yv, ids = [], [], [], []
    for breed in ("husky", "shiba"):
        with open(os.path.join(root, f"{breed}_{split}_labels.csv")) as f:
            for row in csv.DictReader(f):
                a, v = row["arousal"].strip(), row["valence"].strip()
                path = os.path.join(root, split, breed, row["audio_id"] + ".wav")
                X.append(bark.features(bark.load_wav(path)))
                ya.append(bark.AROUSAL.index(a))
                yv.append(bark.VALENCE.index(v))
                ids.append(row["audio_id"])
    return np.array(X), np.array(ya), np.array(yv), ids


def report(name, classes, y, pred):
    k = len(classes)
    cm = np.zeros((k, k), int)
    for t, p in zip(y, pred):
        cm[t, p] += 1
    f1s = []
    for i in range(k):
        prec = cm[i, i] / max(cm[:, i].sum(), 1)
        rec = cm[i, i] / max(cm[i].sum(), 1)
        f1s.append(2 * prec * rec / max(prec + rec, 1e-9))
    acc = (y == pred).mean()
    majority = np.bincount(y, minlength=k).max() / len(y)
    print(f"\n{name}: accuracy {acc:.1%}  macro-F1 {np.mean(f1s):.2f}  "
          f"(chance {1/k:.0%}, always-guess-most-common {majority:.0%})")
    print("  true \\ pred " + " ".join(f"{c[:4]:>5}" for c in classes))
    for i, c in enumerate(classes):
        print(f"  {c:<11} " + " ".join(f"{n:>5}" for n in cm[i]))
    return acc, float(np.mean(f1s))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="data/barkopedia")
    ap.add_argument("--out", default="models/bark_model.json")
    args = ap.parse_args()
    if not os.path.isdir(args.data):
        sys.exit("No data. Run: python3 src/fetch_dog_data.py")

    Xtr, atr, vtr, _ = load_split(args.data, "train")
    Xva, ava, vva, _ = load_split(args.data, "validation")
    print(f"train {len(Xtr)} clips, validation {len(Xva)} clips (different dogs)")

    # pick regularization on validation, then refit on train+validation
    best = None
    for l2 in (1e-2, 1e-1, 1.0, 3.0, 10.0):
        m = bark.BarkModel().fit(Xtr, atr, vtr, l2=l2)
        p = m.predict_proba(Xva)
        score = ((p["arousal"].argmax(1) == ava).mean() + (p["valence"].argmax(1) == vva).mean()) / 2
        print(f"  l2={l2:<6} mean val accuracy {score:.1%}")
        if best is None or score > best[0]:
            best = (score, l2, m)
    _, l2, m = best
    p = m.predict_proba(Xva)
    report("Arousal", bark.AROUSAL, ava, p["arousal"].argmax(1))
    report("Valence", bark.VALENCE, vva, p["valence"].argmax(1))

    final = bark.BarkModel().fit(np.vstack([Xtr, Xva]), np.r_[atr, ava], np.r_[vtr, vva], l2=l2)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    final.save(args.out)
    final.save_txt(os.path.splitext(args.out)[0] + ".txt")
    print(f"\nsaved {args.out} (l2={l2}, trained on all {len(Xtr) + len(Xva)} clips)")


if __name__ == "__main__":
    main()
