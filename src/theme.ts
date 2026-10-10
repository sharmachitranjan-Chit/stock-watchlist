import { useColorScheme } from 'react-native';

const light = {
  bg: '#F7F3E8', card: '#FFFDF7', ink: '#2B2A26', muted: '#7A776C', line: '#E6E0CF',
  moss: '#4A5D45', onMoss: '#FFFFFF', amber: '#C9973C', up: '#2E7D4F', down: '#B3412E',
  upBg: 'rgba(46,125,79,0.12)', downBg: 'rgba(179,65,46,0.12)', scrim: 'rgba(0,0,0,0.35)',
};

const dark: typeof light = {
  bg: '#1C1E1A', card: '#252822', ink: '#ECE8DC', muted: '#9C998E', line: '#34382F',
  moss: '#8FA886', onMoss: '#1C1E1A', amber: '#D9A94E', up: '#5DBB83', down: '#E0735F',
  upBg: 'rgba(93,187,131,0.15)', downBg: 'rgba(224,115,95,0.15)', scrim: 'rgba(0,0,0,0.55)',
};

export type Theme = typeof light;

export function useTheme(): Theme {
  return useColorScheme() === 'dark' ? dark : light;
}

export const serif = 'serif';
