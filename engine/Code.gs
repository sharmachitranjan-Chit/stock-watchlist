/**
 * Stock Watchlist — price engine for the Android app
 * Google Sheets + GOOGLEFINANCE, answered as JSON.
 * A tool made by Chitranjan Sharma
 *
 * Every request needs ?key=<App key from the Settings tab>.
 *   ?action=data                         → indices + all watchlist rows
 *   ?action=history&ticker=NSE:TCS       → daily closes for ~1 year
 *   ?action=add&list=My list&ticker=NSE:TCS
 *   ?action=remove&list=My list&ticker=NSE:TCS
 *   ?action=move&list=My list&ticker=NSE:TCS&dir=-1   (−1 up, +1 down)
 *   ?action=deleteList&list=Banks
 */

const SHEET_ID = '17uiQK74TKib-epCr_FO6VBk7dwSmFf8xZotsMKrQ14c';
const WL = 'Watchlist';
const IDX = 'Indices';
const HIST = '_History';
const SETTINGS = 'Settings';
const TZ = 'Asia/Kolkata';
const FIELDS = ['name', 'price', 'change', 'changepct', 'closeyest', 'high', 'low',
                'high52', 'low52', 'volume', 'tradetime'];
const HEAD = ['List', 'Ticker'].concat(FIELDS);
const TRADETIME_COL = 2 + FIELDS.indexOf('tradetime') + 1;

/* ---------- Entry point ---------- */

function doGet(e) {
  const p = (e && e.parameter) || {};
  try {
    if (!p.key || p.key !== appKey_()) return json_({ ok: false, error: 'Wrong or missing app key' });
    let data;
    switch (p.action || 'data') {
      case 'data':       data = getData(); break;
      case 'history':    data = getHistory(p.ticker); break;
      case 'add':        data = addStock(p.list, p.ticker); break;
      case 'remove':     data = removeStock(p.list, p.ticker); break;
      case 'move':       data = moveStock(p.list, p.ticker, Number(p.dir) < 0 ? -1 : 1); break;
      case 'deleteList': data = deleteList(p.list); break;
      default: throw new Error('Unknown action: ' + p.action);
    }
    return json_({ ok: true, data: data });
  } catch (err) {
    return json_({ ok: false, error: String(err && err.message || err) });
  }
}

/** Run once from the editor so Google asks for permission. */
function setup() {
  const d = getData();
  Logger.log('Engine OK. ' + d.rows.length + ' stocks, ' + d.indices.length + ' indices.');
}

/* ---------- Actions ---------- */

function getData() {
  return { rows: read_(sheet_(WL)), indices: read_(sheet_(IDX)), at: new Date().toISOString() };
}

function addStock(list, ticker) {
  list = cleanList_(list);
  ticker = normalize_(ticker);
  return locked_(function () {
    const sh = sheet_(WL);
    if (findRow_(sh, list, ticker) > 0) throw new Error(ticker + ' is already in "' + list + '"');
    const row = appendRow_(sh, list, ticker);
    let ok = false;
    for (let k = 0; k < 14 && !ok; k++) {
      SpreadsheetApp.flush();
      const v = sh.getRange(row, 3, 1, 2).getValues()[0];
      ok = isReal_(v[0]) || isReal_(v[1]);
      if (!ok) Utilities.sleep(700);
    }
    if (!ok) {
      sh.deleteRow(row);
      throw new Error('Google Finance has no data for ' + ticker + '. Check the symbol (BSE needs the 6-digit scrip code).');
    }
    return getData();
  });
}

function removeStock(list, ticker) {
  return locked_(function () {
    const sh = sheet_(WL);
    const row = findRow_(sh, cleanList_(list), String(ticker || ''));
    if (row > 0) sh.deleteRow(row);
    return getData();
  });
}

function moveStock(list, ticker, dir) {
  list = cleanList_(list);
  return locked_(function () {
    const sh = sheet_(WL);
    const n = sh.getLastRow() - 1;
    if (n < 2) return getData();
    const v = sh.getRange(2, 1, n, 2).getValues();
    const rows = [];
    v.forEach(function (r, i) { if (r[0] === list) rows.push(i + 2); });
    const at = rows.findIndex(function (r) { return v[r - 2][1] === ticker; });
    const to = at + dir;
    if (at < 0 || to < 0 || to >= rows.length) return getData();
    const a = rows[at], b = rows[to];
    const ta = v[a - 2][1], tb = v[b - 2][1];
    sh.getRange(a, 2).setValue(tb);
    sh.getRange(b, 2).setValue(ta);
    SpreadsheetApp.flush();
    return getData();
  });
}

function deleteList(list) {
  list = cleanList_(list);
  return locked_(function () {
    const sh = sheet_(WL);
    const n = sh.getLastRow() - 1;
    if (n > 0) {
      const v = sh.getRange(2, 1, n, 1).getValues();
      for (let i = v.length - 1; i >= 0; i--) if (v[i][0] === list) sh.deleteRow(i + 2);
    }
    return getData();
  });
}

/** Daily closes for about the last year: [[ 'yyyy-MM-dd', close ], ...] */
function getHistory(ticker) {
  ticker = normalize_(ticker);
  return locked_(function () {
    const ss = ss_();
    let sh = ss.getSheetByName(HIST);
    if (!sh) { sh = ss.insertSheet(HIST); sh.hideSheet(); }
    sh.clear();
    sh.getRange('A1').setFormula('=GOOGLEFINANCE("' + ticker + '","close",TODAY()-400,TODAY(),"DAILY")');
    let out = [];
    for (let k = 0; k < 14; k++) {
      SpreadsheetApp.flush();
      const v = sh.getDataRange().getValues();
      out = v.filter(function (r) { return r[0] instanceof Date && typeof r[1] === 'number'; });
      if (out.length) break;
      Utilities.sleep(700);
    }
    return out.map(function (r) { return [Utilities.formatDate(r[0], TZ, 'yyyy-MM-dd'), r[1]]; });
  });
}

/* ---------- Helpers ---------- */

function ss_() { return SpreadsheetApp.openById(SHEET_ID); }

function appKey_() {
  const sh = ss_().getSheetByName(SETTINGS);
  return sh ? String(sh.getRange('B2').getValue()).trim() : '';
}

function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

function sheet_(name) {
  const ss = ss_();
  let sh = ss.getSheetByName(name);
  if (!sh) {
    sh = ss.insertSheet(name);
    sh.getRange(1, 1, 1, HEAD.length).setValues([HEAD]).setFontWeight('bold');
    sh.setFrozenRows(1);
    sh.getRange('B:B').setNumberFormat('@');
  }
  return sh;
}

function appendRow_(sh, list, ticker) {
  const row = sh.getLastRow() + 1;
  sh.getRange(row, 1, 1, 2).setValues([[list, ticker]]);
  const f = FIELDS.map(function (x) { return '=IFERROR(GOOGLEFINANCE($B' + row + ',"' + x + '"),"")'; });
  sh.getRange(row, 3, 1, FIELDS.length).setFormulas([f]);
  sh.getRange(row, TRADETIME_COL).setNumberFormat('dd-mmm-yyyy hh:mm');
  return row;
}

function read_(sh) {
  const n = sh.getLastRow() - 1;
  if (n < 1) return [];
  return sh.getRange(2, 1, n, HEAD.length).getValues()
    .filter(function (v) { return v[1]; })
    .map(function (v) {
      const o = { list: String(v[0]), ticker: String(v[1]) };
      FIELDS.forEach(function (f, i) {
        let x = v[i + 2];
        if (f === 'tradetime') x = toIso_(x);
        else if (f === 'name') x = isReal_(x) ? String(x) : null;
        else x = typeof x === 'number' ? x : null;
        o[f] = x;
      });
      return o;
    });
}

/** Trade time arrives as a Date, or as a plain serial number if the cell is unformatted. */
function toIso_(x) {
  if (x instanceof Date) return x.toISOString();
  if (typeof x === 'number' && x > 30000) {
    const istMs = (x - 25569) * 86400000;          // days since 1899-12-30, read as IST
    return new Date(istMs - 330 * 60000).toISOString();
  }
  return null;
}

function findRow_(sh, list, ticker) {
  const n = sh.getLastRow() - 1;
  if (n < 1) return -1;
  const v = sh.getRange(2, 1, n, 2).getValues();
  for (let i = 0; i < v.length; i++) if (v[i][0] === list && v[i][1] === ticker) return i + 2;
  return -1;
}

function normalize_(t) {
  t = String(t || '').trim().toUpperCase().replace(/\s+/g, '');
  if (!t) throw new Error('Enter a symbol');
  t = t.replace(/^BSE:/, 'BOM:').replace(/\.NS$/, '').replace(/\.BO$/, '');
  if (t.indexOf(':') === -1) t = (/^\d{6}$/.test(t) ? 'BOM:' : 'NSE:') + t;
  if (!/^[A-Z]+:[A-Z0-9_&.\-]+$/.test(t)) throw new Error('That does not look like a valid symbol');
  return t;
}

function cleanList_(l) { return (String(l || '').trim() || 'My list').slice(0, 30); }

function isReal_(x) { return x !== '' && x !== null && x !== 'Loading...' && x !== '#N/A'; }

function locked_(fn) {
  const lock = LockService.getScriptLock();
  lock.waitLock(25000);
  try { return fn(); } finally { lock.releaseLock(); }
}
