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
    [
      'answers with something else than a list',
      () => Promise.resolve(Response.json({ message: 'oops' })),
    ],
  ])('reports that IGDB %s, unlike no match', async (_, answer) => {
    games = answer;
    await expect(
      new IgdbClient(config(true)).findGame('Doom', null),
    ).rejects.toBeInstanceOf(LookupUnavailableError);
  });

  it('leaves out games without a name', async () => {
    games = () =>
      Promise.resolve(Response.json([{ id: 1, game_type: 0 }, DOOM]));
    await expect(
      new IgdbClient(config(true)).findGame('Doom', null),
    ).resolves.toMatchObject({ title: 'Doom' });
  });

  it('renews a rejected access token once', async () => {
    const answers = [
      new Response(null, { status: 401 }),
      Response.json([DOOM]),
    ];
    games = () => Promise.resolve(answers.shift() ?? Response.json([]));
    await expect(
      new IgdbClient(config(true)).findGame('Doom', null),
    ).resolves.toMatchObject({ title: 'Doom' });
    const tokenRequests = fetchMock.mock.calls.filter(([url]) =>
      String(url).includes('twitch.tv'),
    );
    expect(tokenRequests).toHaveLength(2);
  });
});

describe('IgdbClient.searchGames', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('needs credentials', async () => {
    await expect(
      new IgdbClient(config(false)).searchGames('Doom', 20),
    ).rejects.toBeInstanceOf(LookupUnavailableError);
  });

  it('asks for the games themselves, not their editions', async () => {
    const fetchMock = vi.fn((url: string | URL, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('twitch.tv')
          ? Response.json({ access_token: 't', expires_in: 3600 })
          : Response.json([DOOM]),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      new IgdbClient(config(true)).searchGames('Doom', 20),
    ).resolves.toEqual([DOOM]);
    const [, init] = fetchMock.mock.calls.at(-1) ?? [];
    expect(init?.body).toContain('where version_parent = null; limit 20;');
  });
});
