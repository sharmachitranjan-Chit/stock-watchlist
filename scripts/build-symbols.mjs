// Builds app/src/main/assets/symbols.json: every listed equity on NSE and BSE, one row per company.
// Row format: [company name, NSE symbol or "", BSE scrip code or "", ISIN]
// Run by the "Update stock list" GitHub workflow, which has open internet access.
import { writeFileSync, mkdirSync } from 'node:fs';

const UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36';

async function get(url, headers = {}) {
  const res = await fetch(url, { headers: { 'User-Agent': UA, Accept: '*/*', ...headers } });
  if (!res.ok) throw new Error(`${url} → HTTP ${res.status}`);
  return res.text();
}

function parseCsv(text) {
  const lines = text.replace(/\r/g, '').split('\n').filter(Boolean);
  const split = l => {
    const out = []; let cur = ''; let q = false;
    for (const ch of l) {
      if (ch === '"') q = !q;
      else if (ch === ',' && !q) { out.push(cur); cur = ''; }
      else cur += ch;
    }
    out.push(cur);
    return out.map(s => s.trim());
  };
  const head = split(lines[0]).map(h => h.toUpperCase());
  return lines.slice(1).map(l => Object.fromEntries(split(l).map((v, i) => [head[i], v])));
}

async function nse() {
  const urls = [
    'https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv',
    'https://archives.nseindia.com/content/equities/EQUITY_L.csv',
  ];
  for (const u of urls) {
    try {
      const rows = parseCsv(await get(u, { Referer: 'https://www.nseindia.com/' }));
      const keep = rows.filter(r => ['EQ', 'BE', 'BZ', 'SM', 'ST'].includes(r['SERIES']) && r['SYMBOL']);
      if (keep.length > 1000) return keep.map(r => ({ sym: r['SYMBOL'], name: r['NAME OF COMPANY'], isin: r['ISIN NUMBER'] }));
    } catch (e) { console.warn('NSE source failed:', e.message); }
  }
  throw new Error('Could not download the NSE list');
}

async function bse() {
  const url = 'https://api.bseindia.com/BseIndiaAPI/api/ListofScripData/w?Group=&Scripcode=&industry=&segment=Equity&status=Active';
  const text = await get(url, { Referer: 'https://www.bseindia.com/', Origin: 'https://www.bseindia.com' });
  const rows = JSON.parse(text);
  if (!Array.isArray(rows) || rows.length < 1000) throw new Error('BSE list looks incomplete');
  return rows
    .filter(r => r.SCRIP_CD && r.Status === 'Active')
    .map(r => ({ code: String(r.SCRIP_CD).trim(), name: String(r.Scrip_Name || r.Issuer_Name || '').trim(), isin: String(r.ISIN_NUMBER || '').trim() }));
}

const tidy = s => s.replace(/\s+/g, ' ').replace(/\s*-\s*\$$/, '').trim();

const [n, b] = await Promise.all([nse(), bse()]);
const byIsin = new Map();
for (const r of n) byIsin.set(r.isin || 'NSE:' + r.sym, { name: tidy(r.name), nse: r.sym, bse: '', isin: r.isin });
for (const r of b) {
  const key = r.isin || 'BSE:' + r.code;
  const hit = byIsin.get(key);
  if (hit) hit.bse = r.code;
  else byIsin.set(key, { name: tidy(r.name), nse: '', bse: r.code, isin: r.isin });
}
const out = [...byIsin.values()]
  .filter(r => r.name)
  .sort((a, b2) => a.name.localeCompare(b2.name))
  .map(r => [r.name, r.nse, r.bse, r.isin]);

mkdirSync('app/src/main/assets', { recursive: true });
writeFileSync('app/src/main/assets/symbols.json', JSON.stringify(out));
const both = out.filter(r => r[1] && r[2]).length;
console.log(`NSE ${n.length}, BSE ${b.length}, companies ${out.length}, on both ${both}`);
