import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import React, { useMemo, useState } from 'react';
import { Alert, FlatList, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import type { Quote } from '../../api';
import NameDialog from '../../components/NameDialog';
import CompareRow, { CompareHeader, Pair } from '../../components/CompareRow';
import StockRow from '../../components/StockRow';
import { ChangePill, Chip, Header, IconButton, PrimaryButton, Segmented, StatusPill } from '../../components/ui';
import { INDEX_NAMES, ist, latestTrade, num, parts } from '../../format';
import { SortMode, useStore, ViewMode } from '../../store';
import { companyFor, labelFor } from '../../symbols';
import { radius } from '../../theme';

const SORTS: [SortMode, string][] = [['my', 'My order'], ['gain', 'Top gainers'], ['loss', 'Top losers'], ['name', 'A–Z']];
const VIEWS: [ViewMode, string][] = [['all', 'All'], ['nse', 'NSE'], ['bse', 'BSE'], ['compare', 'Side by side']];

const bestPct = (p: Pair) => Math.max(p.nse?.changepct ?? -1e9, p.bse?.changepct ?? -1e9);
const worstPct = (p: Pair) => Math.min(p.nse?.changepct ?? 1e9, p.bse?.changepct ?? 1e9);

export default function WatchlistScreen() {
  const st = useStore();
  const { theme: t, data, prefs } = st;
  const [edit, setEdit] = useState(false);
  const [newList, setNewList] = useState(false);

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
  const updated = data ? `Updated ${ist(new Date(data.at)).hm}${lt ? ` · last trade ${ist(new Date(lt)).dayLabel}, ${ist(new Date(lt)).hm}` : ''}` : 'Loading prices…';

  const run = (p: Promise<void>) => p.catch(e => Alert.alert('Could not update', e?.message || String(e)));
  const remove = (q: Quote) => Alert.alert('Remove stock', `Remove ${labelFor(q.ticker, q.name)} (${parts(q.ticker).ex}) from “${q.list}”?`, [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Remove', style: 'destructive', onPress: () => run(st.remove(q.list, q.ticker)) },
  ]);

  const header = (
    <View>
      <Header
        title="Watchlist"
        subtitle={
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            <StatusPill />
            <Text style={{ color: t.muted, fontSize: 12.5 }} numberOfLines={1}>{updated}</Text>
          </View>
        }
        right={<>
          <IconButton name="refresh" label="Refresh prices" busy={st.loading} onPress={st.refresh} />
          <IconButton name="add" label="Add stock" onPress={() => router.navigate('/search')} />
        </>}
      />

      {!!st.error && (
        <Pressable onPress={st.refresh} style={[s.banner, { backgroundColor: t.downSoft }]}>
          <Ionicons name="cloud-offline-outline" size={18} color={t.down} />
          <Text style={{ color: t.down, fontSize: 13, flex: 1 }}>{st.error} Tap to retry.</Text>
        </Pressable>
      )}

      <View style={{ flexDirection: 'row', paddingHorizontal: 16, gap: 8 }}>
        {(data?.indices || []).filter(q => q.price != null).map(q => (
          <Pressable key={q.ticker} onPress={() => router.push({ pathname: '/stock/[ticker]', params: { ticker: q.ticker, list: 'Indices' } })}
            style={({ pressed }) => [s.idx, { backgroundColor: pressed ? t.surface2 : t.surface, borderColor: t.border }]}>
            <Text style={{ color: t.muted, fontSize: 12, fontWeight: '700' }} numberOfLines={1}>{INDEX_NAMES[q.ticker] || q.name}</Text>
            <Text style={{ color: t.text, fontSize: 15.5, fontWeight: '800', marginVertical: 4, fontVariant: ['tabular-nums'] }} numberOfLines={1} adjustsFontSizeToFit>{num(q.price)}</Text>
            <ChangePill change={q.change} pct={q.changepct} compact style={{ alignSelf: 'flex-start' }} />
          </Pressable>
        ))}
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 16, gap: 8, marginTop: 18 }}>
        {st.lists.map(l => <Chip key={l} label={l} active={l === st.activeList} onPress={() => { st.setActiveList(l); setEdit(false); }} />)}
        <Chip label="New list" icon="add" onPress={() => setNewList(true)} />
      </ScrollView>

      <View style={{ marginHorizontal: 16, marginTop: 14 }}>
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

  return (
    <View style={{ flex: 1, backgroundColor: t.bg }}>
      <FlatList<Quote | Pair>
        data={compare ? pairs : rows}
        keyExtractor={x => ('key' in x ? x.key : x.list + '|' + x.ticker)}
        ListHeaderComponent={header}
        renderItem={({ item, index }) => (
          <View style={frame(index)}>
            {'key' in item ? (
              <CompareRow pair={item} list={st.activeList} onOpen={open} divider={index < count - 1} />
            ) : (
              <StockRow q={item} divider={index < count - 1} onPress={() => open(item)}
                edit={edit ? {
                  first: index === 0 || prefs.sort !== 'my', last: index === count - 1 || prefs.sort !== 'my',
                  up: () => run(st.move(item.list, item.ticker, -1)),
                  down: () => run(st.move(item.list, item.ticker, 1)),
                  remove: () => remove(item),
                } : undefined} />
            )}
          </View>
        )}
        ListEmptyComponent={
          <View style={[s.empty, { backgroundColor: t.surface, borderColor: t.border }]}>
            <Ionicons name="bookmarks-outline" size={30} color={t.muted} />
            <Text style={{ color: t.text, fontSize: 16, fontWeight: '700', marginTop: 10, textAlign: 'center' }}>{emptyTitle}</Text>
            <Text style={{ color: t.muted, textAlign: 'center', marginTop: 4, marginBottom: 16, lineHeight: 19 }}>Search any NSE or BSE company by name and add it here.</Text>
            <PrimaryButton label="Search stocks" icon="search" onPress={() => router.navigate('/search')} />
          </View>
        }
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
  banner: { flexDirection: 'row', alignItems: 'center', gap: 8, marginHorizontal: 16, marginBottom: 12, padding: 12, borderRadius: radius.md },
  idx: { flex: 1, minWidth: 0, borderRadius: radius.lg, borderWidth: StyleSheet.hairlineWidth, paddingHorizontal: 11, paddingVertical: 11 },
  toolbar: { flexDirection: 'row', alignItems: 'center', marginHorizontal: 20, marginTop: 16, marginBottom: 10 },
  cardRow: { marginHorizontal: 16, borderLeftWidth: StyleSheet.hairlineWidth, borderRightWidth: StyleSheet.hairlineWidth, overflow: 'hidden' },
  empty: { marginHorizontal: 16, borderRadius: radius.lg, borderWidth: StyleSheet.hairlineWidth, padding: 24, alignItems: 'center' },
  foot: { textAlign: 'center', fontSize: 12, marginTop: 18 },
});
