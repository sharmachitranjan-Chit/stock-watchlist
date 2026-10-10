import Ionicons from '@expo/vector-icons/Ionicons';
import { useIsFocused } from 'expo-router';
import React, { useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Keyboard, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Chip } from '../../components/ui';
import { useStore } from '../../store';
import { COMPANIES, Company, POPULAR, search } from '../../symbols';
import { radius } from '../../theme';

export default function SearchScreen() {
  const st = useStore();
  const t = st.theme;
  const inset = useSafeAreaInsets();
  const [q, setQ] = useState('');
  const dq = useDeferredValue(q);
  const [pending, setPending] = useState<Record<string, boolean>>({});
  const input = useRef<TextInput>(null);
  const focused = useIsFocused();

  useEffect(() => { if (focused) setTimeout(() => input.current?.focus(), 250); }, [focused]);

  const results = useMemo(() => (dq.trim() ? search(dq) : POPULAR), [dq]);
  const inList = useMemo(() => new Set((st.data?.rows || []).filter(r => r.list === st.activeList).map(r => r.ticker)), [st.data, st.activeList]);

  async function toggle(ticker: string, label: string) {
    if (pending[ticker]) return;
    setPending(p => ({ ...p, [ticker]: true }));
    try {
      if (inList.has(ticker)) { await st.remove(st.activeList, ticker); st.showToast(`${label} removed from ${st.activeList}`); }
      else { await st.add(st.activeList, ticker); st.showToast(`${label} added to ${st.activeList}`); }
    } catch (e: any) {
      st.showToast(e?.message || String(e));
    } finally {
      setPending(p => { const n = { ...p }; delete n[ticker]; return n; });
    }
  }

  const ExButton = ({ c, ex }: { c: Company; ex: 'NSE' | 'BSE' }) => {
    const code = ex === 'NSE' ? c.nse : c.bse;
    if (!code) return <View style={{ width: 58 }} />;
    const ticker = (ex === 'NSE' ? 'NSE:' : 'BOM:') + code;
    const on = inList.has(ticker), busy = pending[ticker];
    return (
      <Pressable onPress={() => toggle(ticker, `${c.nse || c.short} (${ex})`)} hitSlop={4}
        accessibilityLabel={`${on ? 'Remove' : 'Add'} ${c.short} on ${ex}`}
        style={({ pressed }) => [s.exBtn, {
          backgroundColor: on ? t.primary : pressed ? t.primarySoft : t.surface,
          borderColor: on ? t.primary : t.border,
        }]}>
        {busy ? <ActivityIndicator size="small" color={on ? t.onPrimary : t.primary} /> : (
          <>
            <Ionicons name={on ? 'checkmark' : 'add'} size={15} color={on ? t.onPrimary : t.primary} />
            <Text style={{ color: on ? t.onPrimary : t.primary, fontWeight: '800', fontSize: 12.5 }}>{ex}</Text>
          </>
        )}
      </Pressable>
    );
  };

  return (
    <View style={{ flex: 1, backgroundColor: t.bg, paddingTop: inset.top + 8 }}>
      <View style={{ paddingHorizontal: 16 }}>
        <Text style={{ color: t.text, fontSize: 26, fontWeight: '800', letterSpacing: -0.4, marginBottom: 12 }}>Search</Text>
        <View style={[s.box, { backgroundColor: t.surface, borderColor: t.primary }]}>
          <Ionicons name="search" size={20} color={t.muted} />
          <TextInput ref={input} value={q} onChangeText={setQ}
            placeholder="Search by name, symbol or code" placeholderTextColor={t.muted}
            autoCorrect={false} autoCapitalize="characters" returnKeyType="search" onSubmitEditing={Keyboard.dismiss}
            style={{ flex: 1, color: t.text, fontSize: 16, paddingVertical: 0 }} />
          {!!q && <Pressable onPress={() => setQ('')} hitSlop={8}><Ionicons name="close-circle" size={20} color={t.muted} /></Pressable>}
        </View>
      </View>

      <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 12 }}>
        <Text style={{ color: t.muted, fontSize: 12.5, fontWeight: '700', marginLeft: 16, marginRight: 8 }}>ADD TO</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} keyboardShouldPersistTaps="handled" contentContainerStyle={{ gap: 8, paddingRight: 16 }}>
          {st.lists.map(l => <Chip key={l} label={l} active={l === st.activeList} onPress={() => st.setActiveList(l)} />)}
        </ScrollView>
      </View>

      <Text style={{ color: t.muted, fontSize: 12.5, fontWeight: '700', marginHorizontal: 20, marginTop: 18, marginBottom: 8, letterSpacing: 0.5 }}>
        {q.trim() ? (results.length ? `${results.length === 40 ? '40+' : results.length} RESULTS` : 'NO MATCHES') : 'POPULAR'}
      </Text>

      <FlatList
        data={results}
        keyExtractor={c => c.isin || c.nse || c.bse}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
        contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 24 }}
        renderItem={({ item: c, index }) => (
          <View style={[s.row, {
            backgroundColor: t.surface, borderColor: t.border,
            borderTopLeftRadius: index === 0 ? radius.lg : 0, borderTopRightRadius: index === 0 ? radius.lg : 0,
            borderBottomLeftRadius: index === results.length - 1 ? radius.lg : 0, borderBottomRightRadius: index === results.length - 1 ? radius.lg : 0,
            borderTopWidth: index === 0 ? StyleSheet.hairlineWidth : 0,
            borderBottomWidth: StyleSheet.hairlineWidth,
          }]}>
            <View style={{ flex: 1, paddingRight: 10 }}>
              <Text style={{ color: t.text, fontSize: 15, fontWeight: '700' }} numberOfLines={1}>{c.short}</Text>
              <Text style={{ color: t.muted, fontSize: 12.5, marginTop: 3 }} numberOfLines={1}>
                {[c.nse && `NSE ${c.nse}`, c.bse && `BSE ${c.bse}`].filter(Boolean).join(' · ')}
              </Text>
            </View>
            <View style={{ flexDirection: 'row', gap: 6 }}>
              <ExButton c={c} ex="NSE" />
              <ExButton c={c} ex="BSE" />
            </View>
          </View>
        )}
        ListEmptyComponent={q.trim() ? (
          <Text style={{ color: t.muted, textAlign: 'center', marginTop: 30, lineHeight: 20 }}>
            Nothing matches “{q.trim()}”.{'\n'}Try part of the company name, like “baroda” or “tata”.
          </Text>
        ) : null}
        ListFooterComponent={<Text style={{ color: t.muted, fontSize: 12, textAlign: 'center', marginTop: 16 }}>{COMPANIES.length.toLocaleString('en-IN')} companies from NSE and BSE · updated weekly</Text>}
      />
    </View>
  );
}

const s = StyleSheet.create({
  box: { flexDirection: 'row', alignItems: 'center', gap: 10, height: 52, borderRadius: radius.md, borderWidth: 1.5, paddingHorizontal: 14 },
  row: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 12, borderLeftWidth: StyleSheet.hairlineWidth, borderRightWidth: StyleSheet.hairlineWidth },
  exBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 3, width: 58, height: 34, borderRadius: radius.sm + 2, borderWidth: 1 },
});
