# Predictive Keyboard (Android IME)

A custom Android keyboard (Input Method Editor) that shows the six most likely
*next* letters — based on what you've typed so far — on the number-row keys.
When only one word in the dictionary still matches what you've typed, the
space bar turns green; tapping it completes the word.

## How the prediction works

- Words are loaded from `app/src/main/assets/wordlist.txt` (one word per
  line, order doesn't matter) into a trie at keyboard startup.
- Each trie node tracks how many dictionary words pass through it. For the
  current prefix, the six child letters shared by the most words are shown
  on the top row, ranked left-to-right (or right-to-left, per the setting
  in the keyboard's Settings screen). This is purely a function of the
  prefix already typed — no overall word-frequency weighting is involved,
  so an unordered word list works exactly as well as a frequency-sorted one.
- Tapping one of those six letters types it directly, letting you skip
  hunting for the key.
- When the current prefix's node has **exactly one** word left in its
  subtree, the space bar turns green and shows the full word; tapping it
  commits the rest of the word plus a space.

## About the word list

`assets/wordlist.txt` is built from the "Google 10000 English" frequency
list (no-swears variant). Its order is no longer meaningful to the engine
(see above) — it's kept in its original order purely by convenience.

It's been scrubbed of:
- single letters
- explicit day/month shorthand (mon–sun, jan–dec)
- tokens with no vowel at all (html, ctrl, www, pdf, nba-style acronyms,
  state/country-style two-letter codes, etc.)
- a further hand-reviewed batch of ~40 ambiguous abbreviations (info, usa,
  fax, corp, inc, vol, ave, utc, nato, etc.) that were removed on request

9,359 words remain. If you'd rather use the actual **Oxford 5000** list
instead — Oxford University Press's proprietary, CEFR-leveled list — you'd
need to license/obtain it separately (it isn't reproduced here for
copyright reasons) and drop it in as a same-format replacement.

Format: one word per line. No frequency column needed.

## Project layout

- `PredictiveKeyboardService.kt` — the `InputMethodService`; owns the typed
  buffer, drives the prediction engine, and reacts to key taps.
- `PredictionEngine.kt` — trie + frequency ranking logic (pure Kotlin, no
  Android dependencies — easy to unit test on its own).
- `KeyboardPanelView.kt` — the letters keyboard: 6 dynamic prediction keys
  on top, QWERTY rows below, and the space bar.
- `SymbolKeyboardView.kt` — the secondary keyboard: digits on top,
  punctuation/symbols below.
- `Prefs.kt` — persisted settings (prediction-row direction, etc).
- `SettingsActivity.kt` — the screen linked from Android's
  Settings → System → Languages & input → your keyboard.

## Building via GitHub Actions

Push this project to a repo and the included workflow
(`.github/workflows/android-build.yml`) will build a debug APK on every push
and upload it as a build artifact — no local Android Studio needed. Grab the
APK from the Actions run's "Artifacts" section.

## Enabling the keyboard on a device

1. Install the built APK.
2. Settings → System → Languages & input → On-screen keyboard → Manage
   keyboards → enable "Predictive Keyboard".
3. Switch to it from any text field via the keyboard-switch icon (or
   long-press the spacebar of your current keyboard).
