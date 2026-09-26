import { useState, type Ref } from 'react';
import { Platform, StyleSheet, TextInput, View, type TextInputProps } from 'react-native';

import { colors, fontSize, radius, spacing } from '@/constants/theme';

import { AppText } from './app-text';

export type TextFieldProps = TextInputProps & {
  label?: string;
  error?: string;
  hint?: string;
  suffix?: string;
  ref?: Ref<TextInput>;
};

export function TextField({
  label,
  error,
  hint,
  suffix,
  style,
  onFocus,
  onBlur,
  ref,
  ...props
}: TextFieldProps) {
  const [focused, setFocused] = useState(false);

  return (
    <View style={styles.container}>
      {label && (
        <AppText variant="label" tone="secondary">
          {label}
        </AppText>
      )}
      <View
        style={[
          styles.inputWrapper,
          focused && styles.focused,
          error ? styles.errorBorder : null,
          props.multiline && styles.multilineWrapper,
        ]}>
        <TextInput
          ref={ref}
          placeholderTextColor={colors.textMuted}
          selectionColor={colors.primary}
          keyboardAppearance="dark"
          {...props}
          onFocus={(event) => {
            setFocused(true);
            onFocus?.(event);
          }}
          onBlur={(event) => {
            setFocused(false);
            onBlur?.(event);
          }}
          style={[styles.input, props.multiline && styles.multiline, style]}
        />
        {suffix && (
          <AppText variant="label" tone="muted" style={styles.suffix}>
            {suffix}
          </AppText>
        )}
      </View>
      {error ? (
        <AppText variant="caption" tone="danger">
          {error}
        </AppText>
      ) : hint ? (
        <AppText variant="caption" tone="muted">
          {hint}
        </AppText>
      ) : null}
    </View>
  );
}

const webNoOutline = Platform.OS === 'web' ? { outlineWidth: 0 } : {};

const styles = StyleSheet.create({
  container: { gap: spacing.xs + 2 },
  inputWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surfaceRaised,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
  },
  multilineWrapper: { alignItems: 'flex-start' },
  focused: { borderColor: colors.primary },
  errorBorder: { borderColor: colors.danger },
  input: {
    flex: 1,
    minWidth: 0,
    minHeight: 48,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    color: colors.text,
    fontSize: fontSize.md,
    // The wrapper's border already shows focus; drop the browser's outline.
    ...webNoOutline,
  },
  multiline: { minHeight: 110, paddingTop: spacing.md, textAlignVertical: 'top' },
  suffix: { paddingRight: spacing.md },
});
