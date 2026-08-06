# Halo — brand & identity

The visual identity for **Halo**, the private offline companion for Colmi R0x smart rings.
This is the single source of truth for the name, palette, accents and app icon.

## Name

- **Halo** — the ring worn on your finger, the glow of your live vitals, the loop of a day's rhythm.
- Tagline: **"Your ring. Your data."** — offline, on-device, no account, no cloud.
- Android `applicationId` / namespace: `com.krejci.halo`.

## Palette — "Refined Midnight"

Evolves the original teal-on-navy DNA: a deeper ink base with a slightly richer cyan and a
warmer secondary. Colours live in `ui/Theme.kt` (Compose) and `res/values/colors.xml` (window).

### Dark (default)

| Role | Hex | Notes |
|---|---|---|
| Background (ink) | `#060A17` | deeper than the old `#0A0F22` |
| Surface (cards, nav) | `#0F1828` | |
| Surface variant | `#17223B` | |
| On-surface | `#EAF0FF` | primary text |
| On-surface dim | `#8CA0C6` | secondary text |
| Outline | `#2B3A5E` | |
| Secondary (warm) | `#FB7AA8` | coral — heart / accents |

### Light

| Role | Hex |
|---|---|
| Background | `#EEF2FB` |
| Surface | `#FFFFFF` |
| Surface variant | `#E7EEFB` |
| On-surface | `#0C1630` |
| On-surface dim | `#4D5D80` |
| Outline | `#C7D2E8` |

## Accents (user-selectable)

The **primary** colour is chosen in **You → Appearance**. Each has a bright variant for dark mode
and a deeper variant for light mode. Default is **Cyan** — the classic Halo teal.

| Accent | Dark | Light |
|---|---|---|
| **Cyan** (default) | `#22D3EE` | `#0E97B4` |
| **Violet** | `#A78BFA` | `#7C5CE0` |
| **Ember** | `#FB923C` | `#D9720F` |
| **Mint** | `#34D399` | `#0F9A6B` |
| **Rose** | `#FB7185` | `#D64C63` |

Theme mode (System / Light / Dark) and accent are persisted in SharedPreferences (`halo`) and
applied app-wide via `HaloTheme(mode, accent)` in `MainActivity`.

### Per-metric colours (semantic, not themeable)

Charts keep fixed, meaning-carrying colours independent of the accent:
HR `#F472B6` · SpO₂ `#22D3EE` · HRV `#A78BFA` · Stress `#FBBF24` · Steps `#34D399` · Sleep `#818CF8`.

## App icon

Adaptive icon (`res/mipmap-anydpi-v26/`), all vector, three layers:

- **Background** — Refined-Midnight diagonal ink gradient (`#0A1226 → #05080F`).
- **Foreground** — the **halo glow** concept: a soft radial cyan glow (the halo), a thin teal→cyan
  ring, a bright **orbiting node** on the upper-right, and a **coral heartbeat pulse** across the
  centre. The glow is what makes the name land.
- **Monochrome** — single-colour ring + node + pulse for Android 13+ themed icons.

Sources: `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`,
`ic_launcher_monochrome.xml`.

## Voice

Plain, factual, privacy-forward. Say what a number means, not what to do about it (Halo is not a
medical device). Prefer "your data stays on your phone" over marketing superlatives.
