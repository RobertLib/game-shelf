import type { ConfigService } from '@nestjs/config';
import type { Env } from '../config/env.js';
import { IgdbClient } from './igdb.client.js';
import type { IgdbGame } from './igdb-match.js';
import { LookupUnavailableError } from './lookup-unavailable.error.js';

const DOOM: IgdbGame = {
  id: 673,
  name: 'Doom',
  game_type: 0,
  platforms: [{ id: 6, name: 'PC (Microsoft Windows)' }],
};

const config = (credentials: boolean) =>
  ({
    get: (key: string) =>
      credentials
        ? { IGDB_CLIENT_ID: 'id', IGDB_CLIENT_SECRET: 'secret' }[key]
        : undefined,
  }) as unknown as ConfigService<Env, true>;

describe('IgdbClient.findGame', () => {
  /** What the games endpoint answers; the token endpoint always succeeds. */
  let games: () => Promise<Response>;
  const fetchMock = vi.fn((url: string | URL) =>
    String(url).includes('twitch.tv')
      ? Promise.resolve(Response.json({ access_token: 't', expires_in: 3600 }))
      : games(),
  );

  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockClear();
  });

  it('returns the best match', async () => {
    games = () => Promise.resolve(Response.json([DOOM]));
    await expect(
      new IgdbClient(config(true)).findGame('Doom', 'PC'),
    ).resolves.toMatchObject({ title: 'Doom', platforms: ['PC'] });
  });

  it('returns null when nothing matches', async () => {
    games = () => Promise.resolve(Response.json([DOOM]));
    await expect(
      new IgdbClient(config(true)).findGame('Quake', null),
    ).resolves.toBeNull();
  });

  it('returns null without credentials, without asking', async () => {
    await expect(
      new IgdbClient(config(false)).findGame('Doom', null),
    ).resolves.toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    [
      'answers with an error',
      () => Promise.resolve(new Response(null, { status: 500 })),
    ],
    ['cannot be reached', () => Promise.reject(new TypeError('fetch failed'))],
  ])('reports that IGDB %s, unlike no match', async (_, answer) => {
    games = answer;
    await expect(
      new IgdbClient(config(true)).findGame('Doom', null),
    ).rejects.toBeInstanceOf(LookupUnavailableError);
  });
});
