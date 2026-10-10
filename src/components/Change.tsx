import React from 'react';
import { Text, TextStyle } from 'react-native';
import { arrow, dirOf, num, signPct } from '../format';
import { useTheme } from '../theme';

/** ▲ 21.39 (+1.51%) in green, ▼ in red. pctOnly drops the rupee amount. */
export default function Change({ change, pct, pctOnly, style }: { change: number | null; pct: number | null; pctOnly?: boolean; style?: TextStyle }) {
  const t = useTheme();
  const d = dirOf(change);
  const color = d === 'up' ? t.up : d === 'down' ? t.down : t.muted;
  if (change == null) return <Text style={[{ color: t.muted, fontVariant: ['tabular-nums'] }, style]}>—</Text>;
  const body = pctOnly ? signPct(pct) : `${num(Math.abs(change))} (${signPct(pct)})`;
  return <Text style={[{ color, fontVariant: ['tabular-nums'] }, style]} numberOfLines={1}>{arrow(change)} {body}</Text>;
}
