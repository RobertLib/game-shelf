import Ionicons from '@expo/vector-icons/Ionicons';
import type { ComponentProps } from 'react';
import { Pressable, StyleSheet, View, type PressableProps } from 'react-native';

import { colors, radius } from '@/constants/theme';

import { AppText } from './app-text';

type IconButtonProps = Omit<PressableProps, 'style' | 'children'> & {
  icon: ComponentProps<typeof Ionicons>['name'];
  accessibilityLabel: string;
  color?: string;
  size?: number;
  variant?: 'plain' | 'filled';
  badge?: number;
};

export function IconButton({
  icon,
  color = colors.text,
  size = 22,
  variant = 'plain',
  badge,
  ...props
}: IconButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      hitSlop={6}
      {...props}
      style={({ pressed }) => [
        styles.base,
        variant === 'filled' && styles.filled,
        pressed && styles.pressed,
      ]}>
      <Ionicons name={icon} size={size} color={color} />
      {badge ? (
        <View style={styles.badge}>
          <AppText variant="caption" weight="800" tone="onPrimary" style={styles.badgeText}>
            {badge > 99 ? '99+' : badge}
          </AppText>
        </View>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    width: 40,
    height: 40,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  filled: {
    backgroundColor: colors.surfaceRaised,
    borderWidth: 1,
    borderColor: colors.border,
  },
  pressed: { opacity: 0.6 },
  badge: {
    position: 'absolute',
    top: -4,
    right: -4,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    paddingHorizontal: 4,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { fontSize: 10, lineHeight: 12 },
});
