import {
  keepPreviousData,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { useMemo } from 'react';

import type { LibraryFilters } from '@/features/library/library-store';

import {
  createGame,
  deleteGame,
  fetchCollectionStats,
  fetchGame,
  fetchLibraryCount,
  fetchLibraryPage,
  fetchPlatforms,
  LIBRARY_PAGE_SIZE,
  setFavorite,
  updateGame,
  type LibraryQuery,
} from './api';
import type { FormImage, GameDetail, GameInput, Platform, ProgressCallback } from './types';

export const queryKeys = {
  games: ['games'] as const,
  library: (query: LibraryQuery) => ['games', 'library', query] as const,
  libraryCount: (search: string, filters: LibraryFilters) =>
    ['games', 'library-count', search, filters] as const,
  game: (id: string) => ['games', 'detail', id] as const,
  stats: ['games', 'stats'] as const,
  platforms: ['platforms'] as const,
};

export function useLibrary(query: LibraryQuery) {
  return useInfiniteQuery({
    queryKey: queryKeys.library(query),
    queryFn: ({ pageParam }) => fetchLibraryPage(query, pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * LIBRARY_PAGE_SIZE < lastPage.total ? lastPage.page + 1 : undefined,
    placeholderData: keepPreviousData,
  });
}

export function useLibraryCount(search: string, filters: LibraryFilters) {
  return useQuery({
    queryKey: queryKeys.libraryCount(search, filters),
    queryFn: () => fetchLibraryCount(search, filters),
    placeholderData: keepPreviousData,
  });
}

export function useGame(id: string) {
  return useQuery({
    queryKey: queryKeys.game(id),
    queryFn: () => fetchGame(id),
  });
}

export function useCollectionStats() {
  return useQuery({ queryKey: queryKeys.stats, queryFn: fetchCollectionStats });
}

export function usePlatforms() {
  const query = useQuery({
    queryKey: queryKeys.platforms,
    queryFn: fetchPlatforms,
    staleTime: Infinity,
  });
  const byId = useMemo(
    () => new Map<string, Platform>((query.data ?? []).map((p) => [p.id, p])),
    [query.data],
  );
  return { ...query, platforms: query.data ?? [], byId };
}

type SaveVariables = {
  input: GameInput;
  images: FormImage[];
  onProgress?: ProgressCallback;
};

export function useCreateGame() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ input, images, onProgress }: SaveVariables) =>
      createGame(input, images, onProgress),
    // Also after a partial failure: the game itself exists.
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.games }),
  });
}

export function useUpdateGame(game: GameDetail) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ input, images, onProgress }: SaveVariables) =>
      updateGame(game.id, input, images, game.images, onProgress),
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.games }),
  });
}

export function useDeleteGame() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteGame,
    // Skip the deleted game's own detail query: refetching it would flash
    // "not found" on the screen the user is just leaving. It is garbage
    // collected once that screen unmounts.
    onSuccess: (_data, id) =>
      queryClient.invalidateQueries({
        queryKey: queryKeys.games,
        predicate: (query) => query.queryKey[1] !== 'detail' || query.queryKey[2] !== id,
      }),
  });
}

export function useToggleFavorite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, isFavorite }: { id: string; isFavorite: boolean }) =>
      setFavorite(id, isFavorite),
    onMutate: async ({ id, isFavorite }) => {
      const key = queryKeys.game(id);
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<GameDetail | null>(key);
      if (previous) queryClient.setQueryData(key, { ...previous, is_favorite: isFavorite });
      return { previous };
    },
    onError: (_error, { id }, context) => {
      if (context?.previous) queryClient.setQueryData(queryKeys.game(id), context.previous);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.games }),
  });
}
