import Ionicons from '@expo/vector-icons/Ionicons';
import { Pressable, StyleSheet, View } from 'react-native';

import type { Option } from '@/constants/game-options';
import { colors, radius, spacing } from '@/constants/theme';

import { AppText } from './app-text';

type ChipProps = {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  count?: number;
  /** Shows a remove (×) icon, for active-filter chips. */
  removable?: boolean;
  disabled?: boolean;
};

export function Chip({ label, selected = false, onPress, count, removable, disabled }: ChipProps) {
  return (
    <Pressable
      accessibilityRole={removable ? 'button' : 'checkbox'}
      accessibilityState={{ checked: removable ? undefined : selected, disabled }}
      accessibilityLabel={removable ? `Remove filter ${label}` : label}
      onPress={onPress}
      disabled={disabled}
      hitSlop={4}
      style={({ pressed }) => [
        styles.chip,
        selected && styles.selected,
        pressed && styles.pressed,
        disabled && styles.disabled,
      ]}>
      <AppText variant="label" style={{ color: selected ? colors.text : colors.textSecondary }}>
        {label}
      </AppText>
      {count != null && (
        <AppText
          variant="caption"
          weight="700"
          style={{ color: selected ? colors.primary : colors.textMuted }}>
          {count}
        </AppText>
      )}
      {removable && <Ionicons name="close" size={14} color={colors.textSecondary} />}
    </Pressable>
  );
}

type OptionChipsProps<T extends string> =
  | {
      options: Option<T>[];
      multiple?: false;
      value: T | null;
      onChange: (value: T | null) => void;
      /** Allow tapping the selected chip again to clear it (default true). */
      allowDeselect?: boolean;
      counts?: Partial<Record<T, number>>;
    }
  | {
      options: Option<T>[];
      multiple: true;
      value: T[];
      onChange: (value: T[]) => void;
      allowDeselect?: never;
      counts?: Partial<Record<T, number>>;
    };

export function OptionChips<T extends string>(props: OptionChipsProps<T>) {
  return (
    <View style={styles.wrap}>
      {props.options.map((option) => {
        const selected = props.multiple
          ? props.value.includes(option.value)
          : props.value === option.value;
        const onPress = () => {
          if (props.multiple) {
            props.onChange(
              selected
                ? props.value.filter((v) => v !== option.value)
                : [...props.value, option.value],
            );
          } else if (selected) {
            if (props.allowDeselect !== false) props.onChange(null);
          } else {
            props.onChange(option.value);
          }
        };
        return (
          <Chip
            key={option.value}
            label={option.label}
            selected={selected}
            onPress={onPress}
            count={props.counts?.[option.value]}
          />
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs + 2,
    minHeight: 34,
    paddingHorizontal: spacing.md,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceRaised,
  },
  selected: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  pressed: { opacity: 0.7 },
  disabled: { opacity: 0.4 },
});
