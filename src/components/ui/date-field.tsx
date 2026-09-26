import Ionicons from '@expo/vector-icons/Ionicons';
import DateTimePicker, { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import { Platform, Pressable, StyleSheet, View } from 'react-native';

import { colors, radius, spacing } from '@/constants/theme';
import { formatDate, parseIsoDate, toIsoDate } from '@/lib/format';

import { AppText } from './app-text';

export type DateFieldProps = {
  /** ISO calendar date ("YYYY-MM-DD") or null. */
  value: string | null;
  onChange: (value: string | null) => void;
  placeholder?: string;
  error?: string;
};

export function DateField({ value, onChange, placeholder = 'Not set' }: DateFieldProps) {
  const date = value ? parseIsoDate(value) : null;
  const today = new Date();

  const openAndroidPicker = () =>
    DateTimePickerAndroid.open({
      value: date ?? today,
      mode: 'date',
      maximumDate: today,
      onValueChange: (_event, picked) => onChange(toIsoDate(picked)),
    });

  return (
    <View style={styles.row}>
      {Platform.OS === 'ios' && date ? (
        <DateTimePicker
          value={date}
          mode="date"
          display="compact"
          maximumDate={today}
          themeVariant="dark"
          accentColor={colors.primary}
          onValueChange={(_event, picked) => onChange(toIsoDate(picked))}
        />
      ) : (
        <Pressable
          accessibilityRole="button"
          onPress={() =>
            Platform.OS === 'android' ? openAndroidPicker() : onChange(toIsoDate(today))
          }
          style={({ pressed }) => [styles.button, pressed && styles.pressed]}>
          <Ionicons
            name="calendar-outline"
            size={18}
            color={date ? colors.text : colors.textMuted}
          />
          <AppText tone={date ? 'default' : 'muted'}>
            {date ? formatDate(value) : placeholder}
          </AppText>
        </Pressable>
      )}
      {date && (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Clear date"
          hitSlop={8}
          onPress={() => onChange(null)}
          style={styles.clear}>
          <Ionicons name="close-circle" size={20} color={colors.textMuted} />
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, minHeight: 48 },
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    minHeight: 48,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceRaised,
  },
  pressed: { opacity: 0.7 },
  clear: { padding: spacing.xs },
});
