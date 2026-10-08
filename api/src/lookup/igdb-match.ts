import { Platform } from '../generated/prisma/enums.js';
import { detectPlatform } from './product-title.js';

/** A game from the IGDB `games` endpoint with the fields {@link IGDB_GAME_FIELDS}. */
export interface IgdbGame {
  id: number;
  name: string;
  /** Unix time in seconds. */
  first_release_date?: number;
  game_type?: number;
  genres?: { id: number; name: string }[];
  platforms?: { id: number; name: string }[];
  cover?: { id: number; image_id: string };
  involved_companies?: {
    id: number;
    developer: boolean;
    publisher: boolean;
    company?: { id: number; name: string };
  }[];
}

export const IGDB_GAME_FIELDS = [
  'name',
  'first_release_date',
  'game_type',
  'genres.name',
  'platforms.name',
  'cover.image_id',
  'involved_companies.developer',
  'involved_companies.publisher',
  'involved_companies.company.name',
].join(', ');

/** What a barcode lookup takes over from an IGDB game. */
export interface IgdbGameDetails {
  title: string;
  platforms: Platform[];
  genre: string | null;
  developer: string | null;
  publisher: string | null;
  releaseYear: number | null;
  coverImageUrl: string | null;
}

/** Below this share of common title words a search result is a different game. */
const MIN_SIMILARITY = 0.5;

/** IGDB game types that are not a game sold on its own: DLC, mod, episode, season, fork, pack, update. */
const ADD_ON_TYPES = new Set([1, 5, 6, 7, 12, 13, 14]);

const PLATFORM_ORDER: readonly Platform[] = Object.values(Platform);

/** IGDB genres from the most telling one, renamed to how collectors usually write them. */
const GENRES: ReadonlyArray<readonly [igdb: string, label: string]> = [
  ['Platform', 'Platformer'],
  ['Fighting', 'Fighting'],
  ['Racing', 'Racing'],
  ['Sport', 'Sports'],
  ['Shooter', 'Shooter'],
  ['Puzzle', 'Puzzle'],
  ['Real Time Strategy (RTS)', 'Real-time strategy'],
  ['Turn-based strategy (TBS)', 'Turn-based strategy'],
  ['Tactical', 'Tactical'],
  ['Strategy', 'Strategy'],
  ['Role-playing (RPG)', 'RPG'],
  ["Hack and slash/Beat 'em up", "Beat 'em up"],
  ['Simulator', 'Simulation'],
  ['Point-and-click', 'Point-and-click'],
  ['Music', 'Music'],
  ['Pinball', 'Pinball'],
  ['Quiz/Trivia', 'Quiz'],
  ['Visual Novel', 'Visual novel'],
  ['Card & Board Game', 'Card & board game'],
  ['MOBA', 'MOBA'],
  ['Adventure', 'Adventure'],
  ['Arcade', 'Arcade'],
  ['Indie', 'Indie'],
];

const ROMAN_NUMERALS: Record<string, string> = {
  ii: '2',
  iii: '3',
  iv: '4',
  v: '5',
  vi: '6',
  vii: '7',
  viii: '8',
  ix: '9',
  x: '10',
  xi: '11',
  xii: '12',
  xiii: '13',
};
const IGNORED_WORDS = new Set(['the', 'a', 'an']);

/**
 * The search result that is most likely the game from the barcode: similar
 * title first, then the right platform, and a full game rather than an add-on.
 */
export function pickBestMatch(
  candidates: IgdbGame[],
  title: string,
  platform: Platform | null,
): IgdbGame | null {
  let best: { game: IgdbGame; score: number } | null = null;
  for (const game of candidates) {
    const similarity = titleSimilarity(title, game.name);
    if (similarity < MIN_SIMILARITY) continue;
    let score = similarity;
    if (platform && gamePlatforms(game).includes(platform)) score += 0.2;
    if (isAddOn(game)) score -= 0.3;
    if (!best || score > best.score) best = { game, score };
  }
  return best?.game ?? null;
}

/** Share of common words (Jaccard index), ignoring case, accents, punctuation and roman numerals. */
export function titleSimilarity(a: string, b: string): number {
  const wordsA = titleWords(a);
  const wordsB = titleWords(b);
  if (wordsA.size === 0 || wordsB.size === 0) return 0;
  let common = 0;
  for (const word of wordsA) if (wordsB.has(word)) common++;
  return common / (wordsA.size + wordsB.size - common);
}

/** DLC, a mod, an episode or another add-on rather than a game sold on its own. */
export function isAddOn(game: IgdbGame): boolean {
  return game.game_type !== undefined && ADD_ON_TYPES.has(game.game_type);
}

export function toGameDetails(game: IgdbGame): IgdbGameDetails {
  const companies = game.involved_companies ?? [];
  const releaseYear = game.first_release_date
    ? new Date(game.first_release_date * 1000).getUTCFullYear()
    : null;
  return {
    title: game.name.trim(),
    platforms: gamePlatforms(game),
    genre: pickGenre(game.genres?.map((genre) => genre.name) ?? []),
    developer: companies.find((c) => c.developer)?.company?.name ?? null,
    publisher: companies.find((c) => c.publisher)?.company?.name ?? null,
    releaseYear:
      releaseYear && releaseYear >= 1950 && releaseYear <= 2100
        ? releaseYear
        : null,
    coverImageUrl: game.cover?.image_id
      ? `https://images.igdb.com/igdb/image/upload/t_cover_big/${game.cover.image_id}.jpg`
      : null,
  };
}

/** The game's platforms the collection knows, in the order of the Platform enum. */
function gamePlatforms(game: IgdbGame): Platform[] {
  const platforms = (game.platforms ?? [])
    .map((platform) => detectPlatform(platform.name))
    .filter((platform) => platform !== null);
  return [...new Set(platforms)].sort(
    (a, b) => PLATFORM_ORDER.indexOf(a) - PLATFORM_ORDER.indexOf(b),
  );
}

function pickGenre(names: string[]): string | null {
  for (const [igdb, label] of GENRES) {
    if (names.includes(igdb)) return label;
  }
  return names[0] ?? null;
}

function titleWords(title: string): Set<string> {
  const words = title
    .normalize('NFKD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/['’]/g, '')
    .split(/[^\p{L}\p{N}]+/u)
    .filter((word) => word && !IGNORED_WORDS.has(word))
    .map((word) => ROMAN_NUMERALS[word] ?? word);
  return new Set(words);
}
