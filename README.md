# Neural Syllable & Concept Bridge (NSCB)

A software prototype of the NSCB spec. **Only one part is real:** a
dog-bark emotion model trained on real recordings. The neural hardware
(`/dev/neuro_mesh_0`, `/dev/neuro_pulse_tx0`) does not exist, so those
drivers are simulated, and nothing is ever sent to an animal.

## What's real vs simulated

| Part | Status |
|---|---|
| Bark → arousal/valence → English (`nscb/bark.py`) | **Real data**: 1,200 Husky/Shiba Inu clips |
| Brain-signal reader (`SimulatedMesh`, `ReplayMesh`) | Simulated signals; CSV logging/replay works |
| Pulse transmitter (`SimulatedPulse`) | Records commands only, emits nothing |
| Safety interlock (`nscb/safety.py`) | Real code, unit-tested |
| Text → concept encoder, species decoder | Toy keyword/weight tables |

## Quick start

```bash
pip install numpy
python3 src/fetch_dog_data.py               # ~50 MB, ~30 s
python3 src/train_cross_species_weights.py  # ~3 s, prints accuracy
python3 src/translator_core.py --audio some_bark.wav
python3 src/translator_core.py --simulate alert
python3 src/neuro_pulse_tx.py --vector_id 0x4F9A --intensity 0.02
python3 -m unittest discover -s tests
```

## Bark model results (validation = dogs not seen in training)

| Head | Accuracy | Chance |
|---|---|---|
| Arousal (Low/Medium/High) | ~46% | 33% |
| Valence (Negative/Neutral/Positive) | ~39% | 33% |

Above chance, but not reliable. Arousal (how worked up the dog is) comes
through in the sound; valence (happy or upset) mostly needs context like
body language. A bark carries a coarse emotional state, not a sentence. No
current method decodes something like "intruder at the perimeter window".

Dataset: EmotionalCanines / Barkopedia (Dang et al., ACM MM 2025),
[Hugging Face](https://huggingface.co/datasets/ArlingtonCL2/BarkopediaDogEmotionClassification_Data),
CC BY-NC-SA 4.0. It's non-commercial, so the audio and trained weights are
git-ignored; each user downloads and trains locally.

## Safety interlock

Every transmission goes through `SafetyInterlock.send`, which enforces:
- at least 500 ms between pulses
- link cut if heart rate rises more than 15% above baseline, or tissue temp rises >0.5 °C, or stress >0.4
- intensity capped at 0.05
- fear/survival-related concepts (`alert`, `stranger`) can never be sent
- once cut, the link stays down until a named human operator calls `reset()`

Any real stimulation of animals would need veterinary oversight and
animal-ethics (IACUC or equivalent) approval. This repo does not do it.

## Other real dog data worth trying
- Aalto University: [dog EEG responses to faces/objects](https://research.aalto.fi/en/datasets/dog-visual-perception-of-faces-objects-and-phase-scrambled-images/) (non-invasive, 7–9 channels)
- Canine epilepsy intracranial EEG (see the [dataset review](https://arxiv.org/pdf/2306.12292))
