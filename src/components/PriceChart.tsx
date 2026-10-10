import React, { useState } from 'react';
import { LayoutChangeEvent, Text, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { useTheme } from '../theme';

/** Simple line of closing prices, coloured by whether the period ended up or down. */
export default function PriceChart({ values, height = 120 }: { values: number[]; height?: number }) {
  const t = useTheme();
  const [w, setW] = useState(0);
  const onLayout = (e: LayoutChangeEvent) => setW(e.nativeEvent.layout.width);

  if (values.length < 2) {
    return (
      <View style={{ height, alignItems: 'center', justifyContent: 'center' }}>
        <Text style={{ color: t.muted, fontSize: 13 }}>Not enough history yet</Text>
      </View>
    );
  }
  const mn = Math.min(...values), mx = Math.max(...values), pad = 6;
  const x = (i: number) => (i / (values.length - 1)) * w;
  const y = (v: number) => (mx === mn ? height / 2 : pad + (1 - (v - mn) / (mx - mn)) * (height - 2 * pad));
  const line = values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const col = values[values.length - 1] >= values[0] ? t.up : t.down;

  return (
    <View style={{ height }} onLayout={onLayout}>
      {w > 0 && (
        <Svg width={w} height={height}>
          <Path d={`${line} L${w},${height} L0,${height} Z`} fill={col} opacity={0.09} />
          <Path d={line} fill="none" stroke={col} strokeWidth={2} strokeLinejoin="round" />
        </Svg>
      )}
    </View>
  );
}
