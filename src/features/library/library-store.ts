import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type {
  GameCompleteness,
  GameCondition,
  GameFormat,
  GameRegion,
  PlayStatus,
} from '@/constants/game-options';

export type SortKey =
  | 'recent'
  | 'title_asc'
  | 'title_desc'
  | 'release_desc'
  | 'release_asc'
  | 'price_desc'
  | 'rating_desc';

export const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  { value: 'recent', label: 'Recently added' },
  { value: 'title_asc', label: 'Title A–Z' },
  { value: 'title_desc', label: 'Title Z–A' },
  { value: 'release_desc', label: 'Release year (newest)' },
  { value: 'release_asc', label: 'Release year (oldest)' },
  { value: 'price_desc', label: 'Purchase price (highest)' },
  { value: 'rating_desc', label: 'My rating (highest)' },
];

export type ViewMode = 'grid' | 'list';

export type LibraryFilters = {
  platformIds: string[];
  regions: GameRegion[];
  formats: GameFormat[];
  conditions: GameCondition[];
  completeness: GameCompleteness[];
  playStatuses: PlayStatus[];
  genres: string[];
  yearFrom: number | null;
  yearTo: number | null;
  minRating: number | null;
  favoritesOnly: boolean;
};

export const EMPTY_FILTERS: LibraryFilters = {
  platformIds: [],
  regions: [],
  formats: [],
  conditions: [],
  completeness: [],
  playStatuses: [],
  genres: [],
  yearFrom: null,
  yearTo: null,
  minRating: null,
  favoritesOnly: false,
};

export function countActiveFilters(filters: LibraryFilters) {
  return (
    filters.platformIds.length +
    filters.regions.length +
    filters.formats.length +
    filters.conditions.length +
    filters.completeness.length +
    filters.playStatuses.length +
    filters.genres.length +
    (filters.yearFrom != null || filters.yearTo != null ? 1 : 0) +
    (filters.minRating != null ? 1 : 0) +
    (filters.favoritesOnly ? 1 : 0)
  );
}

type LibraryState = {
  search: string;
  filters: LibraryFilters;
  sort: SortKey;
  viewMode: ViewMode;
  /** Currency pre-selected for new games (the last one used). */
  preferredCurrency: string;
  setSearch: (search: string) => void;
  setFilters: (filters: LibraryFilters) => void;
  resetFilters: () => void;
  setSort: (sort: SortKey) => void;
  setViewMode: (viewMode: ViewMode) => void;
  setPreferredCurrency: (currency: string) => void;
};

export const useLibraryStore = create<LibraryState>()(
  persist(
    (set) => ({
      search: '',
      filters: EMPTY_FILTERS,
      sort: 'recent',
      viewMode: 'grid',
      preferredCurrency: 'USD',
      setSearch: (search) => set({ search }),
      setFilters: (filters) => set({ filters }),
      resetFilters: () => set({ filters: EMPTY_FILTERS }),
      setSort: (sort) => set({ sort }),
      setViewMode: (viewMode) => set({ viewMode }),
      setPreferredCurrency: (preferredCurrency) => set({ preferredCurrency }),
    }),
    {
      name: 'library-preferences',
      storage: createJSONStorage(() => AsyncStorage),
      // Search and filters start fresh on every launch; layout preferences stick.
      partialize: (state) => ({
        sort: state.sort,
        viewMode: state.viewMode,
        preferredCurrency: state.preferredCurrency,
      }),
    },
  ),
);
