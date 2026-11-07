// Builds app/src/main/assets/sectors.json: the official NSE industry of every company in the Nifty 500,
// Nifty Total Market and Nifty Microcap 250 indices, keyed by ISIN.
// Format: {"s": ["Capital Goods", ...], "m": {"INE117A01022": 0, ...}}  (m maps ISIN → index into s)
// Run by the "Update stock list" GitHub workflow. If a download fails the old file is kept.
import { writeFileSync, readFileSync, existsSync, mkdirSync } from 'node:fs';

const UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36';
const LISTS = ['ind_nifty500list', 'ind_niftytotalmarket_list', 'ind_niftymicrocap250_list', 'ind_niftysmallcap250list'];
const HOSTS = ['https://nsearchives.nseindia.com/content/indices/', 'https://www.niftyindices.com/IndexConstituent/'];
const OUT = 'app/src/main/assets/sectors.json';

async function download(name) {
  for (const host of HOSTS) {
    try {
      const res = await fetch(`${host}${name}.csv`, { headers: { 'User-Agent': UA, Accept: '*/*' } });
      if (res.ok) return await res.text();
    } catch (e) { /* try the next host */ }
  }
  return '';
}

const sectors = [];
const map = {};
for (const name of LISTS) {
  const lines = (await download(name)).replace(/\r/g, '').split('\n').filter(Boolean);
  if (lines.length < 2) { console.warn(`${name}: nothing downloaded`); continue; }
  const head = lines[0].split(',').map(h => h.trim().toUpperCase());
  const iIndustry = head.indexOf('INDUSTRY'), iIsin = head.findIndex(h => h.startsWith('ISIN'));
  if (iIndustry < 0 || iIsin < 0) { console.warn(`${name}: unexpected columns`); continue; }
  for (const l of lines.slice(1)) {
    const c = l.split(',').map(s => s.trim());
    const industry = c[iIndustry], isin = c[iIsin];
    if (!industry || !isin || map[isin] !== undefined) continue;
    let k = sectors.indexOf(industry);
    if (k < 0) { k = sectors.length; sectors.push(industry); }
    map[isin] = k;
  }
}

if (Object.keys(map).length < 400) {
  console.warn('Too few companies downloaded; keeping the existing sectors.json');
  if (!existsSync(OUT)) process.exit(1);
} else {
  mkdirSync('app/src/main/assets', { recursive: true });
  writeFileSync(OUT, JSON.stringify({ s: sectors, m: map }));
  console.log(`Wrote ${Object.keys(map).length} companies in ${sectors.length} sectors`);
}
