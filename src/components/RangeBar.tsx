import React from 'react';
import { Text, View } from 'react-native';
import { num } from '../format';
import { useTheme } from '../store';

/** Where the current price sits between a low and a high. */
export default function RangeBar({ label, lo, hi, v }: { label: string; lo: number | null; hi: number | null; v: number | null }) {
  const t = useTheme();
  if (lo == null || hi == null || v == null || hi <= lo) return null;
  const pc = Math.max(0, Math.min(100, ((v - lo) / (hi - lo)) * 100));
  return (
    <View style={{ marginTop: 14 }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 }}>
        <Text style={{ color: t.muted, fontSize: 12.5, fontWeight: '600' }}>{label}</Text>
        <Text style={{ color: t.muted, fontSize: 12.5 }}>{Math.round(pc)}% of range</Text>
      </View>
      <View style={{ height: 6, borderRadius: 3, backgroundColor: t.surface2, justifyContent: 'center' }}>
        <View style={{ position: 'absolute', left: 0, width: `${pc}%`, height: 6, borderRadius: 3, backgroundColor: t.primarySoft }} />
        <View style={{ position: 'absolute', left: `${pc}%`, marginLeft: -7, width: 14, height: 14, borderRadius: 7, backgroundColor: t.primary, borderWidth: 3, borderColor: t.surface }} />
      </View>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 6 }}>
        <Text style={{ color: t.textSoft, fontSize: 12.5, fontVariant: ['tabular-nums'] }}>{num(lo)}</Text>
        <Text style={{ color: t.textSoft, fontSize: 12.5, fontVariant: ['tabular-nums'] }}>{num(hi)}</Text>
      </View>
    </View>
  );
}
