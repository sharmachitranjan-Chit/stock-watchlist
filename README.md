# Watchlist

Personal NSE/BSE share watchlist for Android. Prices come from Google Finance through a Google Sheet ("Stock Watchlist") and a small Apps Script engine (`engine/Code.gs`).

- Push to `main` and the **Build APK** workflow produces `Watchlist-apk` under Actions → the latest run → Artifacts.
- The engine link and key are built in (`src/defaults.ts`), so the app connects on first launch. **Keep this repository private.**

A tool made by Chitranjan Sharma.
