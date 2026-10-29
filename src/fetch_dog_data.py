"""Download the EmotionalCanines / Barkopedia dog-bark dataset.

Real recordings of Husky and Shiba Inu barks, each labelled with arousal
(Low/Medium/High) and valence (Negative/Neutral/Positive).
Source: https://huggingface.co/datasets/ArlingtonCL2/BarkopediaDogEmotionClassification_Data
License: CC BY-NC-SA 4.0 (non-commercial). Audio is NOT committed to git.
"""
import argparse
import csv
import os
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = ("https://huggingface.co/datasets/ArlingtonCL2/"
        "BarkopediaDogEmotionClassification_Data/resolve/main")
SPLITS = ("train", "validation")
BREEDS = ("husky", "shiba")


def fetch(url: str, dest: str) -> None:
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=60) as r, open(dest + ".part", "wb") as f:
                f.write(r.read())
            os.replace(dest + ".part", dest)
            return
        except OSError:
            if attempt == 3:
                raise


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="data/barkopedia")
    ap.add_argument("--workers", type=int, default=16)
    args = ap.parse_args()

    jobs = []
    for split in SPLITS:
        for breed in BREEDS:
            labels = f"{breed}_{split}_labels.csv"
            fetch(f"{BASE}/{labels}", os.path.join(args.out, labels))
            with open(os.path.join(args.out, labels)) as f:
                for row in csv.DictReader(f):
                    rel = f"{split}/{breed}/{row['audio_id']}.wav"
                    jobs.append((f"{BASE}/{rel}", os.path.join(args.out, rel)))

    with ThreadPoolExecutor(args.workers) as pool:
        list(pool.map(lambda j: fetch(*j), jobs))
    print(f"{len(jobs)} clips in {args.out}")


if __name__ == "__main__":
    main()
