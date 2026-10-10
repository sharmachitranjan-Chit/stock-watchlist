import React, { useState } from 'react';
import { LayoutChangeEvent, Text, View } from 'react-native';
import Svg, { Defs, Line, LinearGradient, Path, Stop } from 'react-native-svg';
import { num } from '../format';
import { useTheme } from '../store';

/** Line of daily closes with the period's high and low marked. */
export default function PriceChart({ values, height = 170 }: { values: number[]; height?: number }) {
  const t = useTheme();
  const [w, setW] = useState(0);
  const onLayout = (e: LayoutChangeEvent) => setW(e.nativeEvent.layout.width);

  if (values.length < 2) {
    return (
      <View style={{ height, alignItems: 'center', justifyContent: 'center' }}>
        <Text style={{ color: t.muted, fontSize: 13 }}>Not enough history for this period</Text>
      </View>
    );
  }
  const mn = Math.min(...values), mx = Math.max(...values), padT = 18, padB = 18;
  const x = (i: number) => (i / (values.length - 1)) * w;
  const y = (v: number) => (mx === mn ? height / 2 : padT + (1 - (v - mn) / (mx - mn)) * (height - padT - padB));
  const line = values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const col = values[values.length - 1] >= values[0] ? t.up : t.down;
  const baseY = y(values[0]);

  return (
    <View style={{ height }} onLayout={onLayout}>
      {w > 0 && (
        <Svg width={w} height={height}>
          <Defs>
            <LinearGradient id="fill" x1="0" y1="0" x2="0" y2="1">
              <Stop offset="0" stopColor={col} stopOpacity={0.22} />
              <Stop offset="1" stopColor={col} stopOpacity={0} />
            </LinearGradient>
          </Defs>
          <Line x1={0} x2={w} y1={baseY} y2={baseY} stroke={t.border} strokeWidth={1} strokeDasharray="4 4" />
          <Path d={`${line} L${w},${height} L0,${height} Z`} fill="url(#fill)" />
          <Path d={line} fill="none" stroke={col} strokeWidth={2.2} strokeLinejoin="round" strokeLinecap="round" />
        </Svg>
      )}
      <Text style={{ position: 'absolute', top: 0, right: 0, color: t.muted, fontSize: 11, fontVariant: ['tabular-nums'] }}>H {num(mx)}</Text>
      <Text style={{ position: 'absolute', bottom: 0, right: 0, color: t.muted, fontSize: 11, fontVariant: ['tabular-nums'] }}>L {num(mn)}</Text>
    </View>
  );
}
