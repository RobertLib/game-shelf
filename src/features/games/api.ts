import type { LibraryFilters, SortKey } from '@/features/library/library-store';
import { supabase } from '@/lib/supabase';

import { removeStorageFiles, signImagePaths, uploadGameImage } from './images';
import type {
  CollectionStats,
  FormImage,
  GameDetail,
  GameInput,
  GameListItem,
  Platform,
  ProgressCallback,
} from './types';

export const LIBRARY_PAGE_SIZE = 30;

const LIST_COLUMNS =
  'id, title, edition, platform_id, region, format, condition, completeness, release_year, rating, is_favorite, play_status, game_images(thumb_path, position)';

export type LibraryQuery = {
  search: string;
  filters: LibraryFilters;
  sort: SortKey;
};

/** Escapes LIKE wildcards so the search box matches literally. */
function escapeLike(term: string) {
  return term.replace(/[\\%_]/g, (char) => `\\${char}`).replace(/\*/g, '');
}

export function searchTerms(search: string) {
  return search.trim().toLowerCase().split(/\s+/).filter(Boolean).slice(0, 6);
}

function buildLibraryQuery(search: string, filters: LibraryFilters, head = false) {
  let query = supabase.from('games').select(LIST_COLUMNS, { count: 'exact', head });

  // Every word has to appear somewhere in title, edition, developer, publisher or serial.
  for (const term of searchTerms(search)) {
    query = query.ilike('search_text', `%${escapeLike(term)}%`);
  }
  if (filters.platformIds.length) query = query.in('platform_id', filters.platformIds);
  if (filters.regions.length) query = query.in('region', filters.regions);
  if (filters.formats.length) query = query.in('format', filters.formats);
  if (filters.conditions.length) query = query.in('condition', filters.conditions);
  if (filters.completeness.length) query = query.in('completeness', filters.completeness);
  if (filters.playStatuses.length) query = query.in('play_status', filters.playStatuses);
  if (filters.genres.length) query = query.overlaps('genres', filters.genres);
  if (filters.yearFrom != null) query = query.gte('release_year', filters.yearFrom);
  if (filters.yearTo != null) query = query.lte('release_year', filters.yearTo);
  if (filters.minRating != null) query = query.gte('rating', filters.minRating);
  if (filters.favoritesOnly) query = query.eq('is_favorite', true);

  return query;
}

export async function fetchLibraryPage({ search, filters, sort }: LibraryQuery, page: number) {
  let query = buildLibraryQuery(search, filters);

  switch (sort) {
    case 'recent':
      query = query.order('created_at', { ascending: false });
      break;
    case 'title_asc':
      query = query.order('title', { ascending: true });
      break;
    case 'title_desc':
      query = query.order('title', { ascending: false });
      break;
    case 'release_desc':
      query = query.order('release_year', { ascending: false, nullsFirst: false });
      break;
    case 'release_asc':
      query = query.order('release_year', { ascending: true, nullsFirst: false });
      break;
    case 'price_desc':
      query = query.order('purchase_price', { ascending: false, nullsFirst: false });
      break;
    case 'rating_desc':
      query = query.order('rating', { ascending: false, nullsFirst: false });
      break;
  }
  // Tie-breakers keep pagination stable.
  if (sort !== 'title_asc' && sort !== 'title_desc') query = query.order('title');
  query = query.order('id');

  const from = page * LIBRARY_PAGE_SIZE;
  const { data, error, count } = await query
    .order('position', { referencedTable: 'game_images' })
    .limit(1, { referencedTable: 'game_images' })
    .range(from, from + LIBRARY_PAGE_SIZE - 1);
  if (error) throw error;

  const coverPaths = data.map((game) => game.game_images[0]?.thumb_path).filter(isString);
  const urls = await signImagePaths(coverPaths);

  const items: GameListItem[] = data.map(({ game_images, ...game }) => {
    const coverPath = game_images[0]?.thumb_path ?? null;
    return { ...game, coverPath, coverUrl: coverPath ? (urls.get(coverPath) ?? null) : null };
  });

  return { items, total: count ?? 0, page };
}

export async function fetchLibraryCount(search: string, filters: LibraryFilters) {
  const { count, error } = await buildLibraryQuery(search, filters, true);
  if (error) throw error;
  return count ?? 0;
}

export async function fetchGame(id: string): Promise<GameDetail | null> {
  const { data, error } = await supabase
    .from('games')
    .select('*, game_images(*)')
    .eq('id', id)
    .order('position', { referencedTable: 'game_images' })
    .maybeSingle();
  if (error) throw error;
  if (!data) return null;

  const { game_images, ...game } = data;
  const urls = await signImagePaths(
    game_images.flatMap((img) => [img.storage_path, img.thumb_path]),
  );

  return {
    ...game,
    images: game_images.map((img) => ({
      ...img,
      url: urls.get(img.storage_path) ?? null,
      thumbUrl: urls.get(img.thumb_path) ?? null,
    })),
  };
}

export async function fetchPlatforms(): Promise<Platform[]> {
  const { data, error } = await supabase.from('platforms').select('*').order('sort_order');
  if (error) throw error;
  return data;
}

export async function fetchCollectionStats(): Promise<CollectionStats> {
  const { data, error } = await supabase.rpc('collection_stats');
  if (error) throw error;
  return data as unknown as CollectionStats;
}

async function currentUserId() {
  const { data } = await supabase.auth.getSession();
  const userId = data.session?.user.id;
  if (!userId) throw new Error('Your session has expired. Please sign in again.');
  return userId;
}

export async function createGame(
  input: GameInput,
  images: FormImage[],
  onProgress?: ProgressCallback,
) {
  const userId = await currentUserId();
  onProgress?.('Saving game…');

  const { data, error } = await supabase.from('games').insert(input).select('id').single();
  if (error) throw error;

  await syncGameImages(userId, data.id, images, [], onProgress);
  return data.id;
}

export async function updateGame(
  id: string,
  input: GameInput,
  images: FormImage[],
  previousImages: { id: string; storage_path: string; thumb_path: string; position: number }[],
  onProgress?: ProgressCallback,
) {
  const userId = await currentUserId();
  onProgress?.('Saving game…');

  const { error } = await supabase.from('games').update(input).eq('id', id);
  if (error) throw error;

  await syncGameImages(userId, id, images, previousImages, onProgress);
}

/**
 * Makes the stored images match the form: removes deleted ones, uploads new
 * ones and stores the order (position 0 is the cover).
 */
async function syncGameImages(
  userId: string,
  gameId: string,
  images: FormImage[],
  previousImages: { id: string; storage_path: string; thumb_path: string; position: number }[],
  onProgress?: ProgressCallback,
) {
  const keptIds = new Set(images.flatMap((img) => (img.kind === 'existing' ? [img.id] : [])));
  const removed = previousImages.filter((img) => !keptIds.has(img.id));

  if (removed.length) {
    const { error } = await supabase
      .from('game_images')
      .delete()
      .in(
        'id',
        removed.map((img) => img.id),
      );
    if (error) throw error;
    await removeStorageFiles(removed.flatMap((img) => [img.storage_path, img.thumb_path]));
  }

  const previousPositions = new Map(previousImages.map((img) => [img.id, img.position]));
  const newCount = images.filter((img) => img.kind === 'new').length;
  let uploaded = 0;
  const failed: number[] = [];

  for (const [position, image] of images.entries()) {
    if (image.kind === 'existing') {
      if (previousPositions.get(image.id) !== position) {
        const { error } = await supabase
          .from('game_images')
          .update({ position })
          .eq('id', image.id);
        if (error) throw error;
      }
      continue;
    }

    uploaded += 1;
    onProgress?.(newCount > 1 ? `Uploading photo ${uploaded} of ${newCount}…` : 'Uploading photo…');
    try {
      const file = await uploadGameImage(userId, gameId, image);
      const { error } = await supabase.from('game_images').insert({
        game_id: gameId,
        storage_path: file.storagePath,
        thumb_path: file.thumbPath,
        width: file.width,
        height: file.height,
        position,
      });
      if (error) {
        await removeStorageFiles([file.storagePath, file.thumbPath]);
        throw error;
      }
    } catch (error) {
      console.warn('Photo upload failed', error);
      failed.push(position);
    }
  }

  if (failed.length) {
    throw new PartialSaveError(
      failed.length === 1
        ? 'The game was saved, but one photo could not be uploaded. Edit the game to try again.'
        : `The game was saved, but ${failed.length} photos could not be uploaded. Edit the game to try again.`,
      gameId,
    );
  }
}

/** The game row was saved but some photos were not. */
export class PartialSaveError extends Error {
  constructor(
    message: string,
    readonly gameId: string,
  ) {
    super(message);
    this.name = 'PartialSaveError';
  }
}

export async function deleteGame(id: string) {
  const { data: images, error: imagesError } = await supabase
    .from('game_images')
    .select('storage_path, thumb_path')
    .eq('game_id', id);
  if (imagesError) throw imagesError;

  const { error } = await supabase.from('games').delete().eq('id', id);
  if (error) throw error;

  await removeStorageFiles(images.flatMap((img) => [img.storage_path, img.thumb_path]));
}

export async function setFavorite(id: string, isFavorite: boolean) {
  const { error } = await supabase.from('games').update({ is_favorite: isFavorite }).eq('id', id);
  if (error) throw error;
}

function isString(value: string | null | undefined): value is string {
  return typeof value === 'string';
}
