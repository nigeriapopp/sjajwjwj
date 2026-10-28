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
   From version 1.5.0 on, new Vapor versions install **over** the old one (Vapor offers them itself), so your games and logins stay.
   Older builds (1.4.0) each had their own signing key: uninstall one of those once before installing 1.5.0.
4. Open Vapor, sign in to Steam (QR code or username + password + Steam Guard).
5. Pick a game → **Install** → wait for the download → **Play**.

**"App not installed" / "package conflicts with an existing package"?** Your phone has a Vapor (or GameNative)
signed with a different key. Uninstall it, then install the new APK. Also check you have about 2 GB free: the APK is
~580 MB and Android needs room to unpack it.

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

**Fast Launch** — upstream rewrote about 170 MB of graphics files (GPU wrapper, a 112 MB library bundle, zink DLLs,
DXVK) on *every* launch. Vapor reuses them when nothing changed and checks the key files are still there.
If a game closes within 40 seconds (a likely crash) the next launch does a full unpack automatically, and
Settings → Performance → **Repair game files** forces one. Also: GPU detection is cached in memory instead of
re-creating a Vulkan instance per check, the GPU shader cache is 2 GB instead of 512 MB (fewer recompiles,
shorter loading screens), and high-end phones use the fastest download tier.

**Save snapshots** — game menu → **Saves**: *Save now* copies the game's save folders (Documents, AppData,
Saved Games, skipping caches) into a slot. Tap a slot → *Load this save*: your current saves are backed up,
the game closes, and the snapshot is put back right before you press Play again. There's also a one-tap
**Quick save** button on the menu's side rail. Keeps the newest 12 snapshots per game.
(Windows games under Wine can't be frozen mid-frame like console emulators, so these are save-file snapshots.)

**Phone graphics presets** — on a game's first launch Vapor converts its graphics settings to what your phone
runs well: Unreal Engine 4/5 games get scalability levels, render scale and an FPS limit picked for your phone's class
(shadows, foliage and effects drop first, textures stay higher); Bethesda games (Oblivion, Fallout 3/NV/4, Skyrim) get
smaller shadow maps and no MSAA or depth of field. After that your own in-game changes are kept.
Settings → Performance → Graphics & saves.

**Save safety** — before every launch Vapor makes sure the Windows save folders (Documents, My Games, Saved Games,
AppData Local/LocalLow/Roaming) exist and are writable, so games don't fail to save. After each session it keeps an
automatic backup of changed saves (newest 3, under game menu → Saves).

**Crash Guard** — when a game closes with an error, Vapor saves the game and Wine log and repairs the game files on the
next launch. Settings → Performance → **Share last game crash log** sends it so the problem can be found.

**Updates** — Vapor checks this repo's Releases when it opens and offers a one-tap update that installs over the
current app (Settings → Performance → Check for updates).

**Deluxe game menu** — the game's title in a gradient with clock and battery chips, a springy slide-in,
tabs that glide into each other, and the game dims behind the menu (except on tabs that preview the picture).

**Minecraft: Java Edition** — side menu → *Minecraft: Java Edition*. Vapor sets you up with an official
open-source Java Edition launcher (Zalith Launcher 2 recommended, or Amethyst — their Microsoft sign-in is
approved), launches it in one tap, and walks you through sign-in, Fabric, performance mods (Sodium, Lithium,
FerriteCore or the Fabulously Optimized pack) and memory / render distance / resolution / FPS tuned for your
phone. Tap **Install** and Vapor downloads the launcher from its official GitHub release and opens Android's installer.
You need to own Java Edition.

**Smart Controls** — pick up a controller and the touch controls disappear; touch the screen and they come back.
The touch that brings them back doesn't press anything, so grabbing the phone never fires a shot. If you turn
controls on or off yourself from the game menu, Vapor leaves your choice alone for that session.

**Ghost controls** — touch controls fade to a faint outline after 4 seconds untouched so you can see the game,
and snap back the moment you touch.

**Controller notices** — "Xbox controller connected · Player 1 · 80% battery", disconnect notices, and a warning
when a controller's battery drops below 15%.

**In-game menu** — one-tap 30 / 40 / 45 / 60 FPS buttons under the FPS limiter.

**After each game** — a one-line recap: play time, average FPS, 1% low FPS, battery temperature.

**Fallout 3 / Fallout 3 GOTY / New Vegas / Oblivion** — set up to start reliably under Wine:
- the game starts directly instead of through its launcher (the launcher's Play button is a known crash point);
- the many-core startup crash fix (`bUseThreadedAI=1`, `iNumHWThreads=2`) is in place before the first launch, and
  the game is shown 4 CPU cores — the phone's fast ones (`WINE_CPU_TOPOLOGY`);
- missing settings files are created from the game's own Low/Medium preset, windowed at exactly the screen size
  Vapor renders (fullscreen mode switches are a classic Wine crash), with phone-friendly shadows and no MSAA;
- if the game still crashes, the next launch turns off Wine's media decoder (winegstreamer) for that game, the
  best-known fix for Fallout 3 / Oblivion crashes under Wine (in-game music may be silent in that mode). Quitting from
the menu never counts as a crash. Settings → Performance → **Reset Crash Guard** turns safe mode off again.

**First launches** — a notice that early shader stutter is normal, plus one short tip in each of your first 3 sessions.

**Settings → Performance** (grouped into Your phone · Controls · In game):
- **Default FPS cap** — 30 / 40 / 45 / 60 / 90 / 120 for games you haven't tweaked.
- **Smart controls** and **Ghost controls** on/off.
- **Heat step-down** (off by default) — drops to 45 FPS when the phone overheats (30 if critical) and restores your cap when it cools.
- **Do Not Disturb while playing** (off by default).
- **Play-time reminders** (off by default) — a notice every hour.
- **Stable FPS mode** — Android sustained-performance mode: slightly lower peak, much less throttling in long sessions.
- **Lock screen to max refresh rate** — stops 120 Hz screens dropping their refresh rate mid-game.
- **Respect Battery Saver** — games start at 30 FPS when Battery Saver or Game Booster's battery mode is on.
- **Heat and battery warnings** — a notice when the phone overheats, the battery drops below 15%, or memory is low at launch.
- **Phone Check** has a Share button.

**True-black AMOLED theme** — Settings → Interface → theme "Dark + AMOLED" now really uses black backgrounds.

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

Every build is signed with the same key so new versions install as updates. CI takes the key from the repository
secret `VAPOR_SIGNING_KEY_B64` (base64 Android keystore, alias `androiddebugkey`, passwords `android`) if set;
otherwise it creates one once and keeps it in this repo's private Actions cache. GitHub drops caches unused for
7 days, so for a key that never changes, add the secret. The version code is 1000 + the CI run number.

Local build: Android Studio, JDK 17, then `./gradlew :app:bundleLegacyRelease`.

## License

GPL-3.0, same as GameNative. See `LICENSE` and `THIRD_PARTY_NOTICES`.
