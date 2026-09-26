import { StyleSheet, Text, type TextProps } from 'react-native';

import { colors, fontSize } from '@/constants/theme';

type Variant = 'display' | 'title' | 'heading' | 'body' | 'label' | 'caption' | 'overline';
type Tone = 'default' | 'secondary' | 'muted' | 'primary' | 'danger' | 'onPrimary';

export type AppTextProps = TextProps & {
  variant?: Variant;
  tone?: Tone;
  weight?: '400' | '500' | '600' | '700' | '800';
  align?: 'left' | 'center' | 'right';
};

const toneColors: Record<Tone, string> = {
  default: colors.text,
  secondary: colors.textSecondary,
  muted: colors.textMuted,
  primary: colors.primary,
  danger: colors.danger,
  onPrimary: colors.onPrimary,
};

export function AppText({
  variant = 'body',
  tone = 'default',
  weight,
  align,
  style,
  ...props
}: AppTextProps) {
  return (
    <Text
      {...props}
      style={[
        styles[variant],
        { color: toneColors[tone] },
        weight && { fontWeight: weight },
        align && { textAlign: align },
        style,
      ]}
    />
  );
}

const styles = StyleSheet.create({
  display: { fontSize: fontSize.display, fontWeight: '800', letterSpacing: -0.5 },
  title: { fontSize: fontSize.xxl, fontWeight: '700', letterSpacing: -0.3 },
  heading: { fontSize: fontSize.lg, fontWeight: '700' },
  body: { fontSize: fontSize.md, lineHeight: 22 },
  label: { fontSize: fontSize.sm, fontWeight: '600' },
  caption: { fontSize: fontSize.xs, lineHeight: 16 },
  overline: {
    fontSize: fontSize.xs,
    fontWeight: '700',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
});
