import Ionicons from '@expo/vector-icons/Ionicons';
import { router, Stack } from 'expo-router';
import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  useWindowDimensions,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { GameGridItem } from '@/components/library/game-grid-item';
import { GameListRow } from '@/components/library/game-list-item';
import { SearchBar } from '@/components/library/search-bar';
import { SortSheet } from '@/components/library/sort-sheet';
import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { Chip } from '@/components/ui/chip';
import { EmptyState } from '@/components/ui/empty-state';
import { IconButton } from '@/components/ui/icon-button';
import { colors, radius, spacing } from '@/constants/theme';
import { searchTerms } from '@/features/games/api';
import { useLibrary, usePlatforms } from '@/features/games/queries';
import type { GameListItem } from '@/features/games/types';
import { listActiveFilters } from '@/features/library/active-filters';
import {
  countActiveFilters,
  SORT_OPTIONS,
  useLibraryStore,
} from '@/features/library/library-store';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { errorMessage } from '@/lib/dialogs';
import { pluralize } from '@/lib/format';

const H_PADDING = spacing.lg;
const GRID_GAP = spacing.md;
const MIN_TILE_WIDTH = 104;

export default function LibraryScreen() {
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const { byId: platformsById } = usePlatforms();

  const {
    search,
    filters,
    sort,
    viewMode,
    setSearch,
    setFilters,
    resetFilters,
    setSort,
    setViewMode,
  } = useLibraryStore();

  const [searchText, setSearchText] = useState(search);
  const debouncedSearch = useDebouncedValue(searchText, 300);
  useEffect(() => {
    setSearch(debouncedSearch);
  }, [debouncedSearch, setSearch]);

  const [sortOpen, setSortOpen] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const library = useLibrary({ search, filters, sort });
  const games = useMemo(
    () => library.data?.pages.flatMap((page) => page.items) ?? [],
    [library.data],
  );
  const total = library.data?.pages[0]?.total ?? 0;

  const activeFilters = useMemo(
    () => listActiveFilters(filters, platformsById),
    [filters, platformsById],
  );
  const filterCount = countActiveFilters(filters);
  const isNarrowed = filterCount > 0 || searchTerms(search).length > 0;

  const numColumns =
    viewMode === 'grid'
      ? Math.max(3, Math.floor((width - 2 * H_PADDING + GRID_GAP) / (MIN_TILE_WIDTH + GRID_GAP)))
      : 1;
  const tileWidth = (width - 2 * H_PADDING - GRID_GAP * (numColumns - 1)) / numColumns;

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await library.refetch();
    } finally {
      setRefreshing(false);
    }
  }, [library]);

  const renderItem = useCallback(
    ({ item }: { item: GameListItem }) =>
      viewMode === 'grid' ? (
        <GameGridItem
          game={item}
          platform={platformsById.get(item.platform_id)}
          width={tileWidth}
        />
      ) : (
        <GameListRow game={item} platform={platformsById.get(item.platform_id)} />
      ),
    [viewMode, platformsById, tileWidth],
  );

  const clearSearchAndFilters = () => {
    setSearchText('');
    setSearch('');
    resetFilters();
  };

  const sortLabel = SORT_OPTIONS.find((option) => option.value === sort)?.label;

  const listHeader = (
    <View style={styles.header}>
      <View style={styles.toolbar}>
        <SearchBar value={searchText} onChangeText={setSearchText} />
        <IconButton
          icon="options-outline"
          variant="filled"
          accessibilityLabel={filterCount ? `Filters, ${filterCount} active` : 'Filters'}
          badge={filterCount}
          onPress={() => router.push('/filters')}
        />
        <IconButton
          icon="swap-vertical"
          variant="filled"
          accessibilityLabel={`Sort, currently ${sortLabel}`}
          onPress={() => setSortOpen(true)}
        />
        <IconButton
          icon={viewMode === 'grid' ? 'list' : 'grid-outline'}
          variant="filled"
          accessibilityLabel={viewMode === 'grid' ? 'Show as list' : 'Show as grid'}
          onPress={() => setViewMode(viewMode === 'grid' ? 'list' : 'grid')}
        />
      </View>

      {activeFilters.length > 0 && (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.activeFilters}
          style={styles.activeFiltersScroll}>
          {activeFilters.map((filter) => (
            <Chip
              key={filter.key}
              label={filter.label}
              selected
              removable
              onPress={() => setFilters(filter.remove(filters))}
            />
          ))}
          <Pressable onPress={resetFilters} hitSlop={8} style={styles.clearAll}>
            <AppText variant="label" tone="primary">
              Clear all
            </AppText>
          </Pressable>
        </ScrollView>
      )}

      {library.data && total > 0 && (
        <View style={styles.summary}>
          <AppText variant="label" tone="secondary">
            {pluralize(total, 'game')}
          </AppText>
          <Pressable onPress={() => setSortOpen(true)} hitSlop={8} style={styles.sortLink}>
            <AppText variant="caption" tone="muted">
              {sortLabel}
            </AppText>
            <Ionicons name="chevron-down" size={12} color={colors.textMuted} />
          </Pressable>
        </View>
      )}
    </View>
  );

  let emptyComponent: ReactElement | null = null;
  if (library.isPending) {
    emptyComponent = <ActivityIndicator color={colors.primary} style={styles.loading} />;
  } else if (library.isError) {
    emptyComponent = (
      <EmptyState
        icon="cloud-offline-outline"
        title="Could not load your library"
        message={errorMessage(library.error)}
        action={
          <Button title="Try again" variant="secondary" onPress={() => void library.refetch()} />
        }
      />
    );
  } else if (isNarrowed) {
    emptyComponent = (
      <EmptyState
        icon="search-outline"
        title="No games found"
        message="Nothing in your library matches this search and filters."
        action={
          <Button
            title="Clear search and filters"
            variant="secondary"
            onPress={clearSearchAndFilters}
          />
        }
      />
    );
  } else {
    emptyComponent = (
      <EmptyState
        icon="albums-outline"
        title="Your shelf is empty"
        message="Add the first game of your collection: snap the cover, pick the platform and fill in the details."
        action={
          <Button
            title="Add your first game"
            icon="add"
            onPress={() => router.push('/games/new')}
          />
        }
      />
    );
  }

  return (
    <View style={styles.screen}>
      <Stack.Screen
        options={{
          headerRight: () => (
            <IconButton
              icon="person-circle-outline"
              size={26}
              accessibilityLabel="Account"
              onPress={() => router.push('/account')}
            />
          ),
        }}
      />
      <FlatList
        key={`${viewMode}-${numColumns}`}
        data={games}
        keyExtractor={(item) => item.id}
        renderItem={renderItem}
        numColumns={numColumns}
        columnWrapperStyle={numColumns > 1 ? { gap: GRID_GAP } : undefined}
        ListHeaderComponent={listHeader}
        ListEmptyComponent={emptyComponent}
        ListFooterComponent={
          library.isFetchingNextPage ? (
            <ActivityIndicator color={colors.primary} style={styles.footerLoader} />
          ) : null
        }
        contentContainerStyle={[
          styles.content,
          { gap: viewMode === 'grid' ? spacing.lg : spacing.sm, paddingBottom: insets.bottom + 96 },
        ]}
        onEndReached={() => {
          if (library.hasNextPage && !library.isFetchingNextPage) void library.fetchNextPage();
        }}
        onEndReachedThreshold={0.6}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.primary}
          />
        }
        keyboardDismissMode="on-drag"
        keyboardShouldPersistTaps="handled"
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Add game"
        onPress={() => router.push('/games/new')}
        style={({ pressed }) => [
          styles.fab,
          { bottom: insets.bottom + spacing.lg },
          pressed && styles.fabPressed,
        ]}>
        <Ionicons name="add" size={30} color={colors.onPrimary} />
      </Pressable>
      <SortSheet
        visible={sortOpen}
        value={sort}
        onChange={setSort}
        onClose={() => setSortOpen(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { paddingHorizontal: H_PADDING, flexGrow: 1 },
  header: { gap: spacing.md, paddingTop: spacing.xs },
  toolbar: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  activeFiltersScroll: { marginHorizontal: -H_PADDING },
  activeFilters: { gap: spacing.sm, paddingHorizontal: H_PADDING, alignItems: 'center' },
  clearAll: { paddingHorizontal: spacing.sm },
  summary: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sortLink: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  loading: { marginTop: spacing.xxxl },
  footerLoader: { marginVertical: spacing.xl },
  fab: {
    position: 'absolute',
    right: spacing.lg,
    width: 60,
    height: 60,
    borderRadius: radius.xl,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: colors.primary,
    shadowOpacity: 0.45,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 6 },
    elevation: 8,
  },
  fabPressed: { backgroundColor: colors.primaryPressed },
});
