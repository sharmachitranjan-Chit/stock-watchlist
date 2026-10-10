import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput } from 'react-native';
import { api, Config } from '../api';
import { serif, useTheme } from '../theme';
import Sheet from './Sheet';

type Props = { visible: boolean; current: Config | null; onClose: () => void; onSave: (c: Config) => void };

/** Where the app is told which price engine to talk to. Shown on first launch. */
export default function SettingsSheet({ visible, current, onClose, onSave }: Props) {
  const t = useTheme();
  const [url, setUrl] = useState('');
  const [key, setKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => { if (visible) { setUrl(current?.url || ''); setKey(current?.key || ''); setErr(''); } }, [visible, current]);

  async function test() {
    const c = { url: url.trim(), key: key.trim() };
    if (!/^https:\/\/script\.google\.com\/.+\/exec$/.test(c.url)) { setErr('The link should start with https://script.google.com/ and end with /exec'); return; }
    if (!c.key) { setErr('Paste the app key from the Settings tab of your sheet'); return; }
    setBusy(true); setErr('');
    try {
      await api.data(c);
      onSave(c);
    } catch (e: any) {
      setErr(e?.message || String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Sheet visible={visible} onClose={onClose} dismissable={!!current}>
      <Text style={[s.title, { color: t.ink }]}>Connect price engine</Text>
      <Text style={{ color: t.muted, fontSize: 13, lineHeight: 19, marginTop: 4 }}>
        Paste the web app link from your Google Apps Script deployment, and the app key from the Settings tab of your Stock Watchlist sheet.
      </Text>

      <Text style={[s.label, { color: t.muted }]}>Engine link</Text>
      <TextInput value={url} onChangeText={setUrl} placeholder="https://script.google.com/macros/s/…/exec" placeholderTextColor={t.muted}
        autoCapitalize="none" autoCorrect={false} keyboardType="url" style={[s.input, { color: t.ink, backgroundColor: t.card, borderColor: t.line }]} />

      <Text style={[s.label, { color: t.muted }]}>App key</Text>
      <TextInput value={key} onChangeText={setKey} placeholder="From Settings!B2 in the sheet" placeholderTextColor={t.muted}
        autoCapitalize="none" autoCorrect={false} style={[s.input, { color: t.ink, backgroundColor: t.card, borderColor: t.line }]} />

      {!!err && <Text style={{ color: t.down, fontSize: 13, marginTop: 10 }}>{err}</Text>}

      <Pressable onPress={test} disabled={busy} style={[s.primary, { backgroundColor: t.moss, opacity: busy ? 0.7 : 1 }]}>
        {busy ? <ActivityIndicator color={t.onMoss} /> : <Text style={{ color: t.onMoss, fontWeight: '700', fontSize: 15 }}>Test and save</Text>}
      </Pressable>
    </Sheet>
  );
}

const s = StyleSheet.create({
  title: { fontFamily: serif, fontSize: 24 },
  label: { fontSize: 12, marginTop: 16, marginBottom: 6 },
  input: { fontSize: 15, paddingHorizontal: 14, paddingVertical: 12, borderRadius: 12, borderWidth: 1 },
  primary: { marginTop: 18, paddingVertical: 15, borderRadius: 12, alignItems: 'center' },
});
