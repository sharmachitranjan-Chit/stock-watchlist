import Ionicons from '@expo/vector-icons/Ionicons';
import { router, useLocalSearchParams } from 'expo-router';
import React, { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { api, History, Quote } from '../../api';
import PriceChart from '../../components/PriceChart';
import RangeBar from '../../components/RangeBar';
import { Card, ChangeText, Chip, ExBadge, Header, IconButton, SectionTitle } from '../../components/ui';
import { arrow, dirOf, INDEX_NAMES, lastTradeLabel, num, parts, signPct, vol } from '../../format';
import { useStore } from '../../store';
import { companyFor, labelFor } from '../../symbols';
import { radius } from '../../theme';

const PERIODS: [string, number][] = [['1W', 7], ['1M', 30], ['3M', 91], ['6M', 182], ['1Y', 365]];

export default function StockScreen() {
  const { ticker, list } = useLocalSearchParams<{ ticker: string; list?: string }>();
  const st = useStore();
  const t = st.theme;
  const isIndex = list === 'Indices' || ticker.startsWith('INDEX');
  const all: Quote[] = [...(st.data?.indices || []), ...(st.data?.rows || [])];
  const q = all.find(r => r.ticker === ticker && (isIndex ? r.list === 'Indices' : true));
  const c = companyFor(ticker);
  const p = parts(ticker);

  const [hist, setHist] = useState<History | null>(null);
  const [histSource, setHistSource] = useState<'own' | 'nse'>('own');
  const [failed, setFailed] = useState(false);
  const [period, setPeriod] = useState('1M');
  const [busyList, setBusyList] = useState('');

  useEffect(() => {
    if (!st.cfg) return;
    let live = true;
    (async () => {
      try {
        let h = await api.history(st.cfg!, ticker);
        // Google keeps only a point or two of history for many BSE stocks.
        // The same company's NSE history tracks it within paise, so use that instead.
        if (h.length < 15 && ticker.startsWith('BOM:') && c?.nse) {
          const alt = await api.history(st.cfg!, 'NSE:' + c.nse).catch(() => [] as History);
          if (alt.length > h.length) { h = alt; if (live) setHistSource('nse'); }
        }
        if (live) setHist(h);
      } catch {
        if (live) { setFailed(true); setHist([]); }
      }
    })();
    return () => { live = false; };
  }, [ticker, st.cfg, c?.nse]);

  const slice = useMemo(() => {
    if (!hist?.length) return [] as number[];
    const days = PERIODS.find(x => x[0] === period)![1];
    const cutMs = Date.now() - days * 86400000;
    const cut = new Date(cutMs).toISOString().slice(0, 10);
    const inside = hist.filter(h => h[0] >= cut);
    // Start the line from the close just before the period, but only if it is close to the start date.
    const before = hist.filter(h => h[0] < cut).pop();
    const near = before && (cutMs - new Date(before[0]).getTime()) < 10 * 86400000;
    if (!inside.length) return [] as number[];
    return (near ? [before!, ...inside] : inside).map(h => h[1]);
  }, [hist, period]);

  const memberOf = new Set((st.data?.rows || []).filter(r => r.ticker === ticker).map(r => r.list));
  const rowsAll = st.data?.rows || [];
  const nseQ = c?.nse ? rowsAll.find(r => r.ticker === 'NSE:' + c.nse) : undefined;
  const bseQ = c?.bse ? rowsAll.find(r => r.ticker === 'BOM:' + c.bse) : undefined;
  const twin = !isIndex && !!nseQ && !!bseQ;
  const twinGap = twin && nseQ!.price != null && bseQ!.price != null ? bseQ!.price - nseQ!.price : null;
  const title = isIndex ? INDEX_NAMES[ticker] || q?.name || ticker : labelFor(ticker, q?.name);

  async function toggleList(l: string) {
    setBusyList(l);
    try {
      if (memberOf.has(l)) await st.remove(l, ticker); else await st.add(l, ticker);
      st.showToast(memberOf.has(l) ? `Removed from ${l}` : `Added to ${l}`);
    } catch (e: any) { Alert.alert('Could not update', e?.message || String(e)); }
    finally { setBusyList(''); }
  }

  const price = q?.price ?? null;
  const values = price != null && slice.length ? [...slice, price] : slice;
  const base = slice[0];
  const pChange = base != null && price != null ? price - base : null;
  const pd = dirOf(pChange);

  return (
    <View style={{ flex: 1, backgroundColor: t.bg }}>
      <Header title={title}
        left={<IconButton name="arrow-back" label="Back" onPress={() => (router.canGoBack() ? router.back() : router.replace('/'))} />}
        right={<IconButton name="refresh" label="Refresh" busy={st.loading} onPress={st.refresh} />} />
      <ScrollView contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 32 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          {!isIndex && <ExBadge ex={p.ex} />}
          <Text style={{ color: t.muted, fontSize: 13.5, flex: 1 }} numberOfLines={1}>
            {isIndex ? (p.ex === 'BSE' ? 'BSE index' : 'NSE index') : c?.name || q?.name || ''}
          </Text>
        </View>

        {!q ? (
          <Card style={{ marginTop: 16 }}><Text style={{ color: t.muted }}>This stock isn't in your watchlists any more.</Text></Card>
        ) : (
          <>
            <Text style={{ color: t.text, fontSize: 36, fontWeight: '800', marginTop: 8, letterSpacing: -0.5, fontVariant: ['tabular-nums'] }}>₹{num(price)}</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 2 }}>
              <ChangeText change={q.change} pct={q.changepct} style={{ fontSize: 15 }} />
              <Text style={{ color: t.muted, fontSize: 13 }}>today · {lastTradeLabel(q)}</Text>
            </View>

            <Card style={{ marginTop: 18 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 }}>
                {pChange != null && base != null ? (
                  <Text style={{ fontSize: 13.5 }}>
                    <Text style={{ color: pd === 'up' ? t.up : pd === 'down' ? t.down : t.muted, fontWeight: '800' }}>{arrow(pChange)} {num(Math.abs(pChange))} ({signPct((pChange / base) * 100)})</Text>
                    <Text style={{ color: t.muted }}>  in {period}</Text>
                  </Text>
                ) : <Text style={{ color: t.muted, fontSize: 13.5 }}>Price history</Text>}
              </View>
              {hist == null ? (
                <View style={{ height: 170, alignItems: 'center', justifyContent: 'center' }}>
                  <ActivityIndicator color={t.primary} />
                  <Text style={{ color: t.muted, fontSize: 12.5, marginTop: 8 }}>Loading history…</Text>
                </View>
              ) : failed ? (
                <View style={{ height: 170, alignItems: 'center', justifyContent: 'center' }}>
                  <Text style={{ color: t.muted }}>Couldn't load history</Text>
                </View>
              ) : <PriceChart values={values} />}
              {histSource === 'nse' && hist != null && !failed && (
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 8 }}>
                  <Ionicons name="information-circle-outline" size={15} color={t.muted} />
                  <Text style={{ color: t.muted, fontSize: 12, flex: 1 }}>Google has no BSE price history for this stock, so the chart shows its NSE prices.</Text>
                </View>
              )}
              <View style={[s.periods, { backgroundColor: t.surface2 }]}>
                {PERIODS.map(([k]) => (
                  <Pressable key={k} onPress={() => setPeriod(k)} style={[s.period, k === period && { backgroundColor: t.primary }]}>
                    <Text style={{ color: k === period ? t.onPrimary : t.textSoft, fontWeight: '700', fontSize: 13 }}>{k}</Text>
                  </Pressable>
                ))}
              </View>
            </Card>

            {twin && (
              <>
                <SectionTitle>NSE vs BSE</SectionTitle>
                <Card>
                  <View style={{ flexDirection: 'row', gap: 10 }}>
                    {[nseQ, bseQ].map((x, i) => (
                      <Pressable key={i} disabled={!x || x.ticker === ticker}
                        onPress={() => x && router.replace({ pathname: '/stock/[ticker]', params: { ticker: x.ticker, list: x.list } })}
                        style={[s.twin, { backgroundColor: x?.ticker === ticker ? t.primarySoft : t.surface2, borderColor: x?.ticker === ticker ? t.primary : 'transparent' }]}>
                        <Text style={{ color: t.muted, fontSize: 12, fontWeight: '800', letterSpacing: 0.5 }}>{i === 0 ? 'NSE' : 'BSE'}</Text>
                        <Text style={{ color: t.text, fontSize: 19, fontWeight: '800', marginTop: 4, fontVariant: ['tabular-nums'] }}>{num(x?.price)}</Text>
                        {x && <ChangeText change={x.change} pct={x.changepct} style={{ fontSize: 12.5, marginTop: 2 }} />}
                      </Pressable>
                    ))}
                  </View>
                  {twinGap != null && (
                    <Text style={{ color: t.textSoft, fontSize: 13, marginTop: 12 }}>
                      {twinGap === 0 ? 'Same price on both exchanges right now.' : `BSE is ₹${num(Math.abs(twinGap))} ${twinGap > 0 ? 'higher' : 'lower'} than NSE (${num(Math.abs((twinGap / nseQ!.price!) * 100), 2)}%).`}
                    </Text>
                  )}
                </Card>
              </>
            )}

            <SectionTitle>Today</SectionTitle>
            <Card>
              <Grid items={[
                ['Day low – high', `${num(q.low)} – ${num(q.high)}`],
                ['Prev close', num(q.closeyest)],
                ['Volume', isIndex ? '—' : vol(q.volume)],
                ['Last trade', lastTradeLabel(q)],
              ]} />
              <RangeBar label="Day range" lo={q.low} hi={q.high} v={price} />
              <RangeBar label="52-week range" lo={q.low52} hi={q.high52} v={price} />
            </Card>

            {!isIndex && (
              <>
                <SectionTitle>In your lists</SectionTitle>
                <Card>
                  <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                    {st.lists.map(l => (
                      <Chip key={l} label={busyList === l ? 'Saving…' : l} active={memberOf.has(l)} icon={memberOf.has(l) ? 'checkmark' : 'add'} onPress={() => !busyList && toggleList(l)} />
                    ))}
                  </View>
                  <Text style={{ color: t.muted, fontSize: 12.5, marginTop: 10 }}>Tap a list to add or remove this stock.</Text>
                </Card>
                {c && c.nse && c.bse && !twin && (
                  <Pressable onPress={() => router.navigate('/search')} style={[s.other, { borderColor: t.border }]}>
                    <Ionicons name="swap-horizontal" size={18} color={t.primary} />
                    <Text style={{ color: t.primary, fontWeight: '700', flex: 1 }}>Also listed on {p.ex === 'NSE' ? 'BSE' : 'NSE'} · add it from Search</Text>
                  </Pressable>
                )}
              </>
            )}
          </>
        )}
      </ScrollView>
    </View>
  );
}

function Grid({ items }: { items: [string, string][] }) {
  const t = useStore().theme;
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', rowGap: 14 }}>
      {items.map(([k, v]) => (
        <View key={k} style={{ width: '50%' }}>
          <Text style={{ color: t.muted, fontSize: 12.5 }}>{k}</Text>
          <Text style={{ color: t.text, fontSize: 15, fontWeight: '700', marginTop: 3, fontVariant: ['tabular-nums'] }} numberOfLines={1} adjustsFontSizeToFit>{v}</Text>
        </View>
      ))}
    </View>
  );
}

const s = StyleSheet.create({
  periods: { flexDirection: 'row', borderRadius: radius.md, padding: 3, marginTop: 14 },
  period: { flex: 1, alignItems: 'center', paddingVertical: 8, borderRadius: radius.sm + 1 },
  twin: { flex: 1, padding: 12, borderRadius: radius.md, borderWidth: 1.5 },
  other: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 12, padding: 14, borderRadius: radius.lg, borderWidth: StyleSheet.hairlineWidth },
});
