# Hand-off: engine script and starter prompts

## 1. The engine script (`engine/Code.gs`)

This is the Google Apps Script behind the Google Sheet. The version in this repository has **one new action, `renameList`**
(`?action=renameList&list=Old&to=New`), which renames a list in the sheet (it changes column A for every stock of that list, and refuses a name that already exists).
Everything else in the file is unchanged.

**Does updating it affect the Android app? No.**
- The app works with the old script and the new script. If the script does not know `renameList` it answers "Unknown action"; the app then keeps the new list name on the phone only and maps it back to the sheet's name for every call.
- If the new script is deployed, the next rename in the app also renames the list in the sheet.
- No other action changed, so nothing else in the app is affected. No new app build is needed after deploying the script.

### How to deploy it (on a laptop)
1. Open the Google Sheet "Stock Watchlist" → Extensions → Apps Script.
2. Replace the whole editor content with `engine/Code.gs` from this repository (raw file: `https://raw.githubusercontent.com/sharmachitranjan-Chit/stock-watchlist/<branch>/engine/Code.gs`; the repo is private, so open it from the GitHub page → Raw → select all → copy).
3. Deploy → Manage deployments → pencil (Edit) → Version: **New version** → Deploy. The `/exec` link stays the same, so the app needs no change.
4. Quick check in a browser: `<exec link>?action=data&key=<key>` still returns prices.

## 2. What was built in this branch (`claude/portfolio-analytics-ui-3thzwy`, PR #1)
Insights tab (today's combined move, NSE vs BSE, sectors from `assets/sectors.json`, history chart 1W–1Y, drawdown, best/worst), list rename (tap selected chip / long-press / Settings), optional holdings (shares + average price) for value and P&L, hide-amounts switch, pure-black theme, text size, density, auto-refresh off, opening screen, saved-history cache. Details: `PROGRESS.md`. Tests: `app/src/test/.../AnalyticsTest.kt`.

## 3. Starter prompts to paste at the start of a Claude chat/session

**Continue work on this app**
```
Repo: sharmachitranjan-Chit/stock-watchlist (private), Android app "Watchlist".
Before doing anything read CLAUDE.md, PROGRESS.md and docs/HANDOFF.md in the repo, and check git log origin/main.
I only have an Android phone, so keep steps short, no code pasting by me, and publish the APK through the build-apk GitHub workflow (Releases page).
Today's task: <describe the task here>
```

**Only the Google Apps Script is changing (I am on a laptop)**
```
Repo: sharmachitranjan-Chit/stock-watchlist. Read CLAUDE.md and docs/HANDOFF.md first.
I am changing engine/Code.gs (the Google Apps Script for my Google Sheet). Keep every existing action working exactly as before, only add new ones, and tell me whether the Android app (app/.../data/Engine.kt) needs any change. If it does, make the change so the app still works with the old script, and tell me before building a new APK.
Change wanted: <describe it here>
```

**Start a brand-new app from this one**
```
Repo: sharmachitranjan-Chit/stock-watchlist. Read CLAUDE.md, PROGRESS.md and docs/HANDOFF.md.
I want a new Android app based on this one, in a new repository/folder. Reuse the engine contract (engine/Code.gs actions) and data/Analytics.kt. New app goals: <describe here>
```
