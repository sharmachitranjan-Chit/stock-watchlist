import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import React, { useMemo, useRef, useState } from 'react';
import { Alert, Animated, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { Quote } from '../../api';
import NameDialog from '../../components/NameDialog';
import CompareRow, { CompareHeader, Pair } from '../../components/CompareRow';
import StockRow from '../../components/StockRow';
import { ChangePill, Chip, IconButton, PrimaryButton, Segmented, StatusPill } from '../../components/ui';
import { arrow, dirOf, INDEX_NAMES, ist, latestTrade, market, num, parts } from '../../format';
import { SortMode, useStore, ViewMode } from '../../store';
import { companyFor, labelFor } from '../../symbols';
import { radius } from '../../theme';

const SORTS: [SortMode, string][] = [['my', 'My order'], ['gain', 'Top gainers'], ['loss', 'Top losers'], ['name', 'A–Z']];
const VIEWS: [ViewMode, string][] = [['all', 'All'], ['nse', 'NSE'], ['bse', 'BSE'], ['compare', 'Side by side']];

const bestPct = (p: Pair) => Math.max(p.nse?.changepct ?? -1e9, p.bse?.changepct ?? -1e9);
const worstPct = (p: Pair) => Math.min(p.nse?.changepct ?? 1e9, p.bse?.changepct ?? 1e9);

type Item = { kind: 'intro' } | { kind: 'controls' } | { kind: 'row'; q: Quote; i: number } | { kind: 'pair'; p: Pair; i: number } | { kind: 'empty' };
const SHORT: Record<string, string> = { 'INDEXNSE:NIFTY_50': 'NIFTY', 'INDEXBOM:SENSEX': 'SENSEX', 'INDEXNSE:NIFTY_BANK': 'BANKNIFTY' };

export default function WatchlistScreen() {
  const st = useStore();
  const { theme: t, data, prefs } = st;
  const [edit, setEdit] = useState(false);
  const [newList, setNewList] = useState(false);

  const inset = useSafeAreaInsets();
  const scrollY = useRef(new Animated.Value(0)).current;
  const [introH, setIntroH] = useState(240);
  const view = prefs.view || 'all';

  const rows = useMemo(() => {
    let r = (data?.rows || []).filter(x => x.list === st.activeList);
    if (view === 'nse') r = r.filter(x => x.ticker.startsWith('NSE:'));
    if (view === 'bse') r = r.filter(x => x.ticker.startsWith('BOM:'));
    if (prefs.sort === 'gain') r.sort((a, b) => (b.changepct ?? -1e9) - (a.changepct ?? -1e9));
    if (prefs.sort === 'loss') r.sort((a, b) => (a.changepct ?? 1e9) - (b.changepct ?? 1e9));
    if (prefs.sort === 'name') r.sort((a, b) => labelFor(a.ticker).localeCompare(labelFor(b.ticker)));
    return r;
  }, [data, st.activeList, prefs.sort, view]);

  /** Same company on both exchanges becomes one pair. */
  const pairs = useMemo(() => {
    const map = new Map<string, Pair>();
    (data?.rows || []).filter(x => x.list === st.activeList).forEach((q, i) => {
      const c = companyFor(q.ticker);
      const k = c?.isin || q.ticker;
      const p = map.get(k) || { key: k, company: c, order: i };
      if (q.ticker.startsWith('NSE:')) p.nse = q; else p.bse = q;
      map.set(k, p);
    });
    const out = [...map.values()];
    if (prefs.sort === 'my') out.sort((a, b) => a.order - b.order);
    if (prefs.sort === 'gain') out.sort((a, b) => bestPct(b) - bestPct(a));
    if (prefs.sort === 'loss') out.sort((a, b) => worstPct(a) - worstPct(b));
    if (prefs.sort === 'name') out.sort((a, b) => labelFor((a.nse || a.bse)!.ticker).localeCompare(labelFor((b.nse || b.bse)!.ticker)));
    return out;
  }, [data, st.activeList, prefs.sort]);

  const compare = view === 'compare';
  const count = compare ? pairs.length : rows.length;

  const lt = latestTrade(data);
  const mk = market(data).k;
  const updated = !data ? 'Loading prices…'
    : mk === 'closed' && lt ? `Prices as of ${ist(new Date(lt)).dayLabel}, ${ist(new Date(lt)).hm}`
    : `Updated ${ist(new Date(data.at)).hm}`;

  const run = (p: Promise<void>) => p.catch(e => Alert.alert('Could not update', e?.message || String(e)));
  const remove = (q: Quote) => Alert.alert('Remove stock', `Remove ${labelFor(q.ticker, q.name)} (${parts(q.ticker).ex}) from “${q.list}”?`, [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Remove', style: 'destructive', onPress: () => run(st.remove(q.list, q.ticker)) },
  ]);

  /* ---------- fixed top bar ---------- */
  // At the top it shows market status; once the index cards scroll away it swaps to a live index ticker.
  const swap = Math.max(introH - 40, 60);
  const statusOpacity = scrollY.interpolate({ inputRange: [swap - 50, swap], outputRange: [1, 0], extrapolate: 'clamp' });
  const tickerOpacity = scrollY.interpolate({ inputRange: [swap - 20, swap + 20], outputRange: [0, 1], extrapolate: 'clamp' });
  const tickerShift = scrollY.interpolate({ inputRange: [swap - 20, swap + 20], outputRange: [8, 0], extrapolate: 'clamp' });
  const shadow = scrollY.interpolate({ inputRange: [0, 24], outputRange: [0, 1], extrapolate: 'clamp' });
  const indices = (data?.indices || []).filter(q => q.price != null);

  const topBar = (
    <View style={{ backgroundColor: t.bg, paddingTop: inset.top + 6, zIndex: 10 }}>
      <View style={s.barRow}>
        <Text style={[s.title, { color: t.text, flex: 1 }]}>Watchlist</Text>
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <IconButton name="refresh" label="Refresh prices" busy={st.loading} onPress={st.refresh} />
          <IconButton name="add" label="Add stock" onPress={() => router.navigate('/search')} />
        </View>
      </View>
      <View style={{ height: 24, marginHorizontal: 16, marginTop: 4, marginBottom: 10 }}>
        <Animated.View style={[StyleSheet.absoluteFill, s.line, { opacity: statusOpacity }]}>
          <StatusPill />
          <Text style={{ color: t.muted, fontSize: 12, flexShrink: 1 }} numberOfLines={1}>{updated}</Text>
        </Animated.View>
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, s.line, { opacity: tickerOpacity, transform: [{ translateY: tickerShift }], gap: 0, justifyContent: 'space-between', overflow: 'hidden' }]}>
          {indices.map(q => {
            const d = dirOf(q.change);
            return (
              <View key={q.ticker} style={{ flexDirection: 'row', alignItems: 'baseline', gap: 4, flexShrink: 0 }}>
                <Text numberOfLines={1} style={{ color: t.muted, fontSize: 11, fontWeight: '800', letterSpacing: 0.2 }}>{SHORT[q.ticker] || q.name}</Text>
                <Text numberOfLines={1} style={{ color: d === 'up' ? t.up : d === 'down' ? t.down : t.muted, fontSize: 12.5, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
                  {arrow(q.change)}{q.changepct == null ? '' : num(Math.abs(q.changepct)) + '%'}
                </Text>
              </View>
            );
          })}
        </Animated.View>
      </View>
      <Animated.View style={{ height: StyleSheet.hairlineWidth, backgroundColor: t.border, opacity: shadow }} />
    </View>
  );

  /* ---------- scrolling part above the pinned controls ---------- */
  const intro = (
    <View onLayout={e => setIntroH(e.nativeEvent.layout.height)} style={{ paddingTop: 6 }}>
      {!!st.error && (
        <Pressable onPress={st.refresh} style={[s.banner, { backgroundColor: t.downSoft }]}>
          <Ionicons name="cloud-offline-outline" size={18} color={t.down} />
          <Text style={{ color: t.down, fontSize: 13, flex: 1 }}>{st.error} Tap to retry.</Text>
        </Pressable>
      )}
      <View style={{ flexDirection: 'row', paddingHorizontal: 16, gap: 8 }}>
        {indices.map(q => (
          <Pressable key={q.ticker} onPress={() => router.push({ pathname: '/stock/[ticker]', params: { ticker: q.ticker, list: 'Indices' } })}
            style={({ pressed }) => [s.idx, { backgroundColor: pressed ? t.surface2 : t.surface, borderColor: t.border }]}>
            <Text style={{ color: t.muted, fontSize: 12, fontWeight: '700' }} numberOfLines={1}>{INDEX_NAMES[q.ticker] || q.name}</Text>
            <Text style={{ color: t.text, fontSize: 15.5, fontWeight: '800', marginVertical: 4, fontVariant: ['tabular-nums'] }} numberOfLines={1} adjustsFontSizeToFit>{num(q.price)}</Text>
            <ChangePill change={q.change} pct={q.changepct} compact style={{ alignSelf: 'flex-start' }} />
          </Pressable>
        ))}
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 16, gap: 8, marginTop: 16, paddingBottom: 4 }}>
        {st.lists.map(l => <Chip key={l} label={l} active={l === st.activeList} onPress={() => { st.setActiveList(l); setEdit(false); }} />)}
        <Chip label="New list" icon="add" onPress={() => setNewList(true)} />
      </ScrollView>
    </View>
  );

  /* ---------- pinned under the top bar while stocks scroll ---------- */
  const controls = (
    <View style={{ backgroundColor: t.bg, paddingTop: 10, paddingBottom: 2 }}>
      <View style={{ marginHorizontal: 16 }}>
        <Segmented value={view} options={VIEWS} onChange={v => { st.setPrefs({ view: v }); setEdit(false); }} />
      </View>
      <View style={s.toolbar}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 14 }} style={{ flex: 1 }}>
          {SORTS.map(([k, label]) => (
            <Pressable key={k} onPress={() => st.setPrefs({ sort: k })} hitSlop={6}>
              <Text style={{ color: prefs.sort === k ? t.primary : t.muted, fontWeight: prefs.sort === k ? '800' : '500', fontSize: 13.5 }}>{label}</Text>
            </Pressable>
          ))}
        </ScrollView>
        {!compare && rows.length > 0 && (
          <Pressable onPress={() => setEdit(e => !e)} hitSlop={8} style={{ marginLeft: 12 }}>
            <Text style={{ color: t.primary, fontWeight: '800', fontSize: 14 }}>{edit ? 'Done' : 'Edit'}</Text>
          </Pressable>
        )}
      </View>
      {edit && prefs.sort !== 'my' && (
        <Text style={{ color: t.muted, fontSize: 12.5, marginHorizontal: 20, marginBottom: 8 }}>Switch to “My order” to move stocks up or down.</Text>
      )}
      {compare && pairs.length > 0 && <CompareHeader />}
    </View>
  );

  const open = (q: Quote) => router.push({ pathname: '/stock/[ticker]', params: { ticker: q.ticker, list: q.list } });
  const frame = (index: number) => [s.cardRow, {
    backgroundColor: t.surface, borderColor: t.border,
    borderTopLeftRadius: index === 0 ? radius.lg : 0, borderTopRightRadius: index === 0 ? radius.lg : 0,
    borderBottomLeftRadius: index === count - 1 ? radius.lg : 0, borderBottomRightRadius: index === count - 1 ? radius.lg : 0,
    borderTopWidth: index === 0 ? StyleSheet.hairlineWidth : 0, borderBottomWidth: index === count - 1 ? StyleSheet.hairlineWidth : 0,
  }];
  const emptyTitle = !data ? 'Loading your watchlist' : view === 'nse' ? `No NSE stocks in “${st.activeList}”` : view === 'bse' ? `No BSE stocks in “${st.activeList}”` : `“${st.activeList}” is empty`;

  const items: Item[] = [
    { kind: 'intro' }, { kind: 'controls' },
    ...(compare ? pairs.map((p, i) => ({ kind: 'pair' as const, p, i })) : rows.map((q, i) => ({ kind: 'row' as const, q, i }))),
  ];
  if (count === 0) items.push({ kind: 'empty' });

  const render = ({ item }: { item: Item }) => {
    switch (item.kind) {
      case 'intro': return intro;
      case 'controls': return controls;
      case 'pair': return (
        <View style={frame(item.i)}>
          <CompareRow pair={item.p} list={st.activeList} onOpen={open} divider={item.i < count - 1} />
        </View>
      );
      case 'row': {
        const q = item.q, i = item.i;
        return (
          <View style={frame(i)}>
            <StockRow q={q} divider={i < count - 1} onPress={() => open(q)}
              edit={edit ? {
                first: i === 0 || prefs.sort !== 'my', last: i === count - 1 || prefs.sort !== 'my',
                up: () => run(st.move(q.list, q.ticker, -1)),
                down: () => run(st.move(q.list, q.ticker, 1)),
                remove: () => remove(q),
              } : undefined} />
          </View>
        );
      }
      case 'empty': return (
        <View style={[s.empty, { backgroundColor: t.surface, borderColor: t.border }]}>
          <Ionicons name="bookmarks-outline" size={30} color={t.muted} />
          <Text style={{ color: t.text, fontSize: 16, fontWeight: '700', marginTop: 10, textAlign: 'center' }}>{emptyTitle}</Text>
          <Text style={{ color: t.muted, textAlign: 'center', marginTop: 4, marginBottom: 16, lineHeight: 19 }}>Search any NSE or BSE company by name and add it here.</Text>
          <PrimaryButton label="Search stocks" icon="search" onPress={() => router.navigate('/search')} />
        </View>
      );
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: t.bg }}>
      {topBar}
      <Animated.FlatList
        data={items}
        keyExtractor={(x: Item) => x.kind === 'row' ? x.q.list + '|' + x.q.ticker : x.kind === 'pair' ? x.p.key : x.kind}
        renderItem={render}
        stickyHeaderIndices={[1]}
        onScroll={Animated.event([{ nativeEvent: { contentOffset: { y: scrollY } } }], { useNativeDriver: true })}
        scrollEventThrottle={16}
        ListFooterComponent={<Text style={[s.foot, { color: t.muted }]}>Prices from Google Finance · may lag by a few minutes</Text>}
        contentContainerStyle={{ paddingBottom: 30 }}
        refreshControl={<RefreshControl refreshing={false} onRefresh={st.refresh} tintColor={t.primary} colors={[t.primary]} progressBackgroundColor={t.surface} />}
      />
      <NameDialog visible={newList} title="New list" placeholder="e.g. Banks, Long term" onClose={() => setNewList(false)}
        onSubmit={n => { st.createList(n); setNewList(false); }} />
    </View>
  );
}

const s = StyleSheet.create({
  barRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16 },
  title: { fontSize: 26, fontWeight: '800', letterSpacing: -0.4 },
  line: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  banner: { flexDirection: 'row', alignItems: 'center', gap: 8, marginHorizontal: 16, marginBottom: 12, padding: 12, borderRadius: radius.md },
  idx: { flex: 1, minWidth: 0, borderRadius: radius.lg, borderWidth: StyleSheet.hairlineWidth, paddingHorizontal: 11, paddingVertical: 11 },
  toolbar: { flexDirection: 'row', alignItems: 'center', marginHorizontal: 20, marginTop: 14, marginBottom: 10 },
  cardRow: { marginHorizontal: 16, borderLeftWidth: StyleSheet.hairlineWidth, borderRightWidth: StyleSheet.hairlineWidth, overflow: 'hidden' },
  empty: { marginHorizontal: 16, borderRadius: radius.lg, borderWidth: StyleSheet.hairlineWidth, padding: 24, alignItems: 'center' },
  foot: { textAlign: 'center', fontSize: 12, marginTop: 18 },
});
