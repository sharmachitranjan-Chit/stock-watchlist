import React, { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { api, Config, History, Quote } from '../api';
import { arrow, dirOf, INDEX_NAMES, lastTradeLabel, num, parts, signPct, vol } from '../format';
import { serif, useTheme } from '../theme';
import Change from './Change';
import PriceChart from './PriceChart';
import RangeBar from './RangeBar';
import Sheet from './Sheet';

const PERIODS: Record<string, number> = { '1W': 7, '1M': 30, '3M': 91, '1Y': 365 };

type Props = {
  quote: Quote | null;
  isIndex: boolean;
  cfg: Config | null;
  onClose: () => void;
  onRemove: (q: Quote) => void;
};

export default function DetailSheet({ quote, isIndex, cfg, onClose, onRemove }: Props) {
  const t = useTheme();
  const [hist, setHist] = useState<History | null>(null);
  const [failed, setFailed] = useState(false);
  const [period, setPeriod] = useState('1M');
  const ticker = quote?.ticker;

  useEffect(() => {
    if (!ticker || !cfg) return;
    let live = true;
    setHist(null); setFailed(false); setPeriod('1M');
    api.history(cfg, ticker).then(h => live && setHist(h)).catch(() => live && (setFailed(true), setHist([])));
    return () => { live = false; };
  }, [ticker, cfg]);

  const slice = useMemo(() => {
    if (!hist?.length) return [] as number[];
    const cut = new Date(Date.now() - PERIODS[period] * 86400000).toISOString().slice(0, 10);
    const inside = hist.filter(h => h[0] >= cut);
    const before = hist.filter(h => h[0] < cut).pop();
    return (before ? [before, ...inside] : inside).map(h => h[1]);
  }, [hist, period]);

  if (!quote) return <Sheet visible={false} onClose={onClose}>{null}</Sheet>;

  const p = parts(quote.ticker);
  const title = isIndex ? INDEX_NAMES[quote.ticker] || quote.name || p.sym : p.sym;
  const values = quote.price != null && slice.length ? [...slice, quote.price] : slice;
  const base = slice[0];
  const pChange = base != null && quote.price != null ? quote.price - base : null;

  return (
    <Sheet visible onClose={onClose}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        <Text style={[s.title, { color: t.ink }]}>{title}</Text>
        {!isIndex && <Text style={[s.badge, { backgroundColor: t.line, color: t.muted }]}>{p.ex}</Text>}
      </View>
      {!isIndex && !!quote.name && <Text style={{ color: t.muted, fontSize: 13 }}>{quote.name}</Text>}

      <Text style={[s.big, { color: t.ink }]}>{num(quote.price)}</Text>
      <Text style={{ fontSize: 14 }}>
        <Change change={quote.change} pct={quote.changepct} style={{ fontSize: 15 }} />
        <Text style={{ color: t.muted, fontSize: 13 }}>  today</Text>
      </Text>

      <View style={s.periods}>
        {Object.keys(PERIODS).map(k => (
          <Pressable key={k} onPress={() => setPeriod(k)} style={[s.period, { borderColor: k === period ? t.moss : t.line, backgroundColor: k === period ? t.moss : t.card }]}>
            <Text style={{ color: k === period ? t.onMoss : t.ink, fontSize: 13, fontWeight: '600' }}>{k}</Text>
          </Pressable>
        ))}
      </View>

      <View style={[s.card, { backgroundColor: t.card, borderColor: t.line, padding: 10 }]}>
        {hist == null ? (
          <View style={{ height: 120, alignItems: 'center', justifyContent: 'center' }}>
            <ActivityIndicator color={t.moss} />
            <Text style={{ color: t.muted, fontSize: 12, marginTop: 6 }}>Loading history…</Text>
          </View>
        ) : failed ? (
          <View style={{ height: 120, alignItems: 'center', justifyContent: 'center' }}>
            <Text style={{ color: t.muted, fontSize: 13 }}>Could not load history</Text>
          </View>
        ) : (
          <PriceChart values={values} />
        )}
      </View>
      {pChange != null && base != null && (
        <Text style={{ marginTop: 6, fontSize: 13 }}>
          <Text style={{ color: dirOf(pChange) === 'up' ? t.up : dirOf(pChange) === 'down' ? t.down : t.muted }}>
            {arrow(pChange)} {num(Math.abs(pChange))} ({signPct((pChange / base) * 100)})
          </Text>
          <Text style={{ color: t.muted }}>  over {period}</Text>
        </Text>
      )}

      <RangeBar label="Today's range" lo={quote.low} hi={quote.high} v={quote.price} />
      <RangeBar label="52-week range" lo={quote.low52} hi={quote.high52} v={quote.price} />

      <View style={s.stats}>
        <Stat label="Prev close" value={num(quote.closeyest)} />
        <Stat label="Volume" value={vol(quote.volume)} />
        <Stat label="Day high / low" value={`${num(quote.high)} / ${num(quote.low)}`} />
        <Stat label="Last trade" value={lastTradeLabel(quote)} />
      </View>

      <View style={{ flexDirection: 'row', gap: 8, marginTop: 16 }}>
        {!isIndex && (
          <Pressable style={[s.btn, { backgroundColor: t.card, borderColor: t.line }]} onPress={() => onRemove(quote)}>
            <Text style={{ color: t.down, fontSize: 15 }}>Remove</Text>
          </Pressable>
        )}
        <Pressable style={[s.btn, { backgroundColor: t.card, borderColor: t.line }]} onPress={onClose}>
          <Text style={{ color: t.ink, fontSize: 15 }}>Close</Text>
        </Pressable>
      </View>
    </Sheet>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  const t = useTheme();
  return (
    <View style={[s.card, s.stat, { backgroundColor: t.card, borderColor: t.line }]}>
      <Text style={{ color: t.muted, fontSize: 11 }}>{label}</Text>
      <Text style={{ color: t.ink, fontSize: 15, fontWeight: '600', fontVariant: ['tabular-nums'] }} numberOfLines={1} adjustsFontSizeToFit>{value}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  title: { fontFamily: serif, fontSize: 24 },
  badge: { fontSize: 10, fontWeight: '700', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 5, overflow: 'hidden' },
  big: { fontSize: 32, fontWeight: '700', marginTop: 6, fontVariant: ['tabular-nums'] },
  periods: { flexDirection: 'row', gap: 6, marginTop: 14, marginBottom: 8 },
  period: { flex: 1, paddingVertical: 8, borderRadius: 10, borderWidth: 1, alignItems: 'center' },
  card: { borderWidth: 1, borderRadius: 14 },
  stats: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 14 },
  stat: { width: '48.5%', paddingHorizontal: 12, paddingVertical: 9, borderRadius: 12 },
  btn: { flex: 1, paddingVertical: 13, borderRadius: 12, borderWidth: 1, alignItems: 'center' },
});
