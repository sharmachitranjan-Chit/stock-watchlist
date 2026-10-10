import { router } from 'expo-router';
import React, { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { api } from '../api';
import { Card, Header, IconButton, PrimaryButton, SectionTitle } from '../components/ui';
import { DEFAULT_CONFIG } from '../defaults';
import { ist } from '../format';
import { useStore } from '../store';
import { radius } from '../theme';

export default function ConnectionScreen() {
  const st = useStore();
  const t = st.theme;
  const [url, setUrl] = useState(st.cfg?.url || '');
  const [key, setKey] = useState(st.cfg?.key || '');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  useEffect(() => { if (st.cfg) { setUrl(u => u || st.cfg!.url); setKey(k => k || st.cfg!.key); } }, [st.cfg]);

  async function save() {
    const c = { url: url.trim(), key: key.trim() };
    if (!/^https:\/\/script\.google\.com\/.+\/exec$/.test(c.url)) { setMsg({ ok: false, text: 'The link should start with https://script.google.com/ and end with /exec' }); return; }
    if (!c.key) { setMsg({ ok: false, text: 'Paste the app key from the Settings tab of your sheet.' }); return; }
    setBusy(true); setMsg(null);
    try {
      const d = await api.data(c);
      await st.setCfg(c);
      setMsg({ ok: true, text: `Connected · ${d.rows.length} stocks found` });
    } catch (e: any) {
      setMsg({ ok: false, text: e?.message || String(e) });
    } finally { setBusy(false); }
  }

  async function reset() {
    setUrl(DEFAULT_CONFIG.url); setKey(DEFAULT_CONFIG.key);
    await st.setCfg(null);
    setMsg({ ok: true, text: 'Using the built-in engine again' });
  }

  return (
    <View style={{ flex: 1, backgroundColor: t.bg }}>
      <Header title="Price engine" left={<IconButton name="arrow-back" label="Back" onPress={() => router.back()} />} />
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 40 }} keyboardShouldPersistTaps="handled">
          <Card>
            <Text style={{ color: t.text, fontWeight: '700', fontSize: 15 }}>{st.error ? 'Not reachable right now' : 'Connected'}</Text>
            <Text style={{ color: t.muted, fontSize: 13, marginTop: 4, lineHeight: 19 }}>
              {st.error || (st.data ? `Last answer at ${ist(new Date(st.data.at)).hm} IST · ${st.data.rows.length} stocks in your sheet` : 'Waiting for the first answer')}
            </Text>
          </Card>

          <SectionTitle>Web app link</SectionTitle>
          <TextInput value={url} onChangeText={setUrl} autoCapitalize="none" autoCorrect={false} keyboardType="url" multiline
            placeholder="https://script.google.com/macros/s/…/exec" placeholderTextColor={t.muted}
            style={[s.input, { color: t.text, backgroundColor: t.surface, borderColor: t.border, minHeight: 74 }]} />

          <SectionTitle>App key</SectionTitle>
          <TextInput value={key} onChangeText={setKey} autoCapitalize="none" autoCorrect={false} secureTextEntry
            placeholder="Settings tab, cell B2" placeholderTextColor={t.muted}
            style={[s.input, { color: t.text, backgroundColor: t.surface, borderColor: t.border }]} />

          {msg && <Text style={{ color: msg.ok ? t.up : t.down, fontSize: 13.5, marginTop: 12, fontWeight: '600' }}>{msg.text}</Text>}

          <View style={{ marginTop: 18, gap: 10 }}>
            <PrimaryButton label="Test and save" icon="checkmark-circle-outline" busy={busy} onPress={save} />
            <PrimaryButton label="Use built-in engine" tone="plain" onPress={reset} />
          </View>
          <Text style={{ color: t.muted, fontSize: 12.5, marginTop: 16, lineHeight: 18 }}>
            You only need this if you redeploy the Apps Script or change the key in the sheet.
          </Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const s = StyleSheet.create({
  input: { fontSize: 15, paddingHorizontal: 14, paddingVertical: 13, borderRadius: radius.md, borderWidth: StyleSheet.hairlineWidth, textAlignVertical: 'top' },
});
