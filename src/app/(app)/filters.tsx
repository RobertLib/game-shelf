import { router, Stack } from 'expo-router';
import { useMemo, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { HeaderTextButton } from '@/components/ui/header-text-button';
import { OptionChips } from '@/components/ui/chip';
import { IconButton } from '@/components/ui/icon-button';
import { RatingStars } from '@/components/ui/rating-stars';
import { Section } from '@/components/ui/section';
import { TextField } from '@/components/ui/text-field';
import { ToggleRow } from '@/components/ui/toggle-row';
import {
  COMPLETENESS,
  CONDITIONS,
  FORMATS,
  GENRES,
  PLAY_STATUSES,
  REGIONS,
} from '@/constants/game-options';
import { colors, MAX_CONTENT_WIDTH, spacing } from '@/constants/theme';
import { useCollectionStats, useLibraryCount, usePlatforms } from '@/features/games/queries';
import {
  countActiveFilters,
  EMPTY_FILTERS,
  useLibraryStore,
  type LibraryFilters,
} from '@/features/library/library-store';
import { pluralize } from '@/lib/format';

const GENRE_OPTIONS = GENRES.map((genre) => ({ value: genre, label: genre }));

function parseYear(text: string) {
  const trimmed = text.trim();
  if (!/^\d{4}$/.test(trimmed)) return null;
  return Number(trimmed);
}

export default function FiltersScreen() {
  const insets = useSafeAreaInsets();
  const search = useLibraryStore((state) => state.search);
  const appliedFilters = useLibraryStore((state) => state.filters);
  const setFilters = useLibraryStore((state) => state.setFilters);

  const [draft, setDraft] = useState<LibraryFilters>(appliedFilters);
  const [yearFromText, setYearFromText] = useState(appliedFilters.yearFrom?.toString() ?? '');
  const [yearToText, setYearToText] = useState(appliedFilters.yearTo?.toString() ?? '');

  const { platforms } = usePlatforms();
  const stats = useCollectionStats();

  // Normalize the year range so "2005 → 1995" still works.
  const effective = useMemo<LibraryFilters>(() => {
    const from = parseYear(yearFromText);
    const to = parseYear(yearToText);
    const [yearFrom, yearTo] = from != null && to != null && from > to ? [to, from] : [from, to];
    return { ...draft, yearFrom, yearTo };
  }, [draft, yearFromText, yearToText]);

  const count = useLibraryCount(search, effective);

  // Only offer platforms that are actually in the collection (plus any already selected).
  const platformOptions = useMemo(() => {
    const counts = new Map((stats.data?.platforms ?? []).map((p) => [p.platform_id, p.count]));
    return platforms
      .filter((p) => counts.has(p.id) || draft.platformIds.includes(p.id))
      .map((p) => ({ value: p.id, label: p.name, count: counts.get(p.id) ?? 0 }));
  }, [platforms, stats.data, draft.platformIds]);

  const platformCounts = Object.fromEntries(platformOptions.map((p) => [p.value, p.count]));

  const update = <K extends keyof LibraryFilters>(key: K, value: LibraryFilters[K]) =>
    setDraft((current) => ({ ...current, [key]: value }));

  const reset = () => {
    setDraft(EMPTY_FILTERS);
    setYearFromText('');
    setYearToText('');
  };

  const apply = () => {
    setFilters(effective);
    router.back();
  };

  const activeCount = countActiveFilters(effective);
  const yearError =
    (yearFromText.trim() && parseYear(yearFromText) == null) ||
    (yearToText.trim() && parseYear(yearToText) == null)
      ? 'Use four digits, e.g. 1996'
      : undefined;

  return (
    <View style={styles.screen}>
      <Stack.Screen
        options={{
          headerLeft: () => (
            <IconButton icon="close" accessibilityLabel="Close" onPress={() => router.back()} />
          ),
          headerRight: () =>
            activeCount > 0 ? <HeaderTextButton title="Reset" onPress={reset} /> : null,
        }}
      />
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
        automaticallyAdjustKeyboardInsets>
        <Section>
          <ToggleRow
            label="Favorites only"
            value={draft.favoritesOnly}
            onChange={(value) => update('favoritesOnly', value)}
          />
        </Section>

        <Section title="Platform">
          {platformOptions.length ? (
            <OptionChips
              multiple
              options={platformOptions}
              counts={platformCounts}
              value={draft.platformIds}
              onChange={(value) => update('platformIds', value)}
            />
          ) : (
            <AppText tone="muted" variant="caption">
              {stats.isPending ? 'Loading…' : 'Platforms appear here once you add games.'}
            </AppText>
          )}
        </Section>

        <Section title="Region">
          <OptionChips
            multiple
            options={REGIONS}
            value={draft.regions}
            onChange={(value) => update('regions', value)}
          />
        </Section>

        <Section title="Format">
          <OptionChips
            multiple
            options={FORMATS}
            value={draft.formats}
            onChange={(value) => update('formats', value)}
          />
        </Section>

        <Section title="Condition">
          <OptionChips
            multiple
            options={CONDITIONS}
            value={draft.conditions}
            onChange={(value) => update('conditions', value)}
          />
        </Section>

        <Section title="Completeness">
          <OptionChips
            multiple
            options={COMPLETENESS}
            value={draft.completeness}
            onChange={(value) => update('completeness', value)}
          />
        </Section>

        <Section title="Play status">
          <OptionChips
            multiple
            options={PLAY_STATUSES}
            counts={stats.data?.play_status}
            value={draft.playStatuses}
            onChange={(value) => update('playStatuses', value)}
          />
        </Section>

        <Section title="Genre" description="Games with any of the selected genres.">
          <OptionChips
            multiple
            options={GENRE_OPTIONS}
            value={draft.genres}
            onChange={(value) => update('genres', value)}
          />
        </Section>

        <Section title="Release year">
          <View style={styles.row}>
            <View style={styles.flex}>
              <TextField
                label="From"
                placeholder="1985"
                value={yearFromText}
                onChangeText={setYearFromText}
                keyboardType="number-pad"
                maxLength={4}
              />
            </View>
            <View style={styles.flex}>
              <TextField
                label="To"
                placeholder="2005"
                value={yearToText}
                onChangeText={setYearToText}
                keyboardType="number-pad"
                maxLength={4}
              />
            </View>
          </View>
          {yearError && (
            <AppText variant="caption" tone="danger">
              {yearError}
            </AppText>
          )}
        </Section>

        <Section title="My rating" description="Minimum rating. Tap the same star again to clear.">
          <RatingStars
            value={draft.minRating}
            onChange={(value) => update('minRating', value)}
            size={30}
          />
        </Section>
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.md }]}>
        <Button
          title={
            count.data == null
              ? 'Show games'
              : count.data === 0
                ? 'No matching games'
                : `Show ${pluralize(count.data, 'game')}`
          }
          onPress={apply}
          loading={count.isPending}
          fullWidth
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: {
    padding: spacing.lg,
    gap: spacing.xl,
    paddingBottom: spacing.xxl,
    width: '100%',
    maxWidth: MAX_CONTENT_WIDTH,
    alignSelf: 'center',
  },
  row: { flexDirection: 'row', gap: spacing.md },
  flex: { flex: 1 },
  footer: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
    backgroundColor: colors.background,
  },
});
