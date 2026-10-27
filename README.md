# Vapor — your Steam library, running on your phone

Vapor plays the Windows PC games you own on Steam (plus Epic, GOG and Amazon) **on the phone itself**. No PC, no streaming.
Sign in with your Steam account, tap a game, it downloads, and it runs through Wine + an x86→ARM translator,
with settings tuned for your phone automatically.

Vapor is a fork of [GameNative](https://github.com/utkarshdalal/GameNative) (GPL-3.0), which builds on
[Winlator](https://github.com/brunodev85/winlator). Vapor adds a phone auto-tuner and phone-specific
performance features on top. All credit for the emulation stack goes to those projects.

---

## Install (on your phone)

1. Open this repo's **Releases** page on your phone and download the newest `Vapor-….apk`.
2. Tap the file and allow "Install unknown apps" for your browser when Android asks.
3. If you already have **GameNative** installed, uninstall it first — Vapor uses the same internal app ID.
4. Open Vapor, sign in to Steam (QR code or username + password + Steam Guard).
5. Pick a game → **Install** → wait for the download → **Play**.

First launch downloads the Windows runtime (Wine/Proton, drivers, DXVK) once — use Wi-Fi.

## How it works

| Piece | What it does |
|---|---|
| JavaSteam | Native Android Steam login, library, downloads and cloud saves — no Steam client in Wine needed |
| Wine / Proton (ARM64EC) | Runs the Windows game |
| FEX / Box64 | Translates x86 game code to ARM |
| DXVK / VKD3D | Turns DirectX 9–12 into Vulkan |
| Turnip / system Vulkan | GPU driver (Turnip on Snapdragon/Adreno, the phone's own driver on Exynos/Mali) |

## What Vapor adds

**Phone Auto-Tune** — on first launch Vapor reads your chip, GPU, RAM and screen and picks defaults:
- Sorts the phone into Flagship / High-end / Mid-range / Entry-level using a table of known chips
  (Snapdragon 8 Gen 1–8 Elite Gen 5, 7-series, Exynos 2100–2500, Dimensity 8000/9000, Tensor G1–G5) with a GPU-based fallback for anything else.
- Sets the default FPS cap: 60 on Flagship/High-end; 45 on Mid-range phones with 90 Hz screens, otherwise 30; 30 on Entry-level.
- Drops entry-level phones to 960×540 so games stay playable.
- Turns the FPS counter on so you can see how a game runs.
- Re-run it any time: **Settings → Performance → Re-run Phone Auto-Tune**.

**Phone Check** (Settings → Performance) — a report of your phone, chip, GPU, RAM, refresh rate, performance class,
what Vapor set, and tips for that exact phone.

**Galaxy S22 / S22+ / S22 Ultra support** — both versions are recognised:
- *Snapdragon 8 Gen 1 (US, China, many others)* → Turnip driver (best compatibility), 60 FPS cap.
- *Exynos 2200 (Europe and others)* → Samsung's own Vulkan driver, since Turnip doesn't support Xclipse GPUs.
- Tips shown in Phone Check for heat, case removal, and Game Booster.

**Performance settings** (Settings → Performance):
- **Default FPS cap** — 30 / 40 / 45 / 60 / 90 / 120 for games you haven't tweaked.
- **Stable FPS mode** — Android sustained-performance mode: slightly lower peak, much less throttling in long sessions.
- **Lock screen to max refresh rate** — stops 120 Hz screens dropping their refresh rate mid-game.
- **Respect Battery Saver** — games start at 30 FPS when Battery Saver or Game Booster's battery mode is on.
- **Heat and battery warnings** — a notice when the phone overheats or the battery drops below 15%.

**Android Game Mode** — Vapor registers with Android's Game Dashboard and Samsung Game Booster, so their
Performance / Battery modes show up and Vapor follows them.

**Other changes** — the upstream auto-updater is off (it would offer GameNative builds that can't install over Vapor),
the "Source code" link points here, analytics have no key in Vapor builds, and the app is renamed Vapor.

## Already built in (from GameNative)

- Steam, Epic, GOG and Amazon libraries; Steam cloud saves; Steam achievements; Workshop mods
- Community-tested configs applied per game automatically, plus a compatibility badge per game
- On-screen touch controls with an editor; controller support (Xbox, PlayStation, Joy-Con and others), gyro
- Performance HUD (FPS, CPU, GPU, temperature, battery)
- FPS limiter with an adaptive cap that steps down when the phone can't hold the target
- Frame generation (LSFG) to double the displayed frame rate
- CPU/GPU power control on Samsung phones (Samsung Performance SDK) and handhelds
- Per-game settings: resolution, driver, DXVK/VKD3D version, translator presets, env vars, Windows components
- External display support, screen effects (sharpening)

## Honest expectations

Phones aren't PCs, and translating x86 Windows code to ARM costs performance. Roughly:

| Phone class | Typical result |
|---|---|
| Flagship (8 Gen 3 / 8 Elite / Dimensity 9300+) | 60 FPS in most indie and 2010s games; many newer games at 30–60 FPS on low settings |
| High-end (S22 / 8 Gen 1 / 888 / Exynos 2200) | 60 FPS in indie and older games; 30–45 FPS in 2015–2019 3D games on low |
| Mid-range | 2D, indie and older 3D games run well; new 3D games are usually too heavy |
| Entry-level | 2D, indie and early-2000s games |

Not every game runs. Games with kernel anti-cheat (Valorant, Fortnite, many online shooters) will never run.
Check a game's community rating on its page before downloading something big.

**S22 tips:** the Snapdragon 8 Gen 1 runs hot. For long sessions of heavy 3D games, cap at 40–45 FPS,
turn on **Stable FPS mode**, use Game Booster's performance profile, and take the phone out of its case.

## Building

Every push builds an APK with GitHub Actions (`.github/workflows/build-apk.yml`) and publishes it as a Release.

To keep the same signing key across builds (so new versions install as updates instead of needing a reinstall),
add a repository secret `VAPOR_SIGNING_KEY_B64` containing a base64-encoded Android `debug.keystore`
(alias `androiddebugkey`, passwords `android`). Without it, each build gets a fresh key.

Local build: Android Studio, JDK 17, then `./gradlew :app:bundleLegacyRelease`.

## License

GPL-3.0, same as GameNative. See `LICENSE` and `THIRD_PARTY_NOTICES`.
