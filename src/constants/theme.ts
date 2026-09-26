import { Platform } from 'react-native';

export const colors = {
  background: '#0E0F13',
  surface: '#171920',
  surfaceRaised: '#20232C',
  surfacePressed: '#2A2E39',
  border: '#2C303B',
  borderStrong: '#3A3F4C',

  text: '#F2F3F5',
  textSecondary: '#A4A9B6',
  textMuted: '#6E7382',

  primary: '#8B5CF6',
  primaryPressed: '#7C4DEB',
  primarySoft: 'rgba(139, 92, 246, 0.16)',
  onPrimary: '#FFFFFF',

  danger: '#F87171',
  dangerSoft: 'rgba(248, 113, 113, 0.14)',
  success: '#34D399',
  warning: '#FBBF24',
  star: '#FBBF24',
  favorite: '#F472B6',

  overlay: 'rgba(5, 6, 9, 0.72)',
} as const;

export const spacing = {
  xxs: 2,
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  xxxl: 48,
} as const;

export const radius = {
  sm: 6,
  md: 10,
  lg: 14,
  xl: 20,
  pill: 999,
} as const;

export const fontSize = {
  xs: 12,
  sm: 14,
  md: 16,
  lg: 18,
  xl: 22,
  xxl: 28,
  display: 34,
} as const;

export const fonts = {
  mono: Platform.select({ ios: 'Menlo', android: 'monospace', default: 'monospace' }),
};

/** Box-art proportions (width / height) used for covers everywhere. */
export const COVER_ASPECT_RATIO = 0.72;

export const MAX_CONTENT_WIDTH = 720;
