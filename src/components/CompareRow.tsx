import Ionicons from '@expo/vector-icons/Ionicons';
import React, { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import type { Quote } from '../api';
import { arrow, dirOf, num, signPct } from '../format';
import { quoteKey, useStore } from '../store';
import { Company, labelFor } from '../symbols';
import { radius } from '../theme';

export type Pair = { key: string; company?: Company; nse?: Quote; bse?: Quote; order: number };

export const COL_W = 96;

/** Column titles shown once above the side-by-side list. */
export function CompareHeader() {
  const t = useStore().theme;
  return (
    <View style={[s.head, { marginHorizontal: 16 }]}>
      <Text style={[s.headText, { color: t.muted, flex: 1, textAlign: 'left' }]}>COMPANY</Text>
      <Text style={[s.headText, { color: t.muted, width: COL_W }]}>NSE</Text>
      <Text style={[s.headText, { color: t.muted, width: COL_W }]}>BSE</Text>
    </View>
  );
}

/** One company, with its NSE and BSE prices in two columns and the gap between them underneath. */
export default function CompareRow({ pair, list, onOpen, divider }: { pair: Pair; list: string; onOpen: (q: Quote) => void; divider: boolean }) {
  const st = useStore();
  const t = st.theme;
  const { company: c, nse, bse } = pair;
  const any = nse || bse!;
  const title = labelFor(any.ticker, any.name);
  const sub = c?.short || any.name || '';

  const gap = nse?.price != null && bse?.price != null ? bse.price - nse.price : null;
  const gapPct = gap != null && nse?.price ? (gap / nse.price) * 100 : null;
  const lower = gap == null || gap === 0 ? null : gap > 0 ? 'NSE' : 'BSE';

  return (
    <Pressable onPress={() => onOpen(nse || bse!)}
      style={({ pressed }) => [s.row, { backgroundColor: pressed ? t.surface2 : 'transparent', borderBottomColor: t.border, borderBottomWidth: divider ? StyleSheet.hairlineWidth : 0 }]}>
      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
        <View style={{ flex: 1, paddingRight: 8 }}>
          <Text style={{ color: t.text, fontWeight: '700', fontSize: 15 }} numberOfLines={1}>{title}</Text>
          <Text style={{ color: t.muted, fontSize: 12, marginTop: 2 }} numberOfLines={1}>{sub}</Text>
        </View>
        <Cell q={nse} lowest={lower === 'NSE'} addTicker={c?.nse ? 'NSE:' + c.nse : undefined} list={list} />
        <Cell q={bse} lowest={lower === 'BSE'} addTicker={c?.bse ? 'BOM:' + c.bse : undefined} list={list} />
      </View>
      {gap != null && (
        <View style={[s.gap, { backgroundColor: t.surface2 }]}>
          <Ionicons name="swap-horizontal" size={13} color={t.muted} />
          <Text style={{ color: t.textSoft, fontSize: 12, fontVariant: ['tabular-nums'] }}>
            {gap === 0 ? 'Same price on both exchanges' : `Gap ₹${num(Math.abs(gap))} (${num(Math.abs(gapPct!), 2)}%) · ${lower} is lower`}
          </Text>
        </View>
      )}
    </Pressable>
  );
}

function Cell({ q, lowest, addTicker, list }: { q?: Quote; lowest: boolean; addTicker?: string; list: string }) {
  const st = useStore();
  const t = st.theme;
  const [busy, setBusy] = useState(false);
  const f = q ? st.flash[quoteKey(q)] : undefined;

  if (!q) {
    if (!addTicker) return <View style={{ width: COL_W, alignItems: 'flex-end' }}><Text style={{ color: t.muted, fontSize: 12 }}>Not listed</Text></View>;
    const add = async () => {
      setBusy(true);
      try { await st.add(list, addTicker); st.showToast(`Added ${addTicker.startsWith('NSE') ? 'NSE' : 'BSE'} price`); }
      catch (e: any) { st.showToast(e?.message || String(e)); }
      finally { setBusy(false); }
    };
    return (
      <View style={{ width: COL_W, alignItems: 'flex-end' }}>
        <Pressable onPress={add} disabled={busy} hitSlop={6}
          style={({ pressed }) => [s.add, { borderColor: t.border, backgroundColor: pressed ? t.primarySoft : 'transparent' }]}>
          {busy ? <ActivityIndicator size="small" color={t.primary} /> : (
            <><Ionicons name="add" size={14} color={t.primary} /><Text style={{ color: t.primary, fontWeight: '800', fontSize: 12 }}>Add</Text></>
          )}
        </Pressable>
      </View>
    );
  }

  const d = dirOf(q.change);
  const fg = d === 'up' ? t.up : d === 'down' ? t.down : t.muted;
  return (
    <View style={{ width: COL_W, alignItems: 'flex-end' }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
        {lowest && <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: t.primary }} />}
        <Text style={[s.price, { color: t.text, backgroundColor: f ? (f === 'up' ? t.upSoft : t.downSoft) : 'transparent' }]} numberOfLines={1} adjustsFontSizeToFit>{num(q.price)}</Text>
      </View>
      <Text style={{ color: fg, fontSize: 12, fontWeight: '700', marginTop: 2, fontVariant: ['tabular-nums'] }}>{q.change == null ? '—' : `${arrow(q.change)} ${signPct(q.changepct)}`}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  head: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, marginBottom: 6 },
  headText: { fontSize: 11.5, fontWeight: '800', letterSpacing: 0.6, textAlign: 'right' },
  row: { paddingHorizontal: 14, paddingVertical: 12 },
  price: { fontSize: 15.5, fontWeight: '700', fontVariant: ['tabular-nums'], paddingHorizontal: 3, borderRadius: 5, overflow: 'hidden' },
  gap: { flexDirection: 'row', alignItems: 'center', gap: 6, alignSelf: 'flex-start', marginTop: 9, paddingHorizontal: 9, paddingVertical: 4, borderRadius: radius.sm },
  add: { flexDirection: 'row', alignItems: 'center', gap: 2, height: 30, paddingHorizontal: 10, borderRadius: radius.sm, borderWidth: 1 },
});
