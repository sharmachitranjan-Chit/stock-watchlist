import AsyncStorage from '@react-native-async-storage/async-storage';

export type Quote = {
  list: string;
  ticker: string;
  name: string | null;
  price: number | null;
  change: number | null;
  changepct: number | null;
  closeyest: number | null;
  high: number | null;
  low: number | null;
  high52: number | null;
  low52: number | null;
  volume: number | null;
  tradetime: string | null;
};

export type Snapshot = { rows: Quote[]; indices: Quote[]; at: string };
export type History = [string, number][];
export type Config = { url: string; key: string };

const CONFIG_KEY = 'wl_config';
const CACHE_KEY = 'wl_cache';

export async function loadConfig(): Promise<Config | null> {
  try {
    const raw = await AsyncStorage.getItem(CONFIG_KEY);
    const c = raw ? JSON.parse(raw) : null;
    return c && c.url && c.key ? c : null;
  } catch {
    return null;
  }
}

export async function saveConfig(c: Config) {
  await AsyncStorage.setItem(CONFIG_KEY, JSON.stringify({ url: c.url.trim(), key: c.key.trim() }));
}

export async function loadCache(): Promise<Snapshot | null> {
  try {
    const raw = await AsyncStorage.getItem(CACHE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export async function saveCache(s: Snapshot) {
  try { await AsyncStorage.setItem(CACHE_KEY, JSON.stringify(s)); } catch { /* not critical */ }
}

async function call<T>(cfg: Config, action: string, params: Record<string, string | number> = {}): Promise<T> {
  const q = new URLSearchParams({ action, key: cfg.key });
  Object.entries(params).forEach(([k, v]) => q.append(k, String(v)));
  const sep = cfg.url.includes('?') ? '&' : '?';
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 45000);
  let res: Response;
  try {
    res = await fetch(cfg.url + sep + q.toString(), { signal: ctrl.signal, headers: { Accept: 'application/json' } });
  } catch (e: any) {
    throw new Error(e?.name === 'AbortError' ? 'The price engine took too long to answer.' : 'No internet connection, or the engine link is wrong.');
  } finally {
    clearTimeout(timer);
  }
  const text = await res.text();
  let body: any;
  try {
    body = JSON.parse(text);
  } catch {
    throw new Error('The engine link did not return prices. Check that it ends in /exec and was deployed for "Anyone".');
  }
  if (!body.ok) throw new Error(body.error || 'Something went wrong in the engine.');
  return body.data as T;
}

export const api = {
  data: (c: Config) => call<Snapshot>(c, 'data'),
  history: (c: Config, ticker: string) => call<History>(c, 'history', { ticker }),
  add: (c: Config, list: string, ticker: string) => call<Snapshot>(c, 'add', { list, ticker }),
  remove: (c: Config, list: string, ticker: string) => call<Snapshot>(c, 'remove', { list, ticker }),
  move: (c: Config, list: string, ticker: string, dir: -1 | 1) => call<Snapshot>(c, 'move', { list, ticker, dir }),
  deleteList: (c: Config, list: string) => call<Snapshot>(c, 'deleteList', { list }),
};
