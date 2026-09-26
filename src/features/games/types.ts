import type { Tables, TablesInsert } from '@/lib/database.types';

export type Game = Tables<'games'>;
export type GameImageRow = Tables<'game_images'>;
export type Platform = Tables<'platforms'>;

/** Editable columns of a game (everything the form controls). */
export type GameInput = Omit<
  TablesInsert<'games'>,
  'id' | 'user_id' | 'created_at' | 'updated_at' | 'search_text'
>;

export type GameImage = GameImageRow & {
  url: string | null;
  thumbUrl: string | null;
};

export type GameDetail = Game & { images: GameImage[] };

export type GameListItem = Pick<
  Game,
  | 'id'
  | 'title'
  | 'edition'
  | 'platform_id'
  | 'region'
  | 'format'
  | 'condition'
  | 'completeness'
  | 'release_year'
  | 'rating'
  | 'is_favorite'
  | 'play_status'
> & {
  coverPath: string | null;
  coverUrl: string | null;
};

/** An image in the game form: either already stored, or freshly picked on the device. */
export type FormImage =
  | {
      key: string;
      kind: 'existing';
      id: string;
      storagePath: string;
      thumbPath: string;
      previewUrl: string | null;
    }
  | {
      key: string;
      kind: 'new';
      uri: string;
      width: number;
      height: number;
    };

export type CollectionStats = {
  total_games: number;
  favorites: number;
  platforms: { platform_id: string; count: number }[];
  play_status: Partial<Record<Game['play_status'], number>>;
  totals: { currency: string; spent: number; value: number }[];
};

export type ProgressCallback = (message: string) => void;
