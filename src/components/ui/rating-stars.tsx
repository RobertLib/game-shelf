import Ionicons from '@expo/vector-icons/Ionicons';
import { Pressable, StyleSheet, View } from 'react-native';

import { colors, spacing } from '@/constants/theme';

type RatingStarsProps = {
  value: number | null;
  onChange?: (value: number | null) => void;
  size?: number;
};

/** Read-only when `onChange` is omitted. Tapping the current rating clears it. */
export function RatingStars({ value, onChange, size = 18 }: RatingStarsProps) {
  const current = value ?? 0;
  return (
    <View
      style={[styles.row, { gap: onChange ? spacing.sm : 2 }]}
      accessibilityRole={onChange ? 'adjustable' : 'text'}
      accessibilityLabel={current ? `Rating ${current} of 5` : 'Not rated'}>
      {[1, 2, 3, 4, 5].map((star) => {
        const icon = (
          <Ionicons
            name={star <= current ? 'star' : 'star-outline'}
            size={size}
            color={star <= current ? colors.star : colors.textMuted}
          />
        );
        if (!onChange) return <View key={star}>{icon}</View>;
        return (
          <Pressable
            key={star}
            hitSlop={6}
            accessibilityLabel={`${star} star${star > 1 ? 's' : ''}`}
            onPress={() => onChange(star === current ? null : star)}>
            {icon}
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center' },
});
