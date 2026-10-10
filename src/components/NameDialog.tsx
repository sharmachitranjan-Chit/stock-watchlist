import React, { useEffect, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useTheme } from '../store';
import { radius } from '../theme';
import { PrimaryButton } from './ui';

/** Small dialog near the top of the screen, so the keyboard never covers the box being typed in. */
export default function NameDialog({ visible, title, placeholder, onClose, onSubmit }: {
  visible: boolean; title: string; placeholder: string; onClose: () => void; onSubmit: (v: string) => void;
}) {
  const t = useTheme();
  const [v, setV] = useState('');
  useEffect(() => { if (visible) setV(''); }, [visible]);
  const ok = () => { if (v.trim()) onSubmit(v.trim()); };
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose} statusBarTranslucent>
      <Pressable style={[StyleSheet.absoluteFill, { backgroundColor: t.scrim }]} onPress={onClose} />
      <View style={[s.box, { backgroundColor: t.surface, borderColor: t.border }]}>
        <Text style={{ color: t.text, fontSize: 19, fontWeight: '800' }}>{title}</Text>
        <TextInput value={v} onChangeText={setV} placeholder={placeholder} placeholderTextColor={t.muted} autoFocus maxLength={30}
          onSubmitEditing={ok} returnKeyType="done"
          style={[s.input, { color: t.text, backgroundColor: t.bg, borderColor: t.border }]} />
        <View style={{ flexDirection: 'row', gap: 10, marginTop: 14 }}>
          <View style={{ flex: 1 }}><PrimaryButton label="Cancel" tone="plain" onPress={onClose} /></View>
          <View style={{ flex: 1 }}><PrimaryButton label="Create" onPress={ok} /></View>
        </View>
      </View>
    </Modal>
  );
}

const s = StyleSheet.create({
  box: { position: 'absolute', top: 110, left: 20, right: 20, borderRadius: radius.xl, borderWidth: StyleSheet.hairlineWidth, padding: 18 },
  input: { marginTop: 14, fontSize: 16, paddingHorizontal: 14, height: 50, borderRadius: radius.md, borderWidth: StyleSheet.hairlineWidth },
});
