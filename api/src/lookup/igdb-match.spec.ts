import {
  type IgdbGame,
  pickBestMatch,
  titleSimilarity,
  toGameDetails,
} from './igdb-match.js';

const game = (id: number, name: string, extra: Partial<IgdbGame> = {}) => ({
  id,
  name,
  ...extra,
});

describe('titleSimilarity', () => {
  it('ignores case, accents, punctuation, articles and roman numerals', () => {
    expect(titleSimilarity('Pokemon Sword', 'Pokémon Sword')).toBe(1);
    expect(
      titleSimilarity(
        'Call of Duty Black Ops 3',
        'Call of Duty: Black Ops III',
      ),
    ).toBe(1);
    expect(titleSimilarity('Legend of Zelda', 'The Legend of Zelda')).toBe(1);
    expect(titleSimilarity('Ratchet and Clank', 'Ratchet & Clank')).toBe(1);
  });

  it('is low for different games', () => {
    expect(titleSimilarity('Halo 3', 'Halo Wars')).toBeLessThan(0.5);
    expect(titleSimilarity('', 'Halo')).toBe(0);
  });
});

describe('pickBestMatch', () => {
  it('prefers the exact title', () => {
    const best = pickBestMatch(
      [game(1, 'Mario Kart 8'), game(2, 'Mario Kart 8 Deluxe')],
      'Mario Kart 8 Deluxe',
      null,
    );
    expect(best?.id).toBe(2);
  });

  it('prefers the release on the scanned platform', () => {
    const best = pickBestMatch(
      [
        game(1, 'Spyro the Dragon', {
          platforms: [{ id: 7, name: 'PlayStation' }],
        }),
        game(2, 'Spyro the Dragon', {
          platforms: [{ id: 48, name: 'PlayStation 4' }],
        }),
      ],
      'Spyro the Dragon',
      'PS4',
    );
    expect(best?.id).toBe(2);
  });

  it('prefers full games to add-ons', () => {
    const best = pickBestMatch(
      [
        game(1, 'Doom Eternal', { game_type: 13 }),
        game(2, 'Doom Eternal', { game_type: 0 }),
      ],
      'Doom Eternal',
      null,
    );
    expect(best?.id).toBe(2);
  });

  it('rejects results that are a different game', () => {
    expect(pickBestMatch([game(1, 'Halo Wars')], 'Halo 3', null)).toBeNull();
    expect(pickBestMatch([], 'Halo 3', null)).toBeNull();
  });
});

describe('toGameDetails', () => {
  it('takes over genre, companies, year, cover and platforms', () => {
    expect(
      toGameDetails({
        id: 1,
        name: 'Mario Kart 8 Deluxe',
        first_release_date: 1493337600, // 2017-04-28
        genres: [
          { id: 10, name: 'Racing' },
          { id: 33, name: 'Arcade' },
        ],
        platforms: [{ id: 130, name: 'Nintendo Switch' }],
        cover: { id: 5, image_id: 'co213p' },
        involved_companies: [
          {
            id: 1,
            developer: false,
            publisher: true,
            company: { id: 70, name: 'Nintendo' },
          },
          {
            id: 2,
            developer: true,
            publisher: false,
            company: { id: 9, name: 'Nintendo EPD' },
          },
        ],
      }),
    ).toEqual({
      title: 'Mario Kart 8 Deluxe',
      platforms: ['SWITCH'],
      genre: 'Racing',
      developer: 'Nintendo EPD',
      publisher: 'Nintendo',
      releaseYear: 2017,
      coverImageUrl:
        'https://images.igdb.com/igdb/image/upload/t_cover_big/co213p.jpg',
    });
  });

  it('renames genres and picks the most telling one', () => {
    const genre = (names: string[]) =>
      toGameDetails({
        id: 1,
        name: 'x',
        genres: names.map((name, id) => ({ id, name })),
      }).genre;
    expect(genre(['Adventure', 'Role-playing (RPG)'])).toBe('RPG');
    expect(genre(['Platform', 'Adventure'])).toBe('Platformer');
    expect(genre(['Something new'])).toBe('Something new');
    expect(genre([])).toBeNull();
  });

  it('lists known platforms once, in the order of the Platform enum', () => {
    const platforms = toGameDetails({
      id: 1,
      name: 'The Witcher 3: Wild Hunt',
      platforms: [
        { id: 130, name: 'Nintendo Switch' },
        { id: 48, name: 'PlayStation 4' },
        { id: 3, name: 'Linux' },
        { id: 6, name: 'PC (Microsoft Windows)' },
        { id: 167, name: 'PlayStation 5' },
        { id: 49, name: 'Xbox One' },
      ],
    }).platforms;
    expect(platforms).toEqual(['PC', 'PS4', 'PS5', 'XBOX_ONE', 'SWITCH']);
  });

  it('leaves out what IGDB does not know', () => {
    expect(toGameDetails({ id: 1, name: ' Doom ' })).toEqual({
      title: 'Doom',
      platforms: [],
      genre: null,
      developer: null,
      publisher: null,
      releaseYear: null,
      coverImageUrl: null,
    });
  });
});
