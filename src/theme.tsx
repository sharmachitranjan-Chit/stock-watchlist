/**
 * Design tokens. Neutrals are shared; each palette only changes the accent.
 * Every text/background pair below keeps at least 4.5:1 contrast.
 */

export type Scheme = 'light' | 'dark';
export type PaletteId = 'ocean' | 'emerald' | 'saffron' | 'violet' | 'graphite';
export type GainLoss = 'classic' | 'colorblind';

type Accent = { name: string; light: string; dark: string; onLight: string; onDark: string };

export const PALETTES: Record<PaletteId, Accent> = {
  ocean:    { name: 'Ocean',    light: '#1D5BD8', dark: '#7FA8FF', onLight: '#FFFFFF', onDark: '#0A1020' },
  emerald:  { name: 'Emerald',  light: '#0B7A6E', dark: '#4FD8C4', onLight: '#FFFFFF', onDark: '#04211D' },
  saffron:  { name: 'Saffron',  light: '#B84A0C', dark: '#FFA45C', onLight: '#FFFFFF', onDark: '#2A1203' },
  violet:   { name: 'Violet',   light: '#6A33D1', dark: '#B9A0FF', onLight: '#FFFFFF', onDark: '#170A33' },
  graphite: { name: 'Graphite', light: '#1F2733', dark: '#E6EAF0', onLight: '#FFFFFF', onDark: '#0B0F14' },
};

const NEUTRAL = {
  light: {
    bg: '#F4F6F9', surface: '#FFFFFF', surface2: '#EDF0F4', border: '#DFE4EA',
    text: '#0E1726', textSoft: '#3A4556', muted: '#5C6676', scrim: 'rgba(8,12,20,0.45)',
  },
  dark: {
    bg: '#0B0F14', surface: '#141A21', surface2: '#1B232D', border: '#26303C',
    text: '#EAEEF3', textSoft: '#C3CBD5', muted: '#98A2B0', scrim: 'rgba(0,0,0,0.6)',
  },
};

const MOVES = {
  classic:    { light: { up: '#0B7F43', down: '#C62828' }, dark: { up: '#3FD584', down: '#FF6B6B' } },
  colorblind: { light: { up: '#1A5FD0', down: '#B54708' }, dark: { up: '#79B0FF', down: '#FFA25A' } },
};

export type Theme = {
  scheme: Scheme;
  bg: string; surface: string; surface2: string; border: string;
  text: string; textSoft: string; muted: string; scrim: string;
  primary: string; onPrimary: string; primarySoft: string;
  up: string; down: string; upSoft: string; downSoft: string; flat: string;
};

function alpha(hex: string, a: number) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

export function makeTheme(scheme: Scheme, palette: PaletteId, gl: GainLoss): Theme {
  const n = NEUTRAL[scheme];
  const p = PALETTES[palette];
  const m = MOVES[gl][scheme];
  const primary = scheme === 'light' ? p.light : p.dark;
  return {
    scheme, ...n,
    primary,
    onPrimary: scheme === 'light' ? p.onLight : p.onDark,
    primarySoft: alpha(primary, scheme === 'light' ? 0.1 : 0.16),
    up: m.up, down: m.down,
    upSoft: alpha(m.up, scheme === 'light' ? 0.1 : 0.15),
    downSoft: alpha(m.down, scheme === 'light' ? 0.1 : 0.15),
    flat: n.muted,
  };
}

export const radius = { sm: 8, md: 12, lg: 16, xl: 22, pill: 999 };
export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 };
