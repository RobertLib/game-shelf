import type { PropsWithChildren, ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { colors, radius, spacing } from '@/constants/theme';

import { AppText } from './app-text';

type SectionProps = PropsWithChildren<{
  title?: string;
  description?: string;
  action?: ReactNode;
  /** Render children on a card background. */
  card?: boolean;
  /** Tighter card for lists of rows with their own dividers. */
  dense?: boolean;
}>;

export function Section({
  title,
  description,
  action,
  card = true,
  dense,
  children,
}: SectionProps) {
  return (
    <View style={styles.section}>
      {(title || action) && (
        <View style={styles.header}>
          <View style={styles.headerText}>
            {title && (
              <AppText variant="overline" tone="muted">
                {title}
              </AppText>
            )}
            {description && (
              <AppText variant="caption" tone="muted">
                {description}
              </AppText>
            )}
          </View>
          {action}
        </View>
      )}
      <View style={card ? [styles.card, dense && styles.dense] : styles.plain}>{children}</View>
    </View>
  );
}

/** A labelled field block inside a Section card. */
export function Field({
  label,
  hint,
  children,
}: PropsWithChildren<{ label: string; hint?: string }>) {
  return (
    <View style={styles.field}>
      <AppText variant="label" tone="secondary">
        {label}
      </AppText>
      {children}
      {hint && (
        <AppText variant="caption" tone="muted">
          {hint}
        </AppText>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  section: { gap: spacing.sm },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.xs,
  },
  headerText: { gap: 2, flex: 1 },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    gap: spacing.lg,
  },
  dense: { gap: 0, paddingVertical: spacing.xs },
  plain: { gap: spacing.md },
  field: { gap: spacing.sm },
});
