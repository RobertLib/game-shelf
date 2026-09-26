import Ionicons from '@expo/vector-icons/Ionicons';
import type { ComponentProps } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View, type PressableProps } from 'react-native';

import { colors, fontSize, radius, spacing } from '@/constants/theme';

import { AppText } from './app-text';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

export type ButtonProps = Omit<PressableProps, 'children' | 'style'> & {
  title: string;
  variant?: Variant;
  size?: 'md' | 'sm';
  icon?: ComponentProps<typeof Ionicons>['name'];
  loading?: boolean;
  fullWidth?: boolean;
};

const variantStyles: Record<
  Variant,
  { background: string; pressed: string; text: string; border?: string }
> = {
  primary: { background: colors.primary, pressed: colors.primaryPressed, text: colors.onPrimary },
  secondary: {
    background: colors.surfaceRaised,
    pressed: colors.surfacePressed,
    text: colors.text,
    border: colors.border,
  },
  ghost: { background: 'transparent', pressed: colors.surfaceRaised, text: colors.primary },
  danger: {
    background: colors.dangerSoft,
    pressed: 'rgba(248, 113, 113, 0.24)',
    text: colors.danger,
  },
};

export function Button({
  title,
  variant = 'primary',
  size = 'md',
  icon,
  loading = false,
  fullWidth = false,
  disabled,
  ...props
}: ButtonProps) {
  const palette = variantStyles[variant];
  const isDisabled = disabled || loading;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      disabled={isDisabled}
      {...props}
      style={({ pressed }) => [
        styles.base,
        size === 'sm' ? styles.sm : styles.md,
        {
          backgroundColor: pressed ? palette.pressed : palette.background,
          borderColor: palette.border ?? 'transparent',
        },
        fullWidth && styles.fullWidth,
        isDisabled && styles.disabled,
      ]}>
      {loading ? (
        <ActivityIndicator color={palette.text} size="small" />
      ) : (
        <View style={styles.content}>
          {icon && <Ionicons name={icon} size={size === 'sm' ? 16 : 18} color={palette.text} />}
          <AppText
            style={{ color: palette.text, fontSize: size === 'sm' ? fontSize.sm : fontSize.md }}
            weight="700"
            numberOfLines={1}>
            {title}
          </AppText>
        </View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    borderRadius: radius.md,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
  },
  md: { minHeight: 50, paddingHorizontal: spacing.xl },
  sm: { minHeight: 36, paddingHorizontal: spacing.md },
  fullWidth: { alignSelf: 'stretch' },
  content: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  disabled: { opacity: 0.55 },
});
