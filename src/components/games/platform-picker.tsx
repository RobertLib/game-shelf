import Ionicons from '@expo/vector-icons/Ionicons';
import { useMemo, useState } from 'react';
import { Modal, Pressable, SectionList, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppText } from '@/components/ui/app-text';
import { IconButton } from '@/components/ui/icon-button';
import { TextField } from '@/components/ui/text-field';
import { colors, radius, spacing } from '@/constants/theme';
import { usePlatforms } from '@/features/games/queries';
import type { Platform } from '@/features/games/types';

type PlatformFieldProps = {
  value: string | null;
  onChange: (platformId: string) => void;
  error?: string;
};

/** A form row that opens a searchable platform list. */
export function PlatformField({ value, onChange, error }: PlatformFieldProps) {
  const [open, setOpen] = useState(false);
  const { byId } = usePlatforms();
  const selected = value ? byId.get(value) : undefined;

  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={selected ? `Platform: ${selected.name}` : 'Choose platform'}
        onPress={() => setOpen(true)}
        style={({ pressed }) => [
          styles.field,
          error && styles.fieldError,
          pressed && styles.pressed,
        ]}>
        <Ionicons
          name="hardware-chip-outline"
          size={20}
          color={selected ? colors.primary : colors.textMuted}
        />
        <View style={styles.fieldText}>
          {selected ? (
            <>
              <AppText weight="600">{selected.name}</AppText>
              <AppText variant="caption" tone="muted">
                {selected.manufacturer}
                {selected.release_year ? ` · ${selected.release_year}` : ''}
              </AppText>
            </>
          ) : (
            <AppText tone="muted">Choose platform</AppText>
          )}
        </View>
        <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
      </Pressable>
      {error && (
        <AppText variant="caption" tone="danger">
          {error}
        </AppText>
      )}
      <PlatformPickerModal
        visible={open}
        selectedId={value}
        onClose={() => setOpen(false)}
        onSelect={(platform) => {
          onChange(platform.id);
          setOpen(false);
        }}
      />
    </>
  );
}

type PlatformPickerModalProps = {
  visible: boolean;
  selectedId: string | null;
  onSelect: (platform: Platform) => void;
  onClose: () => void;
};

function PlatformPickerModal({ visible, selectedId, onSelect, onClose }: PlatformPickerModalProps) {
  const { platforms, isLoading } = usePlatforms();
  const [search, setSearch] = useState('');

  const sections = useMemo(() => {
    const term = search.trim().toLowerCase();
    const matches = term
      ? platforms.filter((p) =>
          [p.name, p.short_name, p.manufacturer].some((text) => text.toLowerCase().includes(term)),
        )
      : platforms;

    const groups = new Map<string, Platform[]>();
    for (const platform of matches) {
      const list = groups.get(platform.manufacturer) ?? [];
      list.push(platform);
      groups.set(platform.manufacturer, list);
    }
    return [...groups.entries()].map(([title, data]) => ({ title, data }));
  }, [platforms, search]);

  return (
    <Modal
      visible={visible}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={onClose}
      onDismiss={() => setSearch('')}>
      <SafeAreaView style={styles.modal} edges={['top', 'bottom']}>
        <View style={styles.modalHeader}>
          <AppText variant="heading">Choose platform</AppText>
          <IconButton icon="close" accessibilityLabel="Close" onPress={onClose} />
        </View>
        <View style={styles.search}>
          <TextField
            value={search}
            onChangeText={setSearch}
            placeholder="Search platforms"
            autoCorrect={false}
            autoCapitalize="none"
            clearButtonMode="while-editing"
            returnKeyType="search"
          />
        </View>
        <SectionList
          sections={sections}
          keyExtractor={(item) => item.id}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          stickySectionHeadersEnabled
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <AppText tone="muted" align="center" style={styles.empty}>
              {isLoading ? 'Loading platforms…' : 'No platform matches your search.'}
            </AppText>
          }
          renderSectionHeader={({ section }) => (
            <AppText variant="overline" tone="muted" style={styles.sectionHeader}>
              {section.title}
            </AppText>
          )}
          renderItem={({ item }) => {
            const selected = item.id === selectedId;
            return (
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ selected }}
                onPress={() => onSelect(item)}
                style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}>
                <View style={styles.rowText}>
                  <AppText
                    weight={selected ? '700' : '500'}
                    tone={selected ? 'primary' : 'default'}>
                    {item.name}
                  </AppText>
                  {item.release_year && (
                    <AppText variant="caption" tone="muted">
                      {item.release_year}
                    </AppText>
                  )}
                </View>
                {selected && <Ionicons name="checkmark" size={20} color={colors.primary} />}
              </Pressable>
            );
          }}
        />
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  field: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    minHeight: 56,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceRaised,
  },
  fieldError: { borderColor: colors.danger },
  fieldText: { flex: 1, gap: 2 },
  pressed: { opacity: 0.7 },
  modal: { flex: 1, backgroundColor: colors.background },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingLeft: spacing.lg,
    paddingRight: spacing.sm,
    paddingVertical: spacing.sm,
  },
  search: { paddingHorizontal: spacing.lg, paddingBottom: spacing.sm },
  list: { paddingBottom: spacing.xxxl },
  sectionHeader: {
    backgroundColor: colors.background,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.lg,
    paddingBottom: spacing.xs,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 52,
    paddingHorizontal: spacing.lg,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  rowPressed: { backgroundColor: colors.surfaceRaised },
  rowText: { flex: 1, gap: 2 },
  empty: { padding: spacing.xxl },
});
