import type { Quote, Snapshot } from './api';

/** Indian grouping (1,23,456.50) without relying on the phone's locale support. */
export function num(n: number | null | undefined, d = 2): string {
  if (n == null || isNaN(n)) return '—';
  const neg = n < 0;
  const [int, frac] = Math.abs(n).toFixed(d).split('.');
  let out = int;
  if (int.length > 3) {
    const last3 = int.slice(-3);
    const rest = int.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ',');
    out = rest + ',' + last3;
  }
  return (neg ? '-' : '') + out + (frac ? '.' + frac : '');
}

export function vol(n: number | null): string {
  if (n == null || n === 0) return '—';
  if (n >= 1e7) return num(n / 1e7) + ' Cr';
  if (n >= 1e5) return num(n / 1e5) + ' L';
  return num(n, 0);
}

export function signPct(p: number | null): string {
  if (p == null) return '—';
  return (p > 0 ? '+' : '') + num(p) + '%';
}

export function arrow(c: number | null): string {
  return c == null ? '' : c > 0 ? '▲' : c < 0 ? '▼' : '•';
}

export type Dir = 'up' | 'down' | 'flat';
export function dirOf(c: number | null): Dir {
  return c == null || c === 0 ? 'flat' : c > 0 ? 'up' : 'down';
}

export function parts(t: string) {
  const [ex, sym] = t.split(':');
  return { ex: ex === 'BOM' || ex === 'INDEXBOM' ? 'BSE' : 'NSE', sym: sym || t };
}

export const INDEX_NAMES: Record<string, string> = {
  'INDEXNSE:NIFTY_50': 'Nifty 50',
  'INDEXBOM:SENSEX': 'Sensex',
  'INDEXNSE:NIFTY_BANK': 'Bank Nifty',
};

/* ---------- India Standard Time ---------- */

const IST_OFFSET = 330 * 60000;
const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

export function ist(d: Date = new Date()) {
  const t = new Date(d.getTime() + IST_OFFSET);
  const pad = (x: number) => String(x).padStart(2, '0');
  return {
    wd: DAYS[t.getUTCDay()],
    date: `${t.getUTCFullYear()}-${pad(t.getUTCMonth() + 1)}-${pad(t.getUTCDate())}`,
    mins: t.getUTCHours() * 60 + t.getUTCMinutes(),
    hm: `${pad(t.getUTCHours())}:${pad(t.getUTCMinutes())}`,
    dayLabel: `${pad(t.getUTCDate())} ${['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][t.getUTCMonth()]}`,
  };
}

export function latestTrade(s: Snapshot | null): string | null {
  if (!s) return null;
  let best: string | null = null;
  [...s.indices, ...s.rows].forEach(r => { if (r.tradetime && (!best || r.tradetime > best)) best = r.tradetime; });
  return best;
}

export type Market = { k: 'live' | 'pre' | 'closed'; label: string };

export function market(s: Snapshot | null): Market {
  const n = ist();
  if (n.wd === 'Sat' || n.wd === 'Sun') return { k: 'closed', label: 'Closed · weekend' };
  const last = latestTrade(s);
  if (last && n.mins >= 9 * 60 + 30 && ist(new Date(last)).date !== n.date) return { k: 'closed', label: 'Closed · holiday' };
  if (n.mins >= 540 && n.mins < 555) return { k: 'pre', label: 'Pre-open' };
  if (n.mins >= 555 && n.mins < 930) return { k: 'live', label: 'Live' };
  return { k: 'closed', label: n.mins < 540 ? 'Opens 9:15 AM' : 'Closed' };
}

/** One line explaining when trading happens next, for the Markets screen. */
export function sessionNote(s: Snapshot | null): string {
  const m = market(s);
  const n = ist();
  if (m.k === 'live') {
    const left = 930 - n.mins;
    return `Normal trading until 3:30 PM · ${Math.floor(left / 60)}h ${left % 60}m left`;
  }
  if (m.k === 'pre') return 'Pre-open session · normal trading starts at 9:15 AM';
  if (n.wd === 'Sat') return 'Opens Monday at 9:15 AM (pre-open from 9:00 AM)';
  if (n.wd === 'Sun') return 'Opens tomorrow at 9:15 AM (pre-open from 9:00 AM)';
  if (m.label.includes('holiday')) return 'Market holiday today · prices are from the last trading day';
  if (n.mins < 540) return 'Pre-open from 9:00 AM · normal trading 9:15 AM to 3:30 PM';
  return n.wd === 'Fri' ? 'Opens Monday at 9:15 AM' : 'Opens tomorrow at 9:15 AM';
}

export function lastTradeLabel(q: Quote | null | undefined): string {
  if (!q?.tradetime) return '—';
  const t = ist(new Date(q.tradetime));
  return t.date === ist().date ? `${t.hm} IST` : `${t.dayLabel}, ${t.hm}`;
}
