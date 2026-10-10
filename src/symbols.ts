/**
 * Every listed equity on NSE and BSE, refreshed weekly by the "Update stock list" workflow.
 * Row: [company name, NSE symbol, BSE scrip code, ISIN]
 */
import raw from './data/symbols.json';

export type Company = { name: string; short: string; nse: string; bse: string; isin: string };

const ROWS = raw as unknown as [string, string, string, string][];

/** "Reliance Industries Limited" → "Reliance Industries" */
export function shortName(name: string) {
  return name
    .replace(/\s+(Limited|Ltd\.?|LIMITED|LTD\.?)$/i, '')
    .replace(/\s+/g, ' ')
    .trim();
}

export const COMPANIES: Company[] = ROWS.map(r => ({ name: r[0], short: shortName(r[0]), nse: r[1], bse: r[2], isin: r[3] }));

const byNse = new Map<string, Company>();
const byBse = new Map<string, Company>();
COMPANIES.forEach(c => { if (c.nse) byNse.set(c.nse, c); if (c.bse) byBse.set(c.bse, c); });

const searchText = COMPANIES.map(c => ({ c, name: c.name.toUpperCase(), words: c.name.toUpperCase().split(/[\s.&()-]+/) }));

/** Finds the company behind a Google ticker such as NSE:RELIANCE or BOM:500325. */
export function companyFor(ticker: string): Company | undefined {
  const [ex, code] = ticker.split(':');
  if (ex === 'NSE') return byNse.get(code);
  if (ex === 'BOM') return byBse.get(code);
  return undefined;
}

/** What to show as the main label: NSE symbol when there is one, else the short company name. */
export function labelFor(ticker: string, fallbackName?: string | null) {
  const c = companyFor(ticker);
  const [ex, code] = ticker.split(':');
  if (c?.nse) return c.nse;
  if (c) return c.short.toUpperCase().slice(0, 18);
  if (ex === 'NSE') return code;
  return (fallbackName ? shortName(fallbackName) : code).toUpperCase().slice(0, 18);
}

export function search(query: string, limit = 40): Company[] {
  const q = query.trim().toUpperCase();
  if (q.length < 1) return [];
  const scored: { c: Company; s: number }[] = [];
  for (const { c, name, words } of searchText) {
    let s = 0;
    if (c.nse === q) s = 100;
    else if (c.bse === q) s = 95;
    else if (c.nse.startsWith(q)) s = 80 - Math.min(c.nse.length - q.length, 10);
    else if (q.length >= 3 && c.bse.startsWith(q)) s = 70;
    else if (name.startsWith(q)) s = 65;
    else if (words.some(w => w.startsWith(q))) s = 55;
    else if (q.length >= 3 && name.includes(q)) s = 35;
    if (s) {
      if (c.nse && c.bse) s += 3; // listed on both: usually the better-known company
      scored.push({ c, s });
    }
  }
  scored.sort((a, b) => b.s - a.s || a.c.name.length - b.c.name.length);
  return scored.slice(0, limit).map(x => x.c);
}

const POPULAR_NSE = ['RELIANCE', 'HDFCBANK', 'TCS', 'INFY', 'ICICIBANK', 'SBIN', 'BANKBARODA', 'BHARTIARTL',
  'ITC', 'LT', 'HINDUNILVR', 'KOTAKBANK', 'AXISBANK', 'BAJFINANCE', 'MARUTI', 'SUNPHARMA', 'TITAN', 'TATASTEEL',
  'NTPC', 'ONGC', 'WIPRO', 'HCLTECH', 'COALINDIA', 'PNB'];

export const POPULAR: Company[] = POPULAR_NSE.map(s => byNse.get(s)).filter(Boolean) as Company[];
