import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import React, { useMemo } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import type { Quote } from '../../api';
import RangeBar from '../../components/RangeBar';
import StockRow from '../../components/StockRow';
import { Card, ChangeText, Header, IconButton, SectionTitle, StatusPill } from '../../components/ui';
import { INDEX_NAMES, ist, market, num, sessionNote } from '../../format';
import { useStore } from '../../store';
import { radius } from '../../theme';

export default function MarketsScreen() {
  const st = useStore();
  const { theme: t, data } = st;
  const m = market(data);

  const { gainers, losers, breadth } = useMemo(() => {
    const seen = new Map<string, Quote>();
    (data?.rows || []).forEach(r => { if (r.changepct != null && !seen.has(r.ticker)) seen.set(r.ticker, r); });
    const all = [...seen.values()];
    const up = all.filter(r => (r.changepct ?? 0) > 0).sort((a, b) => b.changepct! - a.changepct!);
    const down = all.filter(r => (r.changepct ?? 0) < 0).sort((a, b) => a.changepct! - b.changepct!);
    return { gainers: up.slice(0, 5), losers: down.slice(0, 5), breadth: { up: up.length, down: down.length, flat: all.length - up.length - down.length } };
  }, [data]);

  const open = (q: Quote) => router.push({ pathname: '/stock/[ticker]', params: { ticker: q.ticker, list: q.list } });
  const total = breadth.up + breadth.down + breadth.flat;

  return (
    <View style={{ flex: 1, backgroundColor: t.bg }}>
      <Header title="Markets" right={<IconButton name="refresh" label="Refresh prices" busy={st.loading} onPress={st.refresh} />} />
      <ScrollView contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 30 }}
        refreshControl={<RefreshControl refreshing={false} onRefresh={st.refresh} tintColor={t.primary} colors={[t.primary]} progressBackgroundColor={t.surface} />}>

        <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <View style={[s.clock, { backgroundColor: m.k === 'live' ? t.upSoft : t.primarySoft }]}>
            <Ionicons name={m.k === 'live' ? 'pulse' : 'time-outline'} size={22} color={m.k === 'live' ? t.up : t.primary} />
          </View>
          <View style={{ flex: 1 }}>
            <StatusPill />
            <Text style={{ color: t.textSoft, fontSize: 13.5, marginTop: 6, lineHeight: 19 }}>{sessionNote(data)}</Text>
          </View>
        </Card>

        <SectionTitle>Indices</SectionTitle>
        {(data?.indices || []).map(q => (
          <Pressable key={q.ticker} onPress={() => router.push({ pathname: '/stock/[ticker]', params: { ticker: q.ticker, list: 'Indices' } })}>
            {({ pressed }) => (
              <Card style={{ marginBottom: 10, backgroundColor: pressed ? t.surface2 : t.surface }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <View>
                    <Text style={{ color: t.muted, fontSize: 13, fontWeight: '700' }}>{INDEX_NAMES[q.ticker] || q.name}</Text>
                    <Text style={{ color: t.text, fontSize: 24, fontWeight: '800', marginTop: 2, fontVariant: ['tabular-nums'] }}>{num(q.price)}</Text>
                  </View>
                  <View style={{ alignItems: 'flex-end' }}>
                    <ChangeText change={q.change} pct={q.changepct} style={{ fontSize: 14 }} />
                    <Text style={{ color: t.muted, fontSize: 12, marginTop: 4 }}>Prev close {num(q.closeyest)}</Text>
                  </View>
                </View>
                <RangeBar label="Today's range" lo={q.low} hi={q.high} v={q.price} />
              </Card>
            )}
          </Pressable>
        ))}

        {total > 0 && (
          <>
            <SectionTitle>Your stocks today</SectionTitle>
            <Card>
              <View style={{ flexDirection: 'row', height: 10, borderRadius: 5, overflow: 'hidden', backgroundColor: t.surface2 }}>
                <View style={{ flex: breadth.up, backgroundColor: t.up }} />
                <View style={{ flex: breadth.flat, backgroundColor: t.border }} />
                <View style={{ flex: breadth.down, backgroundColor: t.down }} />
              </View>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 10 }}>
                <Text style={{ color: t.up, fontWeight: '700', fontSize: 13 }}>{breadth.up} up</Text>
                {breadth.flat > 0 && <Text style={{ color: t.muted, fontWeight: '700', fontSize: 13 }}>{breadth.flat} unchanged</Text>}
                <Text style={{ color: t.down, fontWeight: '700', fontSize: 13 }}>{breadth.down} down</Text>
              </View>
            </Card>
          </>
        )}

        {gainers.length > 0 && <Movers title="Top gainers" rows={gainers} open={open} />}
        {losers.length > 0 && <Movers title="Top losers" rows={losers} open={open} />}

        <Text style={{ color: t.muted, fontSize: 12, textAlign: 'center', marginTop: 18 }}>
          {data ? `Updated ${ist(new Date(data.at)).hm} IST · ` : ''}Prices from Google Finance
        </Text>
      </ScrollView>
    </View>
  );
}

function Movers({ title, rows, open }: { title: string; rows: Quote[]; open: (q: Quote) => void }) {
  return (
    <>
      <SectionTitle>{title}</SectionTitle>
      <Card padded={false}>
        {rows.map((q, i) => <StockRow key={q.ticker} q={q} showList divider={i < rows.length - 1} onPress={() => open(q)} />)}
      </Card>
    </>
  );
}

const s = StyleSheet.create({
  clock: { width: 46, height: 46, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' },
});
