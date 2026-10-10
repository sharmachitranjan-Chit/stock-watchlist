import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, useColorScheme } from 'react-native';
import { api, Config, loadCache, loadConfig, Quote, saveCache, saveConfig, Snapshot } from './api';
import { DEFAULT_CONFIG } from './defaults';
import { market } from './format';
import { GainLoss, makeTheme, PaletteId, Theme } from './theme';

export type SortMode = 'my' | 'gain' | 'loss' | 'name';
export type ViewMode = 'all' | 'nse' | 'bse' | 'compare';
export type Prefs = {
  scheme: 'system' | 'light' | 'dark';
  palette: PaletteId;
  gainLoss: GainLoss;
  interval: number; // seconds between refreshes during market hours
  sort: SortMode;
  view: ViewMode;
};

const DEFAULT_PREFS: Prefs = { scheme: 'system', palette: 'ocean', gainLoss: 'classic', interval: 60, sort: 'my', view: 'all' };
const PREFS_KEY = 'wl_prefs';
const LISTS_KEY = 'wl_extra_lists';
const ACTIVE_KEY = 'wl_active_list';

type Store = {
  theme: Theme;
  prefs: Prefs;
  setPrefs: (p: Partial<Prefs>) => void;
  cfg: Config | null;
  setCfg: (c: Config | null) => Promise<void>;
  data: Snapshot | null;
  loading: boolean;
  error: string;
  flash: Record<string, 'up' | 'down'>;
  lists: string[];
  activeList: string;
  setActiveList: (l: string) => void;
  createList: (name: string) => void;
  refresh: () => Promise<void>;
  add: (list: string, ticker: string) => Promise<void>;
  remove: (list: string, ticker: string) => Promise<void>;
  move: (list: string, ticker: string, dir: -1 | 1) => Promise<void>;
  deleteList: (list: string) => Promise<void>;
  toast: string;
  showToast: (m: string) => void;
};

const Ctx = createContext<Store | null>(null);

export function useStore() {
  const s = useContext(Ctx);
  if (!s) throw new Error('useStore outside provider');
  return s;
}
export const useTheme = () => useStore().theme;

const key = (q: Pick<Quote, 'list' | 'ticker'>) => q.list + '|' + q.ticker;

export function StoreProvider({ children }: { children: React.ReactNode }) {
  const system = useColorScheme();
  const [prefs, setPrefsState] = useState<Prefs>(DEFAULT_PREFS);
  const [cfg, setCfgState] = useState<Config | null>(null);
  const [data, setData] = useState<Snapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [flash, setFlash] = useState<Record<string, 'up' | 'down'>>({});
  const [extraLists, setExtraLists] = useState<string[]>([]);
  const [activeList, setActiveListState] = useState('My list');
  const [toast, setToast] = useState('');
  const dataRef = useRef<Snapshot | null>(null);
  const cfgRef = useRef<Config | null>(null);
  const busy = useRef(false);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const scheme = prefs.scheme === 'system' ? (system === 'dark' ? 'dark' : 'light') : prefs.scheme;
  const theme = useMemo(() => makeTheme(scheme, prefs.palette, prefs.gainLoss), [scheme, prefs.palette, prefs.gainLoss]);

  const showToast = useCallback((m: string) => {
    setToast(m);
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(''), 2600);
  }, []);

  const apply = useCallback((d: Snapshot) => {
    const old = dataRef.current;
    if (old) {
      const prev = new Map<string, number | null>();
      [...old.rows, ...old.indices].forEach(r => prev.set(key(r), r.price));
      const f: Record<string, 'up' | 'down'> = {};
      [...d.rows, ...d.indices].forEach(r => {
        const p = prev.get(key(r));
        if (p != null && r.price != null && p !== r.price) f[key(r)] = r.price > p ? 'up' : 'down';
      });
      if (Object.keys(f).length) { setFlash(f); setTimeout(() => setFlash({}), 1400); }
    }
    dataRef.current = d;
    setData(d);
    saveCache(d);
  }, []);

  const refresh = useCallback(async () => {
    const c = cfgRef.current;
    if (!c || busy.current) return;
    busy.current = true; setLoading(true);
    try { apply(await api.data(c)); setError(''); }
    catch (e: any) { setError(e?.message || String(e)); }
    finally { busy.current = false; setLoading(false); }
  }, [apply]);

  /* ---------- load saved state ---------- */
  useEffect(() => {
    (async () => {
      try {
        const [p, l, a] = await Promise.all([AsyncStorage.getItem(PREFS_KEY), AsyncStorage.getItem(LISTS_KEY), AsyncStorage.getItem(ACTIVE_KEY)]);
        if (p) setPrefsState({ ...DEFAULT_PREFS, ...JSON.parse(p) });
        if (l) setExtraLists(JSON.parse(l));
        if (a) setActiveListState(a);
      } catch { /* defaults are fine */ }
      const [c, cached] = await Promise.all([loadConfig(), loadCache()]);
      if (cached) { dataRef.current = cached; setData(cached); }
      cfgRef.current = c; setCfgState(c);
      if (c) refresh();
    })();
  }, [refresh]);

  /* ---------- auto refresh while the app is open ---------- */
  useEffect(() => {
    if (!cfg) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let active = AppState.currentState === 'active';
    const loop = () => {
      clearTimeout(timer);
      if (!active) return;
      const open = market(dataRef.current).k !== 'closed';
      timer = setTimeout(async () => { await refresh(); loop(); }, open ? prefs.interval * 1000 : 10 * 60000);
    };
    loop();
    const sub = AppState.addEventListener('change', st => {
      const now = st === 'active';
      if (now && !active) refresh();
      active = now;
      loop();
    });
    return () => { clearTimeout(timer); sub.remove(); };
  }, [cfg, prefs.interval, refresh]);

  const setPrefs = useCallback((p: Partial<Prefs>) => {
    setPrefsState(prev => {
      const next = { ...prev, ...p };
      AsyncStorage.setItem(PREFS_KEY, JSON.stringify(next)).catch(() => {});
      return next;
    });
  }, []);

  const setCfg = useCallback(async (c: Config | null) => {
    if (c) await saveConfig(c);
    else await AsyncStorage.removeItem('wl_config');
    const eff = c || DEFAULT_CONFIG;
    cfgRef.current = eff; setCfgState(eff);
    await refresh();
  }, [refresh]);

  const lists = useMemo(() => {
    const out: string[] = [];
    (data?.rows || []).forEach(r => { if (!out.includes(r.list)) out.push(r.list); });
    extraLists.forEach(l => { if (!out.includes(l)) out.push(l); });
    if (!out.length) out.push('My list');
    return out;
  }, [data, extraLists]);

  const setActiveList = useCallback((l: string) => {
    setActiveListState(l);
    AsyncStorage.setItem(ACTIVE_KEY, l).catch(() => {});
  }, []);

  useEffect(() => { if (!lists.includes(activeList)) setActiveList(lists[0]); }, [lists, activeList, setActiveList]);

  const saveExtra = (next: string[]) => { setExtraLists(next); AsyncStorage.setItem(LISTS_KEY, JSON.stringify(next)).catch(() => {}); };

  const createList = useCallback((name: string) => {
    const n = name.trim().slice(0, 30);
    if (!n) return;
    setExtraLists(prev => {
      const next = prev.includes(n) ? prev : [...prev, n];
      AsyncStorage.setItem(LISTS_KEY, JSON.stringify(next)).catch(() => {});
      return next;
    });
    setActiveList(n);
  }, [setActiveList]);

  const mutate = useCallback(async (p: Promise<Snapshot>) => {
    setLoading(true);
    try { apply(await p); setError(''); }
    finally { setLoading(false); }
  }, [apply]);

  const need = () => { if (!cfgRef.current) throw new Error('Price engine is not connected'); return cfgRef.current; };

  const value: Store = {
    theme, prefs, setPrefs, cfg, setCfg, data, loading, error, flash,
    lists, activeList, setActiveList, createList, refresh, toast, showToast,
    add: async (list, ticker) => { await mutate(api.add(need(), list, ticker)); saveExtra(extraLists.filter(l => l !== list)); },
    remove: (list, ticker) => mutate(api.remove(need(), list, ticker)),
    move: (list, ticker, dir) => mutate(api.move(need(), list, ticker, dir)),
    deleteList: async list => { saveExtra(extraLists.filter(l => l !== list)); await mutate(api.deleteList(need(), list)); },
  };

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export { key as quoteKey };
