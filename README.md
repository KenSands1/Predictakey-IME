# Predictive Keyboard (Android IME)

A custom Android keyboard (Input Method Editor) whose top row shows the six
most frequent whole words that start with whatever you've typed so far —
tap one to insert the rest of that word plus a space. When only one word in
the dictionary still matches what you've typed, the space bar itself also
turns green and completes it the same way.

## How the prediction works

- Words are loaded from `app/src/main/assets/wordlist.txt`, one per line,
  **ordered most-to-least frequent** — that order IS the ranking signal, so
  keep it frequency-sorted if you swap in a different list.
- For the current prefix, `PredictionEngine.topCompletions()` scans the list
  and returns the first 6 words that (a) start with the prefix and (b) are
  strictly longer than it — so once you've typed a complete word ("the"),
  that word drops off the row and only genuine continuations ("them",
  "then", "there"...) remain. Ranked left-to-right, or right-to-left per
  the Settings screen.
- Rare prefixes may have fewer than 6 (or zero) matching words — those keys
  are simply left blank rather than filled with irrelevant words. The
  regular QWERTY keys underneath are unaffected either way.
- Separately, `PredictionEngine.soleCompletion()` checks whether **exactly
  one** dictionary word matches the prefix at all (whether or not it's
  shown on the row above); if so, the space bar turns green and completes
  it on tap.

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
- `PredictionEngine.kt` — frequency-ranked word-completion logic (pure
  Kotlin, no Android dependencies — easy to unit test on its own).
- `KeyboardPanelView.kt` — the letters keyboard: 6 word-completion keys on
  top, QWERTY rows below, and the space bar.
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
