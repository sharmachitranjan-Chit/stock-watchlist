import Ionicons from '@expo/vector-icons/Ionicons';
import React from 'react';
import { ActivityIndicator, Pressable, StyleProp, StyleSheet, Text, TextStyle, View, ViewStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { arrow, dirOf, market, num, signPct } from '../format';
import { useStore, useTheme } from '../store';
import { radius } from '../theme';

export type IconName = React.ComponentProps<typeof Ionicons>['name'];

/** Top bar used by every screen. Title on the left, actions on the right, never overlapping. */
export function Header({ title, subtitle, left, right }: { title: string; subtitle?: React.ReactNode; left?: React.ReactNode; right?: React.ReactNode }) {
  const t = useTheme();
  const inset = useSafeAreaInsets();
  return (
    <View style={{ paddingTop: inset.top + 8, paddingHorizontal: 16, paddingBottom: 10, backgroundColor: t.bg }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', minHeight: 44 }}>
        {left}
        <Text style={[s.title, { color: t.text, marginLeft: left ? 4 : 0 }]} numberOfLines={1}>{title}</Text>
        <View style={{ flexDirection: 'row', gap: 6, marginLeft: 8 }}>{right}</View>
      </View>
      {subtitle ? <View style={{ marginTop: 6 }}>{subtitle}</View> : null}
    </View>
  );
}

export function IconButton({ name, onPress, busy, label, tint }: { name: IconName; onPress: () => void; busy?: boolean; label: string; tint?: string }) {
  const t = useTheme();
  return (
    <Pressable onPress={onPress} accessibilityLabel={label} hitSlop={6}
      style={({ pressed }) => [s.iconBtn, { backgroundColor: pressed ? t.surface2 : t.surface, borderColor: t.border }]}>
      {busy ? <ActivityIndicator size="small" color={t.primary} /> : <Ionicons name={name} size={20} color={tint || t.text} />}
    </Pressable>
  );
}

export function Card({ children, style, padded = true }: { children: React.ReactNode; style?: StyleProp<ViewStyle>; padded?: boolean }) {
  const t = useTheme();
  return <View style={[{ backgroundColor: t.surface, borderColor: t.border, borderWidth: StyleSheet.hairlineWidth, borderRadius: radius.lg, padding: padded ? 14 : 0, overflow: 'hidden' }, style]}>{children}</View>;
}

export function SectionTitle({ children, right }: { children: string; right?: React.ReactNode }) {
  const t = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 22, marginBottom: 8, paddingHorizontal: 4 }}>
      <Text style={{ color: t.muted, fontSize: 12.5, fontWeight: '700', letterSpacing: 0.6, textTransform: 'uppercase' }}>{children}</Text>
      {right}
    </View>
  );
}

export function Chip({ label, active, onPress, icon }: { label: string; active?: boolean; onPress: () => void; icon?: IconName }) {
  const t = useTheme();
  return (
    <Pressable onPress={onPress}
      style={({ pressed }) => [s.chip, {
        backgroundColor: active ? t.primary : pressed ? t.surface2 : t.surface,
        borderColor: active ? t.primary : t.border,
      }]}>
      {icon && <Ionicons name={icon} size={15} color={active ? t.onPrimary : t.textSoft} style={{ marginRight: 4 }} />}
      <Text style={{ color: active ? t.onPrimary : t.textSoft, fontSize: 13.5, fontWeight: active ? '700' : '500' }}>{label}</Text>
    </Pressable>
  );
}

export function Segmented<T extends string | number>({ value, options, onChange }: { value: T; options: [T, string][]; onChange: (v: T) => void }) {
  const t = useTheme();
  return (
    <View style={[s.seg, { backgroundColor: t.surface2 }]}>
      {options.map(([v, label]) => {
        const on = v === value;
        return (
          <Pressable key={String(v)} onPress={() => onChange(v)} style={[s.segItem, on && { backgroundColor: t.surface, borderColor: t.border, borderWidth: StyleSheet.hairlineWidth }]}>
            <Text style={{ color: on ? t.text : t.muted, fontWeight: on ? '700' : '500', fontSize: 13.5 }}>{label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/** ▲ 2.92  +1.25% on a soft tinted background. */
export function ChangePill({ change, pct, compact, style }: { change: number | null; pct: number | null; compact?: boolean; style?: StyleProp<ViewStyle> }) {
  const t = useTheme();
  const d = dirOf(change);
  const fg = d === 'up' ? t.up : d === 'down' ? t.down : t.muted;
  const bg = d === 'up' ? t.upSoft : d === 'down' ? t.downSoft : t.surface2;
  return (
    <View style={[{ backgroundColor: bg, borderRadius: radius.sm, paddingHorizontal: 7, paddingVertical: 3, alignSelf: 'flex-end' }, style]}>
      <Text style={{ color: fg, fontSize: compact ? 12 : 12.5, fontWeight: '700', fontVariant: ['tabular-nums'] }} numberOfLines={1}>
        {change == null ? '—' : compact ? `${arrow(change)} ${signPct(pct)}` : `${arrow(change)} ${num(Math.abs(change))}  ${signPct(pct)}`}
      </Text>
    </View>
  );
}

export function ChangeText({ change, pct, style }: { change: number | null; pct: number | null; style?: StyleProp<TextStyle> }) {
  const t = useTheme();
  const d = dirOf(change);
  const fg = d === 'up' ? t.up : d === 'down' ? t.down : t.muted;
  if (change == null) return <Text style={[{ color: t.muted }, style]}>—</Text>;
  return <Text style={[{ color: fg, fontWeight: '700', fontVariant: ['tabular-nums'] }, style]}>{arrow(change)} {num(Math.abs(change))} ({signPct(pct)})</Text>;
}

export function ExBadge({ ex }: { ex: string }) {
  const t = useTheme();
  return (
    <View style={{ backgroundColor: t.surface2, borderRadius: 5, paddingHorizontal: 5, paddingVertical: 1.5 }}>
      <Text style={{ color: t.muted, fontSize: 10, fontWeight: '800', letterSpacing: 0.4 }}>{ex}</Text>
    </View>
  );
}

export function StatusPill() {
  const { data } = useStore();
  const t = useTheme();
  const m = market(data);
  const color = m.k === 'live' ? t.up : m.k === 'pre' ? t.primary : t.muted;
  return (
    <View style={[s.pill, { backgroundColor: m.k === 'live' ? t.upSoft : t.surface2 }]}>
      <View style={{ width: 7, height: 7, borderRadius: 4, backgroundColor: color }} />
      <Text style={{ color, fontSize: 12, fontWeight: '700' }}>{m.label}</Text>
    </View>
  );
}

export function PrimaryButton({ label, onPress, busy, icon, tone = 'primary' }: { label: string; onPress: () => void; busy?: boolean; icon?: IconName; tone?: 'primary' | 'danger' | 'plain' }) {
  const t = useTheme();
  const bg = tone === 'primary' ? t.primary : t.surface;
  const fg = tone === 'primary' ? t.onPrimary : tone === 'danger' ? t.down : t.text;
  return (
    <Pressable onPress={onPress} disabled={busy}
      style={({ pressed }) => [s.btn, { backgroundColor: bg, borderColor: tone === 'primary' ? bg : t.border, opacity: pressed || busy ? 0.8 : 1 }]}>
      {busy ? <ActivityIndicator color={fg} /> : (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          {icon && <Ionicons name={icon} size={18} color={fg} />}
          <Text style={{ color: fg, fontWeight: '700', fontSize: 15 }}>{label}</Text>
        </View>
      )}
    </Pressable>
  );
}

/** Small message at the bottom of the screen, e.g. "TCS added to My list". */
export function ToastHost() {
  const { toast } = useStore();
  const t = useTheme();
  const inset = useSafeAreaInsets();
  if (!toast) return null;
  return (
    <View pointerEvents="none" style={{ position: 'absolute', left: 16, right: 16, bottom: inset.bottom + 74, alignItems: 'center' }}>
      <View style={{ backgroundColor: t.text, paddingHorizontal: 16, paddingVertical: 11, borderRadius: radius.md, maxWidth: 420 }}>
        <Text style={{ color: t.bg, fontSize: 13.5, fontWeight: '600', textAlign: 'center' }}>{toast}</Text>
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  title: { flex: 1, fontSize: 26, fontWeight: '800', letterSpacing: -0.4 },
  iconBtn: { width: 40, height: 40, borderRadius: radius.md, borderWidth: StyleSheet.hairlineWidth, alignItems: 'center', justifyContent: 'center' },
  chip: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, height: 34, borderRadius: radius.pill, borderWidth: StyleSheet.hairlineWidth },
  seg: { flexDirection: 'row', borderRadius: radius.md, padding: 3 },
  segItem: { flex: 1, alignItems: 'center', justifyContent: 'center', height: 34, borderRadius: radius.sm + 1 },
  pill: { flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start', gap: 6, paddingHorizontal: 10, paddingVertical: 4, borderRadius: radius.pill },
  btn: { height: 50, borderRadius: radius.md, borderWidth: StyleSheet.hairlineWidth, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 18 },
});
