# Project context for Claude (read this first)

**Watchlist**: personal NSE/BSE share watchlist, native Android app (Kotlin, Jetpack Compose, Material 3) by Chitranjan Sharma.
The owner works **only from an Android phone** (no laptop most of the time): keep any manual step short, avoid anything that needs typing, selecting or pasting code on the phone, and prefer things that GitHub Actions can build and publish.

Read, in this order: `PROGRESS.md` (features and how numbers are worked out) → `docs/HANDOFF.md` (engine script, what changed, starter prompts) → `README.md`.

## How the pieces fit
- **Prices and lists live in a Google Sheet** ("Stock Watchlist"). `engine/Code.gs` is the Google Apps Script web app on top of it. The app talks to it with `?action=…&key=…` (see `app/.../data/Engine.kt`). Engine URL and key are built in (`data/Settings.kt`). The repository must stay private.
- **App**: `app/src/main/java/com/chitranjan/watchlist/` with `data/` (engine client, state, analytics, history cache, sectors), `ui/` (screens), `util/`.
- **Releases**: pushing to `main` builds and publishes the APK (`.github/workflows/build-apk.yml`). It can also be run by hand on any branch (workflow_dispatch). Install from the Releases page in the phone's browser; downloading the APK through a chat app makes it open as a zip.
- **No Android SDK in the sandbox by default.** The sandbox can fetch one (`dl.google.com`) and use the Google mirror of Maven Central (`maven-central.storage-download.googleapis.com/maven2`) because `repo.maven.apache.org` rate-limits. Gradle 9.6 is needed (`services.gradle.org`). Unit tests: `gradle :app:testDebugUnitTest`.

## Rules of thumb
- `engine/Code.gs` changes are **additive**: the app must keep working with the older script already deployed (see `AppState.renameList`, which falls back when the engine answers "Unknown action"). Never make the app require a new script version.
- Heavy maths belongs in `data/Analytics.kt` (plain Kotlin, unit-tested in `app/src/test`). Keep UI recomposition cheap: memoise with `remember`, do work off the main thread.
- Check `git log origin/main` before starting; other sessions push to `main`.
