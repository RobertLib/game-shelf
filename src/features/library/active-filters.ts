import {
  completenessLabel,
  conditionLabel,
  formatLabel,
  playStatusLabel,
  regionLabel,
} from '@/constants/game-options';
import type { Platform } from '@/features/games/types';

import type { LibraryFilters } from './library-store';

export type ActiveFilter = {
  key: string;
  label: string;
  /** Returns the filters with this one removed. */
  remove: (filters: LibraryFilters) => LibraryFilters;
};

function without<T>(list: T[], value: T) {
  return list.filter((item) => item !== value);
}

export function yearRangeLabel(from: number | null, to: number | null) {
  if (from != null && to != null) return from === to ? `${from}` : `${from}–${to}`;
  if (from != null) return `${from} or later`;
  if (to != null) return `${to} or earlier`;
  return '';
}

/** Flattens the filter state into removable chips, in the order they appear in the filter screen. */
export function listActiveFilters(filters: LibraryFilters, platformsById: Map<string, Platform>) {
  const active: ActiveFilter[] = [];

  if (filters.favoritesOnly) {
    active.push({
      key: 'fav',
      label: 'Favorites',
      remove: (f) => ({ ...f, favoritesOnly: false }),
    });
  }
  for (const id of filters.platformIds) {
    active.push({
      key: `platform:${id}`,
      label: platformsById.get(id)?.name ?? id,
      remove: (f) => ({ ...f, platformIds: without(f.platformIds, id) }),
    });
  }
  for (const region of filters.regions) {
    active.push({
      key: `region:${region}`,
      label: regionLabel(region) ?? region,
      remove: (f) => ({ ...f, regions: without(f.regions, region) }),
    });
  }
  for (const format of filters.formats) {
    active.push({
      key: `format:${format}`,
      label: formatLabel(format) ?? format,
      remove: (f) => ({ ...f, formats: without(f.formats, format) }),
    });
  }
  for (const condition of filters.conditions) {
    active.push({
      key: `condition:${condition}`,
      label: conditionLabel(condition) ?? condition,
      remove: (f) => ({ ...f, conditions: without(f.conditions, condition) }),
    });
  }
  for (const completeness of filters.completeness) {
    active.push({
      key: `completeness:${completeness}`,
      label: completenessLabel(completeness) ?? completeness,
      remove: (f) => ({ ...f, completeness: without(f.completeness, completeness) }),
    });
  }
  for (const status of filters.playStatuses) {
    active.push({
      key: `status:${status}`,
      label: playStatusLabel(status) ?? status,
      remove: (f) => ({ ...f, playStatuses: without(f.playStatuses, status) }),
    });
  }
  for (const genre of filters.genres) {
    active.push({
      key: `genre:${genre}`,
      label: genre,
      remove: (f) => ({ ...f, genres: without(f.genres, genre) }),
    });
  }
  if (filters.yearFrom != null || filters.yearTo != null) {
    active.push({
      key: 'year',
      label: yearRangeLabel(filters.yearFrom, filters.yearTo),
      remove: (f) => ({ ...f, yearFrom: null, yearTo: null }),
    });
  }
  if (filters.minRating != null) {
    active.push({
      key: 'rating',
      label: `${filters.minRating}+ stars`,
      remove: (f) => ({ ...f, minRating: null }),
    });
  }

  return active;
}
