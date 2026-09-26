-- Game Shelf — initial schema
-- Run this in the Supabase SQL editor (or with `supabase db push`).
--
-- Tables
--   platforms    reference list of consoles / computers (read-only for users)
--   games        a user's collection; every row belongs to exactly one user
--   game_images  photos of a game (cover, back, cartridge, …) stored in Storage
--
-- Every user-owned row is protected by row level security, so users can only
-- ever see and change their own games and images.

create extension if not exists pg_trgm with schema extensions;

-- ---------------------------------------------------------------------------
-- Enums
-- ---------------------------------------------------------------------------

create type public.game_region as enum (
  'pal', 'ntsc_u', 'ntsc_j', 'ntsc_k', 'ntsc_c', 'region_free', 'other'
);

create type public.game_format as enum ('physical', 'digital');

create type public.game_condition as enum (
  'sealed', 'mint', 'very_good', 'good', 'fair', 'poor'
);

create type public.game_completeness as enum (
  'complete', 'game_box', 'loose', 'box_only'
);

create type public.play_status as enum (
  'not_started', 'playing', 'completed', 'abandoned'
);

-- ---------------------------------------------------------------------------
-- Platforms
-- ---------------------------------------------------------------------------

create table public.platforms (
  id text primary key,
  name text not null,
  short_name text not null,
  manufacturer text not null,
  release_year smallint,
  sort_order smallint not null default 0
);

alter table public.platforms enable row level security;

create policy "Signed-in users can read platforms"
  on public.platforms for select
  to authenticated
  using (true);

insert into public.platforms (id, name, short_name, manufacturer, release_year, sort_order) values
  -- Nintendo
  ('nes',                  'Nintendo Entertainment System', 'NES',       'Nintendo',  1983, 100),
  ('snes',                 'Super Nintendo',                'SNES',      'Nintendo',  1990, 101),
  ('n64',                  'Nintendo 64',                   'N64',       'Nintendo',  1996, 102),
  ('gamecube',             'GameCube',                      'GameCube',  'Nintendo',  2001, 103),
  ('wii',                  'Wii',                           'Wii',       'Nintendo',  2006, 104),
  ('wii_u',                'Wii U',                         'Wii U',     'Nintendo',  2012, 105),
  ('switch',               'Nintendo Switch',               'Switch',    'Nintendo',  2017, 106),
  ('switch_2',             'Nintendo Switch 2',             'Switch 2',  'Nintendo',  2025, 107),
  ('game_boy',             'Game Boy',                      'GB',        'Nintendo',  1989, 110),
  ('game_boy_color',       'Game Boy Color',                'GBC',       'Nintendo',  1998, 111),
  ('game_boy_advance',     'Game Boy Advance',              'GBA',       'Nintendo',  2001, 112),
  ('nintendo_ds',          'Nintendo DS',                   'DS',        'Nintendo',  2004, 113),
  ('nintendo_3ds',         'Nintendo 3DS',                  '3DS',       'Nintendo',  2011, 114),
  ('virtual_boy',          'Virtual Boy',                   'VB',        'Nintendo',  1995, 115),
  -- Sony
  ('ps1',                  'PlayStation',                   'PS1',       'Sony',      1994, 200),
  ('ps2',                  'PlayStation 2',                 'PS2',       'Sony',      2000, 201),
  ('ps3',                  'PlayStation 3',                 'PS3',       'Sony',      2006, 202),
  ('ps4',                  'PlayStation 4',                 'PS4',       'Sony',      2013, 203),
  ('ps5',                  'PlayStation 5',                 'PS5',       'Sony',      2020, 204),
  ('psp',                  'PlayStation Portable',          'PSP',       'Sony',      2004, 210),
  ('ps_vita',              'PlayStation Vita',              'Vita',      'Sony',      2011, 211),
  -- Microsoft
  ('xbox',                 'Xbox',                          'Xbox',      'Microsoft', 2001, 300),
  ('xbox_360',             'Xbox 360',                      'X360',      'Microsoft', 2005, 301),
  ('xbox_one',             'Xbox One',                      'XONE',      'Microsoft', 2013, 302),
  ('xbox_series',          'Xbox Series X|S',               'XSX|S',     'Microsoft', 2020, 303),
  -- Sega
  ('sg_1000',              'SG-1000',                       'SG-1000',   'Sega',      1983, 400),
  ('master_system',        'Master System',                 'SMS',       'Sega',      1985, 401),
  ('mega_drive',           'Mega Drive / Genesis',          'MD',        'Sega',      1988, 402),
  ('sega_cd',              'Mega-CD / Sega CD',             'Sega CD',   'Sega',      1991, 403),
  ('sega_32x',             '32X',                           '32X',       'Sega',      1994, 404),
  ('saturn',               'Saturn',                        'Saturn',    'Sega',      1994, 405),
  ('dreamcast',            'Dreamcast',                     'DC',        'Sega',      1998, 406),
  ('game_gear',            'Game Gear',                     'GG',        'Sega',      1990, 410),
  -- Atari
  ('atari_2600',           'Atari 2600',                    '2600',      'Atari',     1977, 500),
  ('atari_5200',           'Atari 5200',                    '5200',      'Atari',     1982, 501),
  ('atari_7800',           'Atari 7800',                    '7800',      'Atari',     1986, 502),
  ('atari_jaguar',         'Atari Jaguar',                  'Jaguar',    'Atari',     1993, 503),
  ('atari_lynx',           'Atari Lynx',                    'Lynx',      'Atari',     1989, 510),
  -- NEC
  ('pc_engine',            'PC Engine / TurboGrafx-16',     'PCE',       'NEC',       1987, 600),
  ('pc_engine_cd',         'PC Engine CD / TurboGrafx-CD',  'PCE CD',    'NEC',       1988, 601),
  -- SNK
  ('neo_geo_aes',          'Neo Geo AES',                   'AES',       'SNK',       1990, 700),
  ('neo_geo_cd',           'Neo Geo CD',                    'NGCD',      'SNK',       1994, 701),
  ('neo_geo_pocket_color', 'Neo Geo Pocket Color',          'NGPC',      'SNK',       1999, 710),
  -- Other consoles & handhelds
  ('intellivision',        'Intellivision',                 'Intv',      'Mattel',    1979, 800),
  ('colecovision',         'ColecoVision',                  'Coleco',    'Coleco',    1982, 801),
  ('cd_i',                 'CD-i',                          'CD-i',      'Philips',   1991, 802),
  ('3do',                  '3DO Interactive Multiplayer',   '3DO',       'Panasonic', 1993, 803),
  ('wonderswan',           'WonderSwan',                    'WS',        'Bandai',    1999, 804),
  ('steam_deck',           'Steam Deck',                    'Deck',      'Valve',     2022, 805),
  ('evercade',             'Evercade',                      'Evercade',  'Blaze',     2020, 806),
  ('playdate',             'Playdate',                      'Playdate',  'Panic',     2022, 807),
  -- Computers
  ('zx_spectrum',          'ZX Spectrum',                   'ZX',        'Computers', 1982, 900),
  ('c64',                  'Commodore 64',                  'C64',       'Computers', 1982, 901),
  ('msx',                  'MSX',                           'MSX',       'Computers', 1983, 902),
  ('amstrad_cpc',          'Amstrad CPC',                   'CPC',       'Computers', 1984, 903),
  ('amiga',                'Amiga',                         'Amiga',     'Computers', 1985, 904),
  ('pc',                   'PC',                            'PC',        'Computers', null, 910),
  ('mac',                  'Mac',                           'Mac',       'Computers', null, 911),
  -- Fallback
  ('other',                'Other',                         'Other',     'Other',     null, 999);

-- ---------------------------------------------------------------------------
-- Games
-- ---------------------------------------------------------------------------

create table public.games (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  platform_id text not null references public.platforms (id),

  title text not null check (char_length(btrim(title)) between 1 and 200),
  edition text check (char_length(edition) <= 100),
  region public.game_region,
  format public.game_format not null default 'physical',
  condition public.game_condition,
  completeness public.game_completeness,
  genres text[] not null default '{}' check (cardinality(genres) <= 10),
  developer text check (char_length(developer) <= 100),
  publisher text check (char_length(publisher) <= 100),
  release_year smallint check (release_year between 1950 and 2100),
  serial_number text check (char_length(serial_number) <= 100),

  purchase_date date,
  purchase_price numeric(10, 2) check (purchase_price >= 0),
  current_value numeric(10, 2) check (current_value >= 0),
  currency text not null default 'USD' check (currency ~ '^[A-Z]{3}$'),
  purchased_from text check (char_length(purchased_from) <= 100),

  play_status public.play_status not null default 'not_started',
  rating smallint check (rating between 1 and 5),
  is_favorite boolean not null default false,
  notes text check (char_length(notes) <= 5000),

  -- Lower-cased haystack for the library search box.
  search_text text generated always as (
    lower(
      title || ' ' ||
      coalesce(edition, '') || ' ' ||
      coalesce(developer, '') || ' ' ||
      coalesce(publisher, '') || ' ' ||
      coalesce(serial_number, '')
    )
  ) stored,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index games_user_created_idx on public.games (user_id, created_at desc);
create index games_user_title_idx on public.games (user_id, title);
create index games_user_platform_idx on public.games (user_id, platform_id);
create index games_platform_idx on public.games (platform_id);
create index games_genres_idx on public.games using gin (genres);
create index games_search_idx on public.games using gin (search_text extensions.gin_trgm_ops);

create function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger games_set_updated_at
  before update on public.games
  for each row execute function public.set_updated_at();

alter table public.games enable row level security;

create policy "Users can read their own games"
  on public.games for select
  to authenticated
  using ((select auth.uid()) = user_id);

create policy "Users can add games to their own library"
  on public.games for insert
  to authenticated
  with check ((select auth.uid()) = user_id);

create policy "Users can update their own games"
  on public.games for update
  to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy "Users can delete their own games"
  on public.games for delete
  to authenticated
  using ((select auth.uid()) = user_id);

-- ---------------------------------------------------------------------------
-- Game images
-- ---------------------------------------------------------------------------

create table public.game_images (
  id uuid primary key default gen_random_uuid(),
  game_id uuid not null references public.games (id) on delete cascade,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  -- Paths inside the `game-images` bucket: "<user_id>/<game_id>/<image_id>.jpg"
  storage_path text not null unique,
  thumb_path text not null unique,
  position smallint not null default 0,
  width integer,
  height integer,
  created_at timestamptz not null default now(),

  check (storage_path like user_id::text || '/%'),
  check (thumb_path like user_id::text || '/%')
);

create index game_images_game_idx on public.game_images (game_id, position);
create index game_images_user_idx on public.game_images (user_id);

alter table public.game_images enable row level security;

create policy "Users can read their own game images"
  on public.game_images for select
  to authenticated
  using ((select auth.uid()) = user_id);

create policy "Users can add images to their own games"
  on public.game_images for insert
  to authenticated
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1 from public.games g
      where g.id = game_id and g.user_id = (select auth.uid())
    )
  );

create policy "Users can update their own game images"
  on public.game_images for update
  to authenticated
  using ((select auth.uid()) = user_id)
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1 from public.games g
      where g.id = game_id and g.user_id = (select auth.uid())
    )
  );

create policy "Users can delete their own game images"
  on public.game_images for delete
  to authenticated
  using ((select auth.uid()) = user_id);

-- ---------------------------------------------------------------------------
-- API access (explicit, in case the project does not auto-expose new tables)
-- ---------------------------------------------------------------------------

grant select on public.platforms to authenticated;
grant select, insert, update, delete on public.games to authenticated;
grant select, insert, update, delete on public.game_images to authenticated;

-- ---------------------------------------------------------------------------
-- Collection statistics (runs with the caller's permissions, so RLS applies)
-- ---------------------------------------------------------------------------

create function public.collection_stats()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with mine as (
    select * from public.games where user_id = (select auth.uid())
  )
  select jsonb_build_object(
    'total_games', (select count(*) from mine),
    'favorites', (select count(*) from mine where is_favorite),
    'platforms', coalesce((
      select jsonb_agg(jsonb_build_object('platform_id', platform_id, 'count', n) order by n desc, platform_id)
      from (select platform_id, count(*) as n from mine group by platform_id) p
    ), '[]'::jsonb),
    'play_status', coalesce((
      select jsonb_object_agg(play_status, n)
      from (select play_status, count(*) as n from mine group by play_status) s
    ), '{}'::jsonb),
    'totals', coalesce((
      select jsonb_agg(jsonb_build_object('currency', currency, 'spent', spent, 'value', value) order by currency)
      from (
        select currency,
               coalesce(sum(purchase_price), 0) as spent,
               coalesce(sum(coalesce(current_value, purchase_price)), 0) as value
        from mine
        where purchase_price is not null or current_value is not null
        group by currency
      ) t
    ), '[]'::jsonb)
  );
$$;

revoke execute on function public.collection_stats() from public, anon;
grant execute on function public.collection_stats() to authenticated;

-- ---------------------------------------------------------------------------
-- Storage: private bucket, one folder per user ("<user_id>/…")
-- ---------------------------------------------------------------------------

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('game-images', 'game-images', false, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

create policy "Users can read their own game image files"
  on storage.objects for select
  to authenticated
  using (
    bucket_id = 'game-images'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

create policy "Users can upload game image files to their own folder"
  on storage.objects for insert
  to authenticated
  with check (
    bucket_id = 'game-images'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

create policy "Users can update their own game image files"
  on storage.objects for update
  to authenticated
  using (
    bucket_id = 'game-images'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

create policy "Users can delete their own game image files"
  on storage.objects for delete
  to authenticated
  using (
    bucket_id = 'game-images'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );
