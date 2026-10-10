import Ionicons from '@expo/vector-icons/Ionicons';
import Constants from 'expo-constants';
import { router } from 'expo-router';
import React, { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import NameDialog from '../../components/NameDialog';
import { Card, Header, IconName, SectionTitle, Segmented } from '../../components/ui';
import { useStore } from '../../store';
import { COMPANIES } from '../../symbols';
import { makeTheme, PaletteId, PALETTES, radius } from '../../theme';

export default function SettingsScreen() {
  const st = useStore();
  const { theme: t, prefs } = st;
  const [newList, setNewList] = useState(false);
  const counts = new Map<string, number>();
  (st.data?.rows || []).forEach(r => counts.set(r.list, (counts.get(r.list) || 0) + 1));

  const delList = (l: string) => {
    if (st.lists.length <= 1) { Alert.alert('Keep one list', 'You need at least one watchlist.'); return; }
    Alert.alert('Delete list', `Delete “${l}” and the ${counts.get(l) || 0} ${counts.get(l) === 1 ? 'stock' : 'stocks'} in it?`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => st.deleteList(l).then(() => st.showToast(`“${l}” deleted`)).catch(e => Alert.alert('Could not delete', e?.message)) },
    ]);
  };

  return (
    <View style={{ flex: 1, backgroundColor: t.bg }}>
      <Header title="Settings" />
      <ScrollView contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 30 }}>

        <SectionTitle>Theme</SectionTitle>
        <Card>
          <Segmented value={prefs.scheme} onChange={v => st.setPrefs({ scheme: v })}
            options={[['system', 'System'], ['light', 'Light'], ['dark', 'Dark']]} />
          <Text style={[s.hint, { color: t.muted }]}>System follows your phone's day and night setting.</Text>
        </Card>

        <SectionTitle>Colour palette</SectionTitle>
        <Card>
          <View style={s.swatches}>
            {(Object.keys(PALETTES) as PaletteId[]).map(id => {
              const pt = makeTheme(t.scheme, id, prefs.gainLoss);
              const on = prefs.palette === id;
              return (
                <Pressable key={id} onPress={() => st.setPrefs({ palette: id })} style={s.swatchWrap} accessibilityLabel={`${PALETTES[id].name} palette`}>
                  <View style={[s.swatch, { borderColor: on ? pt.primary : t.border, borderWidth: on ? 2.5 : StyleSheet.hairlineWidth }]}>
                    <View style={[s.swatchFill, { backgroundColor: pt.primary }]}>
                      {on && <Ionicons name="checkmark" size={20} color={pt.onPrimary} />}
                    </View>
                  </View>
                  <Text style={{ color: on ? t.text : t.muted, fontSize: 12, fontWeight: on ? '800' : '500', marginTop: 6 }}>{PALETTES[id].name}</Text>
                </Pressable>
              );
            })}
          </View>
        </Card>

        <SectionTitle>Gain and loss colours</SectionTitle>
        <Card>
          <Segmented value={prefs.gainLoss} onChange={v => st.setPrefs({ gainLoss: v })}
            options={[['classic', 'Green / Red'], ['colorblind', 'Blue / Orange']]} />
          <View style={{ flexDirection: 'row', gap: 10, marginTop: 12 }}>
            <View style={[s.sample, { backgroundColor: t.upSoft }]}><Text style={{ color: t.up, fontWeight: '800' }}>▲ +1.25%</Text></View>
            <View style={[s.sample, { backgroundColor: t.downSoft }]}><Text style={{ color: t.down, fontWeight: '800' }}>▼ −0.60%</Text></View>
          </View>
          <Text style={[s.hint, { color: t.muted }]}>Blue and orange are easier to tell apart for colour-blind eyes.</Text>
        </Card>

        <SectionTitle right={<Pressable onPress={() => setNewList(true)} hitSlop={8}><Text style={{ color: t.primary, fontWeight: '800', fontSize: 13.5 }}>+ New list</Text></Pressable>}>Watchlists</SectionTitle>
        <Card padded={false}>
          {st.lists.map((l, i) => (
            <View key={l} style={[s.item, { borderBottomColor: t.border, borderBottomWidth: i < st.lists.length - 1 ? StyleSheet.hairlineWidth : 0 }]}>
              <Ionicons name="list" size={19} color={t.textSoft} />
              <Text style={{ color: t.text, fontSize: 15, flex: 1 }}>{l}</Text>
              <Text style={{ color: t.muted, fontSize: 13 }}>{counts.get(l) || 0} {counts.get(l) === 1 ? 'stock' : 'stocks'}</Text>
              <Pressable onPress={() => delList(l)} hitSlop={10} accessibilityLabel={`Delete ${l}`}>
                <Ionicons name="trash-outline" size={19} color={t.down} />
              </Pressable>
            </View>
          ))}
        </Card>

        <SectionTitle>Auto refresh during market hours</SectionTitle>
        <Card>
          <Segmented value={prefs.interval} onChange={v => st.setPrefs({ interval: v })}
            options={[[30, '30 sec'], [60, '1 min'], [120, '2 min'], [300, '5 min']]} />
          <Text style={[s.hint, { color: t.muted }]}>Only while the app is open. Google updates its prices every few minutes, so faster checks won't always show new numbers.</Text>
        </Card>

        <SectionTitle>Data</SectionTitle>
        <Card padded={false}>
          <NavItem icon="cloud-done-outline" title="Price engine" value={st.error ? 'Problem' : st.cfg ? 'Connected' : 'Not set'} valueColor={st.error ? t.down : t.up} onPress={() => router.push('/connection')} divider />
          <NavItem icon="business-outline" title="Company list" value={`${COMPANIES.length.toLocaleString('en-IN')} on NSE + BSE`} />
        </Card>

        <SectionTitle>About</SectionTitle>
        <Card>
          <Text style={{ color: t.text, fontSize: 15, fontWeight: '700' }}>Watchlist {Constants.expoConfig?.version || ''}</Text>
          <Text style={{ color: t.textSoft, fontSize: 13.5, marginTop: 4, lineHeight: 19 }}>A tool made by Chitranjan Sharma. Prices from Google Finance through your own Google Sheet. For tracking only, not for trading decisions.</Text>
        </Card>
      </ScrollView>

      <NameDialog visible={newList} title="New list" placeholder="e.g. Banks, Long term" onClose={() => setNewList(false)}
        onSubmit={n => { st.createList(n); setNewList(false); st.showToast(`“${n}” created`); }} />
    </View>
  );
}

function NavItem({ icon, title, value, valueColor, onPress, divider }: { icon: IconName; title: string; value?: string; valueColor?: string; onPress?: () => void; divider?: boolean }) {
  const { theme: t } = useStore();
  return (
    <Pressable onPress={onPress} disabled={!onPress}
      style={({ pressed }) => [s.item, { backgroundColor: pressed ? t.surface2 : 'transparent', borderBottomColor: t.border, borderBottomWidth: divider ? StyleSheet.hairlineWidth : 0 }]}>
      <Ionicons name={icon} size={19} color={t.textSoft} />
      <Text style={{ color: t.text, fontSize: 15, flex: 1 }}>{title}</Text>
      {value && <Text style={{ color: valueColor || t.muted, fontSize: 13, fontWeight: valueColor ? '700' : '400' }}>{value}</Text>}
      {onPress && <Ionicons name="chevron-forward" size={18} color={t.muted} />}
    </Pressable>
  );
}

const s = StyleSheet.create({
  hint: { fontSize: 12.5, marginTop: 10, lineHeight: 18 },
  swatches: { flexDirection: 'row', justifyContent: 'space-between' },
  swatchWrap: { alignItems: 'center', width: 58 },
  swatch: { width: 50, height: 50, borderRadius: 25, padding: 4 },
  swatchFill: { flex: 1, borderRadius: 21, alignItems: 'center', justifyContent: 'center' },
  sample: { flex: 1, borderRadius: radius.sm, paddingVertical: 8, alignItems: 'center' },
  item: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 14, minHeight: 54 },
});
