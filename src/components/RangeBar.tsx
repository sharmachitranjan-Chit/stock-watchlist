import React from 'react';
import { Text, View } from 'react-native';
import { num } from '../format';
import { useTheme } from '../theme';

/** Where today's price sits between a low and a high. */
export default function RangeBar({ label, lo, hi, v }: { label: string; lo: number | null; hi: number | null; v: number | null }) {
  const t = useTheme();
  if (lo == null || hi == null || v == null || hi <= lo) return null;
  const pc = Math.max(0, Math.min(100, ((v - lo) / (hi - lo)) * 100));
  return (
    <View style={{ marginTop: 16 }}>
      <Text style={{ color: t.muted, fontSize: 12, marginBottom: 7 }}>{label}</Text>
      <View style={{ height: 6, borderRadius: 3, backgroundColor: t.line, justifyContent: 'center' }}>
        <View style={{ position: 'absolute', left: `${pc}%`, marginLeft: -7, width: 14, height: 14, borderRadius: 7, backgroundColor: t.amber, borderWidth: 2, borderColor: t.card }} />
      </View>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 6 }}>
        <Text style={{ color: t.muted, fontSize: 12 }}>{num(lo)}</Text>
        <Text style={{ color: t.muted, fontSize: 12 }}>{num(hi)}</Text>
      </View>
    </View>
  );
}
