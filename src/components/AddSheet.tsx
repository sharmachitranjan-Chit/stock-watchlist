import React, { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { POPULAR } from '../popular';
import { serif, useTheme } from '../theme';
import Sheet from './Sheet';

type Props = {
  visible: boolean;
  listName: string;
  onClose: () => void;
  onAdd: (ticker: string) => Promise<void>;
};

export default function AddSheet({ visible, listName, onClose, onAdd }: Props) {
  const t = useTheme();
  const [ex, setEx] = useState<'NSE' | 'BOM'>('NSE');
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => { if (visible) { setText(''); setErr(''); setBusy(false); } }, [visible]);

  const hits = useMemo(() => {
    const q = text.trim().toUpperCase();
    if (q.length < 2) return [];
    return POPULAR.filter(p => (ex === 'NSE' || p[2]) && (p[0].startsWith(q) || p[1].toUpperCase().includes(q) || p[2].startsWith(q))).slice(0, 6);
  }, [text, ex]);

  async function submit(code?: string) {
    const raw = (code ?? text).trim().toUpperCase();
    if (!raw) { setErr('Enter a symbol'); return; }
    const ticker = raw.includes(':') ? raw : `${ex}:${raw}`;
    setBusy(true); setErr('');
    try {
      await onAdd(ticker);
    } catch (e: any) {
      setErr(e?.message || String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Sheet visible={visible} onClose={onClose}>
      <Text style={[s.title, { color: t.ink }]}>Add stock</Text>
      <Text style={{ color: t.muted, fontSize: 13 }}>Adding to “{listName}”</Text>

      <View style={[s.seg, { borderColor: t.line }]}>
        {(['NSE', 'BOM'] as const).map(k => (
          <Pressable key={k} onPress={() => setEx(k)} style={[s.segBtn, { backgroundColor: ex === k ? t.moss : t.card }]}>
            <Text style={{ color: ex === k ? t.onMoss : t.ink, fontWeight: '600' }}>{k === 'NSE' ? 'NSE' : 'BSE'}</Text>
          </Pressable>
        ))}
      </View>

      <TextInput
        value={text}
        onChangeText={v => { setText(v); setErr(''); }}
        placeholder={ex === 'NSE' ? 'Symbol, e.g. RELIANCE' : 'Scrip code, e.g. 500325'}
        placeholderTextColor={t.muted}
        autoCapitalize="characters"
        autoCorrect={false}
        keyboardType={ex === 'BOM' ? 'number-pad' : 'default'}
        returnKeyType="done"
        onSubmitEditing={() => submit()}
        style={[s.input, { color: t.ink, backgroundColor: t.card, borderColor: t.line }]}
      />

      {hits.length > 0 && (
        <View style={[s.sugg, { backgroundColor: t.card, borderColor: t.line }]}>
          {hits.map((h, i) => {
            const code = ex === 'NSE' ? h[0] : h[2];
            return (
              <Pressable key={code} onPress={() => submit(code)} disabled={busy}
                style={[s.suggRow, { borderBottomColor: t.line, borderBottomWidth: i < hits.length - 1 ? 1 : 0 }]}>
                <Text style={{ color: t.ink, fontWeight: '700' }}>{code} <Text style={{ color: t.muted, fontWeight: '400' }}>{h[1]}</Text></Text>
                <Text style={{ color: t.muted, fontSize: 12 }}>{ex === 'NSE' ? 'NSE' : 'BSE'}</Text>
              </Pressable>
            );
          })}
        </View>
      )}

      {!!err && <Text style={{ color: t.down, fontSize: 13, marginTop: 8 }}>{err}</Text>}

      <Pressable onPress={() => submit()} disabled={busy} style={[s.primary, { backgroundColor: t.moss, opacity: busy ? 0.7 : 1 }]}>
        {busy ? (
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <ActivityIndicator color={t.onMoss} />
            <Text style={{ color: t.onMoss, fontWeight: '700', fontSize: 15 }}>Checking with Google Finance…</Text>
          </View>
        ) : (
          <Text style={{ color: t.onMoss, fontWeight: '700', fontSize: 15 }}>Add to watchlist</Text>
        )}
      </Pressable>

      <Text style={{ color: t.muted, fontSize: 12, marginTop: 10, lineHeight: 18 }}>
        NSE: use the trading symbol (TCS, SBIN, BANKBARODA).{'\n'}BSE: use the 6-digit scrip code (500325 for Reliance).
      </Text>
    </Sheet>
  );
}

const s = StyleSheet.create({
  title: { fontFamily: serif, fontSize: 24 },
  seg: { flexDirection: 'row', alignSelf: 'flex-start', borderWidth: 1, borderRadius: 10, overflow: 'hidden', marginVertical: 12 },
  segBtn: { paddingVertical: 8, paddingHorizontal: 20 },
  input: { fontSize: 17, paddingHorizontal: 14, paddingVertical: 12, borderRadius: 12, borderWidth: 1 },
  sugg: { marginTop: 8, borderWidth: 1, borderRadius: 12, overflow: 'hidden' },
  suggRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 12 },
  primary: { marginTop: 14, paddingVertical: 15, borderRadius: 12, alignItems: 'center' },
});
