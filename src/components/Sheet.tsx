import React from 'react';
import { KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../theme';

type Props = { visible: boolean; onClose: () => void; children: React.ReactNode; dismissable?: boolean };

/** Bottom sheet that slides up over the list. Tap outside to close. */
export default function Sheet({ visible, onClose, children, dismissable = true }: Props) {
  const t = useTheme();
  const inset = useSafeAreaInsets();
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={dismissable ? onClose : () => {}} statusBarTranslucent>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <Pressable style={[StyleSheet.absoluteFill, { backgroundColor: t.scrim }]} onPress={dismissable ? onClose : undefined} />
        <View style={{ flex: 1 }} pointerEvents="box-none" />
        <View style={[s.sheet, { backgroundColor: t.bg, paddingBottom: inset.bottom + 16 }]}>
          <View style={[s.grab, { backgroundColor: t.line }]} />
          <ScrollView keyboardShouldPersistTaps="handled" bounces={false}>{children}</ScrollView>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const s = StyleSheet.create({
  sheet: { maxHeight: '90%', borderTopLeftRadius: 22, borderTopRightRadius: 22, paddingHorizontal: 16, paddingTop: 10 },
  grab: { width: 40, height: 4, borderRadius: 2, alignSelf: 'center', marginBottom: 12 },
});
