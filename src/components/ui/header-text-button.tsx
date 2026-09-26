import { Platform, Pressable, StyleSheet } from 'react-native';

import { spacing } from '@/constants/theme';

import { AppText } from './app-text';

type HeaderTextButtonProps = {
  title: string;
  onPress: () => void;
  disabled?: boolean;
};

export function HeaderTextButton({ title, onPress, disabled }: HeaderTextButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled}
      hitSlop={8}
      style={({ pressed }) => [styles.button, pressed && styles.pressed]}>
      <AppText weight="700" tone={disabled ? 'muted' : 'primary'}>
        {title}
      </AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  // Native headers inset their items already; the web header does not.
  button: Platform.OS === 'web' ? { paddingHorizontal: spacing.lg } : {},
  pressed: { opacity: 0.6 },
});
