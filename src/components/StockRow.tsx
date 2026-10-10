import Ionicons from '@expo/vector-icons/Ionicons';
import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Quote } from '../api';
import { num, parts } from '../format';
import { quoteKey, useStore } from '../store';
import { companyFor, labelFor } from '../symbols';
import { ChangePill, ExBadge } from './ui';

type Props = {
  q: Quote;
  onPress?: () => void;
  edit?: { up: () => void; down: () => void; remove: () => void; first: boolean; last: boolean };
  divider?: boolean;
  showList?: boolean;
};

export default function StockRow({ q, onPress, edit, divider = true, showList }: Props) {
  const { theme: t, flash } = useStore();
  const p = parts(q.ticker);
  const c = companyFor(q.ticker);
  const f = flash[quoteKey(q)];
  const sub = c?.short || q.name || '';
  return (
    <Pressable onPress={edit ? undefined : onPress} disabled={!!edit}
      style={({ pressed }) => [s.row, { backgroundColor: pressed ? t.surface2 : 'transparent', borderBottomColor: t.border, borderBottomWidth: divider ? StyleSheet.hairlineWidth : 0 }]}>
      <View style={{ flex: 1, paddingRight: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
          <Text style={{ color: t.text, fontWeight: '700', fontSize: 15.5 }} numberOfLines={1}>{labelFor(q.ticker, q.name)}</Text>
          <ExBadge ex={p.ex} />
        </View>
        <Text style={{ color: t.muted, fontSize: 12.5, marginTop: 3 }} numberOfLines={1}>
          {showList ? `${q.list} · ` : ''}{sub}
        </Text>
      </View>
      {edit ? (
        <View style={{ flexDirection: 'row', gap: 6 }}>
          <EditBtn name="chevron-up" onPress={edit.up} disabled={edit.first} />
          <EditBtn name="chevron-down" onPress={edit.down} disabled={edit.last} />
          <EditBtn name="trash-outline" onPress={edit.remove} danger />
        </View>
      ) : (
        <View style={{ alignItems: 'flex-end' }}>
          <Text style={[s.price, { color: t.text, backgroundColor: f ? (f === 'up' ? t.upSoft : t.downSoft) : 'transparent' }]}>{num(q.price)}</Text>
          <ChangePill change={q.change} pct={q.changepct} style={{ marginTop: 4 }} />
        </View>
      )}
    </Pressable>
  );
}

function EditBtn({ name, onPress, disabled, danger }: { name: React.ComponentProps<typeof Ionicons>['name']; onPress: () => void; disabled?: boolean; danger?: boolean }) {
  const { theme: t } = useStore();
  return (
    <Pressable onPress={onPress} disabled={disabled} hitSlop={4}
      style={({ pressed }) => [s.edit, { borderColor: t.border, backgroundColor: pressed ? t.surface2 : t.surface, opacity: disabled ? 0.35 : 1 }]}>
      <Ionicons name={name} size={18} color={danger ? t.down : t.text} />
    </Pressable>
  );
}

const s = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 13 },
  price: { fontSize: 16.5, fontWeight: '700', fontVariant: ['tabular-nums'], paddingHorizontal: 4, borderRadius: 6, overflow: 'hidden' },
  edit: { width: 38, height: 38, borderRadius: 10, borderWidth: StyleSheet.hairlineWidth, alignItems: 'center', justifyContent: 'center' },
});
