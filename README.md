# Watchlist

Personal NSE/BSE share watchlist for Android, by Chitranjan Sharma.

- **Prices:** Google Finance, through the "Stock Watchlist" Google Sheet and the Apps Script engine in `engine/Code.gs`.
- **Company search:** `src/data/symbols.json`, every NSE and BSE equity matched by ISIN. The **Update stock list** workflow refreshes it every Monday.
- **Releases:** every push to `main` builds the APK and publishes it under **Releases**. Download the newest APK there and install it over the old one.
- The engine link and key are built in (`src/defaults.ts`). **Keep this repository private.**

Screens: Watchlist, Insights (NSE vs BSE, sectors, history), Markets, Search, Settings and a stock detail page. See [PROGRESS.md](PROGRESS.md) for what each does and how the numbers are worked out.
