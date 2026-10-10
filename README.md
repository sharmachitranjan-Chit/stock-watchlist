# Watchlist

Personal NSE/BSE share watchlist for Android. Prices come from Google Finance through a Google Sheet ("Stock Watchlist") and a small Apps Script engine (`engine/Code.gs`).

- Push to `main` and the **Build APK** workflow produces `Watchlist-apk` under Actions → the latest run → Artifacts.
- First launch asks for the engine link (ends in `/exec`) and the app key (Settings tab, cell B2, of the sheet).

A tool made by Chitranjan Sharma.
