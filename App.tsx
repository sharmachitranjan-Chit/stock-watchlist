import { StatusBar } from 'expo-status-bar';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator, Alert, AppState, FlatList, Pressable, RefreshControl, ScrollView,
  StyleSheet, Text, TextInput, View, useColorScheme,
} from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { api, Config, loadCache, loadConfig, Quote, saveCache, saveConfig, Snapshot } from './src/api';
import { INDEX_NAMES, ist, latestTrade, market, num, parts } from './src/format';
import { serif, useTheme } from './src/theme';
import AddSheet from './src/components/AddSheet';
import Change from './src/components/Change';
import DetailSheet from './src/components/DetailSheet';
import SettingsSheet from './src/components/SettingsSheet';
import Sheet from './src/components/Sheet';

type Sort = 'my' | 'gain' | 'loss' | 'name';
const SORTS: [Sort, string][] = [['my', 'My order'], ['gain', 'Gainers'], ['loss', 'Losers'], ['name', 'A–Z']];

export default function App() {
  return (
    <SafeAreaProvider>
      <Main />
    </SafeAreaProvider>
  );
}

function Main() {
  const t = useTheme();
  const scheme = useColorScheme();
  const inset = useSafeAreaInsets();

  const [cfg, setCfg] = useState<Config | null>(null);
  const [ready, setReady] = useState(false);
  const [data, setData] = useState<Snapshot | null>(null);
  const [list, setList] = useState<string>('My list');
  const [extraLists, setExtraLists] = useState<string[]>([]);
  const [sort, setSort] = useState<Sort>('my');
  const [edit, setEdit] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [flash, setFlash] = useState<Record<string, 'up' | 'down'>>({});
  const [, tick] = useState(0);

  const [showAdd, setShowAdd] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [showNewList, setShowNewList] = useState(false);
  const [detail, setDetail] = useState<{ q: Quote; index: boolean } | null>(null);

  const dataRef = useRef<Snapshot | null>(null);
  const busyRef = useRef(false);

  /* ---------- Data ---------- */

  const apply = useCallback((d: Snapshot) => {
    const old = dataRef.current;
    if (old) {
      const prev: Record<string, number | null> = {};
      [...old.rows, ...old.indices].forEach(r => { prev[r.list + '|' + r.ticker] = r.price; });
      const f: Record<string, 'up' | 'down'> = {};
      [...d.rows, ...d.indices].forEach(r => {
        const p = prev[r.list + '|' + r.ticker];
        if (p != null && r.price != null && p !== r.price) f[r.list + '|' + r.ticker] = r.price > p ? 'up' : 'down';
      });
      if (Object.keys(f).length) { setFlash(f); setTimeout(() => setFlash({}), 1200); }
    }
    dataRef.current = d;
    setData(d);
    saveCache(d);
  }, []);

  const refresh = useCallback(async (c: Config | null = cfg) => {
    if (!c || busyRef.current) return;
    busyRef.current = true; setLoading(true);
    try {
      apply(await api.data(c));
      setError('');
    } catch (e: any) {
      setError(e?.message || String(e));
    } finally {
      busyRef.current = false; setLoading(false);
    }
  }, [cfg, apply]);

  async function mutate(p: Promise<Snapshot>) {
    setLoading(true);
    try { apply(await p); setError(''); }
    catch (e: any) { Alert.alert('Could not update', e?.message || String(e)); }
    finally { setLoading(false); }
  }

  /* ---------- Start-up: settings + last prices from the phone ---------- */

  useEffect(() => {
    (async () => {
      const [c, cached] = await Promise.all([loadConfig(), loadCache()]);
      if (cached) { dataRef.current = cached; setData(cached); }
      setCfg(c);
      setReady(true);
      if (c) refresh(c); else setShowSettings(true);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* ---------- Auto refresh: every minute in market hours, only while the app is open ---------- */

  useEffect(() => {
    if (!cfg) return;
    let timer: ReturnType<typeof setTimeout>;
    let active = AppState.currentState === 'active';
    const loop = () => {
      clearTimeout(timer);
      if (!active) return;
      const m = market(dataRef.current).k;
      timer = setTimeout(async () => { await refresh(cfg); tick(x => x + 1); loop(); }, m === 'closed' ? 10 * 60000 : 60000);
    };
    loop();
    const sub = AppState.addEventListener('change', st => {
      const now = st === 'active';
      if (now && !active) { refresh(cfg); }
      active = now;
      loop();
    });
    return () => { clearTimeout(timer); sub.remove(); };
  }, [cfg, refresh]);

  /* ---------- Derived ---------- */

  const lists = useMemo(() => {
    const out: string[] = [];
    (data?.rows || []).forEach(r => { if (!out.includes(r.list)) out.push(r.list); });
    extraLists.forEach(l => { if (!out.includes(l)) out.push(l); });
    if (!out.length) out.push('My list');
    return out;
  }, [data, extraLists]);

  useEffect(() => { if (!lists.includes(list)) setList(lists[0]); }, [lists, list]);

  const rows = useMemo(() => {
    const r = (data?.rows || []).filter(x => x.list === list);
    if (sort === 'gain') r.sort((a, b) => (b.changepct ?? -1e9) - (a.changepct ?? -1e9));
    if (sort === 'loss') r.sort((a, b) => (a.changepct ?? 1e9) - (b.changepct ?? 1e9));
    if (sort === 'name') r.sort((a, b) => parts(a.ticker).sym.localeCompare(parts(b.ticker).sym));
    return r;
  }, [data, list, sort]);

  const m = market(data);
  const lt = latestTrade(data);

  /* ---------- Actions ---------- */

  function confirmRemove(q: Quote) {
    Alert.alert('Remove stock', `Remove ${parts(q.ticker).sym} from “${q.list}”?`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Remove', style: 'destructive', onPress: () => { setDetail(null); cfg && mutate(api.remove(cfg, q.list, q.ticker)); } },
    ]);
  }

  function confirmDeleteList() {
    Alert.alert('Delete list', `Delete “${list}” and all its stocks?`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => {
        setExtraLists(x => x.filter(l => l !== list));
        cfg && mutate(api.deleteList(cfg, list));
        setEdit(false);
      } },
    ]);
  }

  async function add(ticker: string) {
    if (!cfg) throw new Error('Connect the price engine first');
    const d = await api.add(cfg, list, ticker);
    apply(d);
    setExtraLists(x => x.filter(l => l !== list));
    setShowAdd(false);
  }

  /* ---------- UI ---------- */

  const pillColor = m.k === 'live' ? t.up : m.k === 'pre' ? t.amber : t.muted;

  const header = (
    <View>
      <View style={s.top}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, flexShrink: 1 }}>
          <Text style={[s.h1, { color: t.moss }]}>Watchlist</Text>
          <View style={[s.pill, { backgroundColor: t.card, borderColor: t.line }]}>
            <View style={[s.dot, { backgroundColor: pillColor }]} />
            <Text style={{ color: pillColor, fontSize: 12 }}>{m.label}</Text>
          </View>
        </View>
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <IconBtn label="⚙" onPress={() => setShowSettings(true)} />
          <IconBtn label={loading ? '' : '⟳'} busy={loading} onPress={() => refresh()} />
        </View>
      </View>
      <Text style={{ color: t.muted, fontSize: 12, marginTop: 6, marginBottom: 12 }}>
        {data ? `Checked ${ist(new Date(data.at)).hm}${lt ? ` · last trade ${ist(new Date(lt)).dayLabel} ${ist(new Date(lt)).hm} IST` : ''}` : cfg ? 'Loading prices…' : 'Not connected yet'}
      </Text>
      {!!error && (
        <Pressable onPress={() => refresh()} style={[s.err, { backgroundColor: t.downBg }]}>
          <Text style={{ color: t.down, fontSize: 13 }}>{error}  Tap to retry.</Text>
        </Pressable>
      )}

      <View style={s.indices}>
        {(data?.indices || []).filter(q => q.price != null).map(q => (
          <Pressable key={q.ticker} onPress={() => setDetail({ q, index: true })}
            style={[s.idx, { backgroundColor: flash['Indices|' + q.ticker] ? (flash['Indices|' + q.ticker] === 'up' ? t.upBg : t.downBg) : t.card, borderColor: t.line }]}>
            <Text style={{ color: t.muted, fontSize: 12 }} numberOfLines={1}>{INDEX_NAMES[q.ticker] || q.name}</Text>
            <Text style={{ color: t.ink, fontSize: 15, fontWeight: '700', marginVertical: 2, fontVariant: ['tabular-nums'] }} numberOfLines={1} adjustsFontSizeToFit>{num(q.price)}</Text>
            <Change change={q.change} pct={q.changepct} pctOnly style={{ fontSize: 12.5 }} />
          </Pressable>
        ))}
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
        {lists.map(l => (
          <Pressable key={l} onPress={() => setList(l)} style={[s.tab, { backgroundColor: l === list ? t.moss : t.card, borderColor: l === list ? t.moss : t.line }]}>
            <Text style={{ color: l === list ? t.onMoss : t.ink, fontSize: 14 }}>{l}</Text>
          </Pressable>
        ))}
        <Pressable onPress={() => setShowNewList(true)} style={[s.tab, { backgroundColor: t.card, borderColor: t.line }]}>
          <Text style={{ color: t.ink, fontSize: 14 }}>+ List</Text>
        </Pressable>
      </ScrollView>

      <View style={s.toolbar}>
        <View style={{ flexDirection: 'row', gap: 6 }}>
          {SORTS.map(([k, label]) => (
            <Pressable key={k} onPress={() => setSort(k)} style={[s.chip, { borderColor: sort === k ? t.moss : t.line, backgroundColor: sort === k ? t.card : 'transparent' }]}>
              <Text style={{ color: sort === k ? t.moss : t.muted, fontSize: 12.5, fontWeight: sort === k ? '700' : '400' }}>{label}</Text>
            </Pressable>
          ))}
        </View>
        <Pressable onPress={() => setEdit(e => !e)} hitSlop={10}>
          <Text style={{ color: t.moss, fontWeight: '700', fontSize: 14 }}>{edit ? 'Done' : 'Edit'}</Text>
        </Pressable>
      </View>
    </View>
  );

  const renderRow = ({ item: q, index }: { item: Quote; index: number }) => {
    const p = parts(q.ticker);
    const f = flash[q.list + '|' + q.ticker];
    const first = index === 0, last = index === rows.length - 1;
    return (
      <Pressable
        disabled={edit}
        onPress={() => setDetail({ q, index: false })}
        style={({ pressed }) => [s.row, {
          backgroundColor: pressed ? t.line : t.card, borderColor: t.line,
          borderTopLeftRadius: first ? 16 : 0, borderTopRightRadius: first ? 16 : 0,
          borderBottomLeftRadius: last ? 16 : 0, borderBottomRightRadius: last ? 16 : 0,
          borderTopWidth: first ? 1 : 0,
        }]}>
        <View style={{ flexShrink: 1, paddingRight: 10 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
            <Text style={{ color: t.ink, fontWeight: '700', fontSize: 15 }}>{p.sym}</Text>
            <Text style={[s.badge, { backgroundColor: t.line, color: t.muted }]}>{p.ex}</Text>
          </View>
          <Text style={{ color: t.muted, fontSize: 12, marginTop: 2 }} numberOfLines={1}>{q.name || ' '}</Text>
        </View>
        {edit ? (
          <View style={{ flexDirection: 'row', gap: 6 }}>
            <SmallBtn label="↑" onPress={() => cfg && mutate(api.move(cfg, q.list, q.ticker, -1))} />
            <SmallBtn label="↓" onPress={() => cfg && mutate(api.move(cfg, q.list, q.ticker, 1))} />
            <SmallBtn label="✕" danger onPress={() => confirmRemove(q)} />
          </View>
        ) : (
          <View style={{ alignItems: 'flex-end' }}>
            <Text style={[s.px, { color: t.ink, backgroundColor: f ? (f === 'up' ? t.upBg : t.downBg) : 'transparent' }]}>{num(q.price)}</Text>
            <Change change={q.change} pct={q.changepct} style={{ fontSize: 12.5, marginTop: 2 }} />
          </View>
        )}
      </Pressable>
    );
  };

  const empty = (
    <View style={[s.empty, { backgroundColor: t.card, borderColor: t.line }]}>
      {!ready || (cfg && !data) ? <ActivityIndicator color={t.moss} /> : (
        <Text style={{ color: t.muted, textAlign: 'center', lineHeight: 21 }}>
          {cfg ? `No stocks in “${list}” yet.\nTap + Add stock to start.` : 'Connect your price engine to begin.\nTap ⚙ at the top.'}
        </Text>
      )}
    </View>
  );

  const footer = (
    <View>
      {edit && lists.length > 1 && (
        <Pressable onPress={confirmDeleteList} style={{ alignItems: 'center', padding: 16 }}>
          <Text style={{ color: t.down, fontWeight: '700' }}>Delete this list</Text>
        </Pressable>
      )}
      <Text style={[s.byline, { color: t.muted }]}>A tool made by Chitranjan Sharma{'\n'}Prices via Google Finance · may lag by a few minutes</Text>
    </View>
  );

  return (
    <View style={{ flex: 1, backgroundColor: t.bg }}>
      <StatusBar style={scheme === 'dark' ? 'light' : 'dark'} />
      <FlatList
        data={rows}
        keyExtractor={q => q.list + '|' + q.ticker}
        renderItem={renderRow}
        ListHeaderComponent={header}
        ListEmptyComponent={empty}
        ListFooterComponent={footer}
        contentContainerStyle={{ paddingHorizontal: 16, paddingTop: inset.top + 12, paddingBottom: inset.bottom + 110 }}
        refreshControl={<RefreshControl refreshing={false} onRefresh={() => refresh()} tintColor={t.moss} colors={[t.moss]} />}
      />

      {cfg && (
        <Pressable onPress={() => setShowAdd(true)} style={[s.fab, { backgroundColor: t.moss, bottom: inset.bottom + 20 }]}>
          <Text style={{ color: t.onMoss, fontWeight: '700', fontSize: 15 }}>+ Add stock</Text>
        </Pressable>
      )}

      <AddSheet visible={showAdd} listName={list} onClose={() => setShowAdd(false)} onAdd={add} />
      <DetailSheet quote={detail?.q || null} isIndex={!!detail?.index} cfg={cfg} onClose={() => setDetail(null)} onRemove={confirmRemove} />
      <SettingsSheet visible={showSettings} current={cfg}
        onClose={() => setShowSettings(false)}
        onSave={async c => { await saveConfig(c); setCfg(c); setShowSettings(false); refresh(c); }} />
      <NewListSheet visible={showNewList} onClose={() => setShowNewList(false)}
        onCreate={name => { setExtraLists(x => (x.includes(name) ? x : [...x, name])); setList(name); setShowNewList(false); }} />
    </View>
  );
}

function NewListSheet({ visible, onClose, onCreate }: { visible: boolean; onClose: () => void; onCreate: (n: string) => void }) {
  const t = useTheme();
  const [name, setName] = useState('');
  useEffect(() => { if (visible) setName(''); }, [visible]);
  const ok = () => { const n = name.trim().slice(0, 30); if (n) onCreate(n); };
  return (
    <Sheet visible={visible} onClose={onClose}>
      <Text style={{ fontFamily: serif, fontSize: 24, color: t.ink }}>New list</Text>
      <TextInput value={name} onChangeText={setName} placeholder="e.g. Banks, Long term" placeholderTextColor={t.muted} autoFocus
        onSubmitEditing={ok} returnKeyType="done"
        style={{ marginTop: 12, fontSize: 16, paddingHorizontal: 14, paddingVertical: 12, borderRadius: 12, borderWidth: 1, color: t.ink, backgroundColor: t.card, borderColor: t.line }} />
      <Pressable onPress={ok} style={[s.primary, { backgroundColor: t.moss }]}>
        <Text style={{ color: t.onMoss, fontWeight: '700', fontSize: 15 }}>Create list</Text>
      </Pressable>
    </Sheet>
  );
}

function IconBtn({ label, onPress, busy }: { label: string; onPress: () => void; busy?: boolean }) {
  const t = useTheme();
  return (
    <Pressable onPress={onPress} hitSlop={6} style={[s.icon, { backgroundColor: t.card, borderColor: t.line }]}>
      {busy ? <ActivityIndicator size="small" color={t.moss} /> : <Text style={{ color: t.ink, fontSize: 18 }}>{label}</Text>}
    </Pressable>
  );
}

function SmallBtn({ label, onPress, danger }: { label: string; onPress: () => void; danger?: boolean }) {
  const t = useTheme();
  return (
    <Pressable onPress={onPress} hitSlop={4} style={[s.small, { borderColor: t.line }]}>
      <Text style={{ color: danger ? t.down : t.ink, fontSize: 16 }}>{label}</Text>
    </Pressable>
  );
}

const s = StyleSheet.create({
  top: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  h1: { fontFamily: serif, fontSize: 30 },
  pill: { flexDirection: 'row', alignItems: 'center', gap: 6, borderWidth: 1, borderRadius: 99, paddingHorizontal: 10, paddingVertical: 4 },
  dot: { width: 7, height: 7, borderRadius: 4 },
  icon: { width: 42, height: 42, borderRadius: 12, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  err: { padding: 10, borderRadius: 10, marginBottom: 12 },
  indices: { flexDirection: 'row', gap: 6, marginBottom: 14 },
  idx: { flex: 1, borderWidth: 1, borderRadius: 14, paddingHorizontal: 10, paddingVertical: 9 },
  tab: { paddingHorizontal: 15, paddingVertical: 8, borderRadius: 99, borderWidth: 1 },
  toolbar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 12, marginBottom: 8 },
  chip: { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 99, borderWidth: 1 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 13, borderLeftWidth: 1, borderRightWidth: 1, borderBottomWidth: 1 },
  badge: { fontSize: 10, fontWeight: '700', paddingHorizontal: 5, paddingVertical: 1, borderRadius: 5, overflow: 'hidden' },
  px: { fontSize: 16, fontWeight: '700', fontVariant: ['tabular-nums'], paddingHorizontal: 4, borderRadius: 6, overflow: 'hidden' },
  small: { width: 38, height: 38, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  empty: { borderWidth: 1, borderRadius: 16, padding: 28, alignItems: 'center' },
  byline: { textAlign: 'center', fontFamily: serif, fontStyle: 'italic', fontSize: 12, marginTop: 22, lineHeight: 19 },
  fab: { position: 'absolute', alignSelf: 'center', paddingHorizontal: 26, paddingVertical: 15, borderRadius: 99, elevation: 6, shadowColor: '#000', shadowOpacity: 0.18, shadowRadius: 10, shadowOffset: { width: 0, height: 4 } },
  primary: { marginTop: 14, paddingVertical: 15, borderRadius: 12, alignItems: 'center' },
});
