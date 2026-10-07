import { detectPlatform, parseProductTitle } from './product-title.js';

describe('parseProductTitle', () => {
  it.each([
    ['Mario Kart 8 Deluxe - Nintendo Switch', 'Mario Kart 8 Deluxe', 'SWITCH'],
    [
      'Call of Duty: Black Ops III - PlayStation 4 [Digital Code]',
      'Call of Duty: Black Ops III',
      'PS4',
    ],
    [
      'Sony PlayStation 4 The Last of Us Remastered',
      'The Last of Us Remastered',
      'PS4',
    ],
    ['Halo 3 Xbox 360', 'Halo 3', 'XBOX_360'],
    ['Diablo IV - PC', 'Diablo IV', 'PC'],
    ['Gran Turismo 7 – PS5', 'Gran Turismo 7', 'PS5'],
    ['NBA 2K20 - Xbox One', 'NBA 2K20', 'XBOX_ONE'],
    ['Spider-Man (PS4)', 'Spider-Man', 'PS4'],
    [
      'Super Mario 3D World + Bowser’s Fury for Nintendo Switch',
      "Super Mario 3D World + Bowser's Fury",
      'SWITCH',
    ],
    [
      'Red Dead Redemption 2 PS4 Brand New Factory Sealed',
      'Red Dead Redemption 2',
      'PS4',
    ],
    [
      'Uncharted 4: A Thief’s End - PlayStation Hits (PS4)',
      "Uncharted 4: A Thief's End",
      'PS4',
    ],
    ['Final Fantasy VII - PlayStation', 'Final Fantasy VII', 'PS1'],
    ['Pokémon Platinum - Nintendo DS', 'Pokémon Platinum', 'NINTENDO_DS'],
    [
      'Kirby: Planet Robobot (Nintendo 3DS)',
      'Kirby: Planet Robobot',
      'NINTENDO_3DS',
    ],
  ])('%s → %s on %s', (raw, title, platform) => {
    expect(parseProductTitle(raw)).toMatchObject({ title, platform });
  });

  it('keeps platform names that are part of the game name', () => {
    expect(parseProductTitle('Wii Sports - Nintendo Wii')).toMatchObject({
      title: 'Wii Sports',
      platform: 'WII',
    });
    expect(parseProductTitle('Mario Kart Wii').title).toBe('Mario Kart Wii');
    expect(
      parseProductTitle('Nintendo Switch Sports - Nintendo Switch').title,
    ).toBe('Nintendo Switch Sports');
  });

  it('takes the edition out of the title and drops "Standard Edition"', () => {
    expect(
      parseProductTitle('Fallout 4 Game of the Year Edition (PS4)'),
    ).toEqual({
      title: 'Fallout 4',
      platform: 'PS4',
      edition: 'Game of the Year Edition',
      region: null,
    });
    expect(
      parseProductTitle('GOD OF WAR RAGNAROK LAUNCH EDITION - PS5'),
    ).toMatchObject({
      title: 'GOD OF WAR RAGNAROK',
      edition: 'Launch Edition',
    });
    expect(
      parseProductTitle(
        'The Legend of Zelda: Breath of the Wild - Nintendo Switch Standard Edition',
      ),
    ).toMatchObject({
      title: 'The Legend of Zelda: Breath of the Wild',
      edition: null,
    });
  });

  it('recognises the region', () => {
    expect(parseProductTitle('FIFA 19 (PS4) [UK IMPORT]')).toMatchObject({
      title: 'FIFA 19',
      region: 'PAL',
    });
    expect(parseProductTitle('Persona 5 (NTSC-J)').region).toBe('NTSC_J');
    expect(parseProductTitle('Halo 3 NTSC Xbox 360').region).toBe('NTSC_U');
    expect(parseProductTitle('Gran Turismo 7 (PS5)').region).toBeNull();
  });

  it('finds the platform in the context when the title does not name it', () => {
    expect(
      parseProductTitle('Super Mario Odyssey', [
        'Video Games > Nintendo Switch > Games',
      ]).platform,
    ).toBe('SWITCH');
    expect(parseProductTitle('Super Mario Odyssey').platform).toBeNull();
  });

  it('never returns an empty title', () => {
    expect(parseProductTitle('PS4').title).toBe('PS4');
  });
});

describe('detectPlatform', () => {
  it.each([
    ['PlayStation 5', 'PS5'],
    ['Xbox Series X|S', 'XBOX_SERIES'],
    ['Nintendo Switch 2', 'SWITCH_2'],
    ['Super Nintendo Entertainment System', 'SNES'],
    ['Nintendo Entertainment System', 'NES'],
    ['Sega Mega Drive/Genesis', 'MEGA_DRIVE'],
    ['PC (Microsoft Windows)', 'PC'],
    ['TurboGrafx-16/PC Engine', 'PC_ENGINE'],
    ['Commodore C64/128/MAX', 'COMMODORE_64'],
    ['Game Boy Advance', 'GAME_BOY_ADVANCE'],
    ['New Nintendo 3DS', 'NINTENDO_3DS'],
    ['PlayStation Vita', 'PS_VITA'],
    ['Linux', null],
  ])('%s → %s', (name, platform) => {
    expect(detectPlatform(name)).toBe(platform);
  });
});
