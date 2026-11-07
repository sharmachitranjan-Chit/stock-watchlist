# Watchlist: progress and design notes

Native Android app (Kotlin, Jetpack Compose, Material 3) for NSE and BSE share watchlists, by Chitranjan Sharma.
Prices come from Google Finance through the "Stock Watchlist" Google Sheet and the Apps Script engine in `engine/Code.gs`.

## What is in the app

| Tab | What it does |
| --- | --- |
| **Watchlist** | Your lists as chips. Tap the selected chip (or long-press any chip) to **rename** or delete a list. All / NSE / BSE / side-by-side views, sorting, edit mode, and a one-line NSE/BSE summary that opens Insights. |
| **Insights** | Analysis of the selected list: today's combined move, **NSE vs BSE**, **sector-wise** movement, **history** (1W to 1Y) with a Combined / NSE / BSE chart, best and worst day, biggest fall from a peak, best and worst stocks, biggest movers. With holdings entered: value, day and total profit and loss. |
| **Markets** | Indices, market status, advance/decline, top gainers and losers across all lists. |
| **Search** | Every NSE and BSE company, matched by ISIN. |
| **Settings** | Theme (system / light / dark, pure black), colour palette (incl. wallpaper colours), gain/loss colours (colour-blind safe option), text size, list density, watchlist management (rename, delete), auto refresh (incl. off), opening screen, default Insights period, hide amounts, engine connection, clear saved history. |

## How the numbers are worked out

- **Today's move** of a group (list, exchange, sector) is the average of its stocks' day change in percent, one stock one vote.
  When *every* stock in the list has a holding (shares and average buy price, entered on a stock's page), it is weighted by money instead.
- **History** uses daily closes from the engine, fetched one stock at a time (the engine is a Google Sheet), at most once a day per stock, and saved on the phone (`files/history`). Today's live price is added as the last point.
  A group's path is the chain of daily moves of every stock that has a price on both days, so stocks that listed later join without a jump. Without holdings each stock starts with the same amount.
  Google has no BSE history for many small BSE stocks; their NSE prices are used (the screen says so).
- **Sectors** are NSE's official industry for about 750 companies (`assets/sectors.json`, refreshed weekly by `scripts/build-sectors.mjs`); "Financial Services" is split into Banks and Insurance by name. Anything else is guessed from words in the company name, else "Other".
- All of this is plain Kotlin in `data/Analytics.kt`, linear in stocks × days (200 stocks × 350 days ≈ 12 ms) and covered by unit tests (`app/src/test`). Heavy work runs on a background dispatcher and is recomputed only when prices, the list, the holdings or the period change.

## New listings and IPOs

Search does **not** read the Google Sheet. It reads the company list bundled in the app (`assets/symbols.json`, built from NSE and BSE by `scripts/build-symbols.mjs`).
- A GitHub workflow refreshes that file **every day** and, when it changed, starts the APK build (before, a push by the workflow did not start a build, so new lists never reached the phone).
- The app also checks **both exchanges directly** (`Companies.refreshLive`, at most every 6 hours, on start-up and from Settings → Data → Company list): NSE's list of listed companies (`EQUITY_L.csv`) and BSE's latest daily bhavcopy (every share that traded, with scrip code and ISIN; BSE's own company-list API blocks non-browser clients). New companies are merged by ISIN, so a new IPO shows both its NSE and BSE buttons without a new APK. The BSE file appears each trading evening, so a BSE code can lag the listing by a few hours.
- Adding a stock goes through the sheet's `GOOGLEFINANCE`, which has prices for newly listed shares from their first trading day.

## Renaming a list

Renaming works inside the app and needs **no change to the Google Sheet script**: the app keeps the new name on the phone and translates it back to the sheet's own name for every add, remove, move and delete.
If you ever redeploy `engine/Code.gs` (it has a `renameList` action), the next rename also renames the list in the sheet itself.

## Ideas not done yet

- A batch history action in the engine, so Insights loads in one call instead of one per stock.
- Alerts and notifications for price targets.
- Import/export of lists and holdings.
- Index comparison line (Nifty/Sensex) on the performance chart.
