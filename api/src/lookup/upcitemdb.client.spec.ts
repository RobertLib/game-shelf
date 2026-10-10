import type { ConfigService } from '@nestjs/config';
import type { Env } from '../config/env.js';
import { LookupUnavailableError } from './lookup-unavailable.error.js';
import { UpcItemDbClient } from './upcitemdb.client.js';

const config = (userKey?: string) =>
  ({
    get: (key: string) => (key === 'UPCITEMDB_USER_KEY' ? userKey : undefined),
  }) as unknown as ConfigService<Env, true>;

describe('UpcItemDbClient', () => {
  const fetchMock =
    vi.fn<(url: URL, init?: RequestInit) => Promise<Response>>();

  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it('returns the first listing with a title, trimmed', async () => {
    fetchMock.mockResolvedValue(
      Response.json({
        items: [
          { title: '  ' },
          {
            title: ' Mario Kart 8 Deluxe - Nintendo Switch ',
            brand: ' Nintendo ',
            category: '',
            images: ['https://example.com/a.jpg'],
          },
        ],
      }),
    );
    await expect(
      new UpcItemDbClient(config()).lookup('045496590741'),
    ).resolves.toEqual({
      title: 'Mario Kart 8 Deluxe - Nintendo Switch',
      brand: 'Nintendo',
      category: null,
      description: null,
      images: ['https://example.com/a.jpg'],
    });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url.href).toBe(
      'https://api.upcitemdb.com/prod/trial/lookup?upc=045496590741',
    );
    expect(init?.headers).not.toHaveProperty('user_key');
  });

  it('uses the paid endpoint with a user key', async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [] }));
    await expect(
      new UpcItemDbClient(config('secret')).lookup('045496590741'),
    ).resolves.toBeNull();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url.pathname).toBe('/prod/v1/lookup');
    expect(init?.headers).toMatchObject({
      user_key: 'secret',
      key_type: '3scale',
    });
  });

  it.each([404, 400])('takes %i for an unknown code', async (status) => {
    fetchMock.mockResolvedValue(new Response(null, { status }));
    await expect(
      new UpcItemDbClient(config()).lookup('12345670'),
    ).resolves.toBeNull();
  });

  it.each([
    ['429 (the daily trial quota)', () => new Response(null, { status: 429 })],
    ['500', () => new Response(null, { status: 500 })],
    ['a body that is not JSON', () => new Response('<html>')],
  ])('reports UPCitemdb as unavailable on %s', async (_, response) => {
    fetchMock.mockImplementation(() => Promise.resolve(response()));
    await expect(
      new UpcItemDbClient(config()).lookup('12345670'),
    ).rejects.toBeInstanceOf(LookupUnavailableError);
  });

  it('reports UPCitemdb as unavailable when it cannot be reached or times out', async () => {
    fetchMock.mockRejectedValue(
      new DOMException('The operation timed out.', 'TimeoutError'),
    );
    await expect(
      new UpcItemDbClient(config()).lookup('12345670'),
    ).rejects.toBeInstanceOf(LookupUnavailableError);
  });
});
