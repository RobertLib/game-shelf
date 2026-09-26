import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/ui/app-text';
import { colors, spacing } from '@/constants/theme';

type InfoRowProps = {
  label: string;
  value?: ReactNode;
  last?: boolean;
};

export function InfoRow({ label, value, last }: InfoRowProps) {
  return (
    <View style={[styles.row, !last && styles.divider]}>
      <AppText tone="secondary" style={styles.label}>
        {label}
      </AppText>
      {typeof value === 'string' || typeof value === 'number' ? (
        <AppText weight="600" align="right" style={styles.value} selectable>
          {value}
        </AppText>
      ) : (
        <View style={styles.valueNode}>{value}</View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.lg,
    paddingVertical: spacing.md,
  },
  divider: { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.border },
  label: { flexShrink: 0 },
  value: { flex: 1 },
  valueNode: { flex: 1, alignItems: 'flex-end' },
});
