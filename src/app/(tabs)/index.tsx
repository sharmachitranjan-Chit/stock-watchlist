import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import React, { useMemo, useState } from 'react';
import { Alert, FlatList, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import type { Quote } from '../../api';
import NameDialog from '../../components/NameDialog';
import StockRow from '../../components/StockRow';
import { ChangePill, Chip, Header, IconButton, PrimaryButton, StatusPill } from '../../components/ui';
import { INDEX_NAMES, ist, latestTrade, num, parts } from '../../format';
import { SortMode, useStore } from '../../store';
import { labelFor } from '../../symbols';
import { radius } from '../../theme';

const SORTS: [SortMode, string][] = [['my', 'My order'], ['gain', 'Top gainers'], ['loss', 'Top losers'], ['name', 'A–Z']];

export default function WatchlistScreen() {
  const st = useStore();
  const { theme: t, data, prefs } = st;
  const [edit, setEdit] = useState(false);
  const [newList, setNewList] = useState(false);

  const rows = useMemo(() => {
    const r = (data?.rows || []).filter(x => x.list === st.activeList);
    if (prefs.sort === 'gain') r.sort((a, b) => (b.changepct ?? -1e9) - (a.changepct ?? -1e9));
    if (prefs.sort === 'loss') r.sort((a, b) => (a.changepct ?? 1e9) - (b.changepct ?? 1e9));
    if (prefs.sort === 'name') r.sort((a, b) => labelFor(a.ticker).localeCompare(labelFor(b.ticker)));
    return r;
  }, [data, st.activeList, prefs.sort]);

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

      <View style={s.toolbar}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 14 }} style={{ flex: 1 }}>
          {SORTS.map(([k, label]) => (
            <Pressable key={k} onPress={() => st.setPrefs({ sort: k })} hitSlop={6}>
              <Text style={{ color: prefs.sort === k ? t.primary : t.muted, fontWeight: prefs.sort === k ? '800' : '500', fontSize: 13.5 }}>{label}</Text>
            </Pressable>
          ))}
        </ScrollView>
        {rows.length > 0 && (
          <Pressable onPress={() => setEdit(e => !e)} hitSlop={8} style={{ marginLeft: 12 }}>
            <Text style={{ color: t.primary, fontWeight: '800', fontSize: 14 }}>{edit ? 'Done' : 'Edit'}</Text>
          </Pressable>
        )}
      </View>
      {edit && prefs.sort !== 'my' && (
        <Text style={{ color: t.muted, fontSize: 12.5, marginHorizontal: 20, marginBottom: 8 }}>Switch to “My order” to move stocks up or down.</Text>
      )}
    </View>
  );

  return (
    <View style={{ flex: 1, backgroundColor: t.bg }}>
      <FlatList
        data={rows}
        keyExtractor={q => q.list + '|' + q.ticker}
        ListHeaderComponent={header}
        renderItem={({ item, index }) => (
          <View style={[s.cardRow, {
            backgroundColor: t.surface, borderColor: t.border,
            borderTopLeftRadius: index === 0 ? radius.lg : 0, borderTopRightRadius: index === 0 ? radius.lg : 0,
            borderBottomLeftRadius: index === rows.length - 1 ? radius.lg : 0, borderBottomRightRadius: index === rows.length - 1 ? radius.lg : 0,
            borderTopWidth: index === 0 ? StyleSheet.hairlineWidth : 0, borderBottomWidth: index === rows.length - 1 ? StyleSheet.hairlineWidth : 0,
          }]}>
            <StockRow q={item} divider={index < rows.length - 1}
              onPress={() => router.push({ pathname: '/stock/[ticker]', params: { ticker: item.ticker, list: item.list } })}
              edit={edit ? {
                first: index === 0 || prefs.sort !== 'my', last: index === rows.length - 1 || prefs.sort !== 'my',
                up: () => run(st.move(item.list, item.ticker, -1)),
                down: () => run(st.move(item.list, item.ticker, 1)),
                remove: () => remove(item),
              } : undefined} />
          </View>
        )}
        ListEmptyComponent={
          <View style={[s.empty, { backgroundColor: t.surface, borderColor: t.border }]}>
            <Ionicons name="bookmarks-outline" size={30} color={t.muted} />
            <Text style={{ color: t.text, fontSize: 16, fontWeight: '700', marginTop: 10 }}>{data ? `“${st.activeList}” is empty` : 'Loading your watchlist'}</Text>
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
