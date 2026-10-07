import { Platform, Region } from '../generated/prisma/enums.js';

/**
 * Platform names as shops and game databases write them. The list is ordered
 * from the most specific name, so the first pattern that matches wins.
 */
const PLATFORM_PATTERNS: ReadonlyArray<readonly [Platform, RegExp]> = [
  [Platform.SWITCH_2, /\bswitch\s*2\b|\bNS2\b/i],
  [Platform.SWITCH, /\bswitch\b|\bNSW\b/i],
  [Platform.PS_VITA, /\b(?:playstation|ps)\s*vita\b/i],
  [Platform.PSP, /\bPSP\b|\bplaystation\s+portable\b/i],
  [Platform.PS5, /\b(?:playstation|ps)\s*5\b/i],
  [Platform.PS4, /\b(?:playstation|ps)\s*4\b/i],
  [Platform.PS3, /\b(?:playstation|ps)\s*3\b/i],
  [Platform.PS2, /\b(?:playstation|ps)\s*2\b/i],
  [Platform.PS1, /\b(?:playstation|ps)\s*(?:1|one)\b|\bPSX\b|\bpsone\b/i],
  [Platform.XBOX_SERIES, /\bxbox\s+series\b|\bXSX\b/i],
  [Platform.XBOX_ONE, /\bxbox\s*one\b|\bXB1\b/i],
  [Platform.XBOX_360, /\bxbox\s*360\b|\bX360\b/i],
  [Platform.XBOX, /\bxbox\b/i],
  [Platform.WII_U, /\bwii\s*u\b/i],
  [Platform.WII, /\bwii\b/i],
  [Platform.GAMECUBE, /\bgame\s*cube\b|\bNGC\b|\bGCN\b/i],
  [Platform.N64, /\bnintendo\s*64\b|\bN64\b/i],
  [Platform.SNES, /\bSNES\b|\bsuper\s+nintendo\b|\bsuper\s+famicom\b/i],
  [Platform.NES, /\bNES\b|\bnintendo\s+entertainment\s+system\b|\bfamicom\b/i],
  // 2DS plays the 3DS library.
  [Platform.NINTENDO_3DS, /\b[23]\s*DS\b/i],
  [Platform.NINTENDO_DS, /\bDSi?\b|\bNDS\b/i],
  [Platform.GAME_BOY_ADVANCE, /\bgame\s*boy\s+advance\b|\bGBA\b/i],
  [Platform.GAME_BOY_COLOR, /\bgame\s*boy\s+colou?r\b|\bGBC\b/i],
  [Platform.GAME_BOY, /\bgame\s*boy\b/i],
  [Platform.VIRTUAL_BOY, /\bvirtual\s*boy\b/i],
  [Platform.DREAMCAST, /\bdreamcast\b/i],
  [Platform.SATURN, /\bsega\s+saturn\b/i],
  [Platform.MEGA_CD, /\b(?:mega|sega)[\s-]*cd\b/i],
  [Platform.SEGA_32X, /\b32x\b/i],
  [Platform.MEGA_DRIVE, /\bmega\s*drive\b|\bgenesis\b/i],
  [Platform.MASTER_SYSTEM, /\bmaster\s+system\b/i],
  [Platform.GAME_GEAR, /\bgame\s*gear\b/i],
  [Platform.ATARI_2600, /\batari\s*2600\b/i],
  [Platform.ATARI_7800, /\batari\s*7800\b/i],
  [Platform.ATARI_LYNX, /\batari\s+lynx\b/i],
  [Platform.ATARI_JAGUAR, /\batari\s+jaguar\b/i],
  [Platform.ATARI_ST, /\batari\s+ST\b/i],
  [Platform.ATARI_8BIT, /\batari\s+(?:400|800|XL|XE|8[\s-]*bit)\b/i],
  [Platform.NEO_GEO_POCKET, /\bneo\s*geo\s+pocket\b/i],
  [Platform.NEO_GEO, /\bneo\s*geo\b/i],
  [Platform.PC_ENGINE, /\bpc\s*engine\b|\bturbo\s*grafx\b/i],
  [Platform.THREE_DO, /\b3DO\b/i],
  [Platform.ZX_SPECTRUM, /\bzx\s*spectrum\b/i],
  [Platform.COMMODORE_64, /\bcommodore\s*64\b|\bC64\b/i],
  [Platform.AMIGA, /\bamiga\b/i],
  [Platform.AMSTRAD_CPC, /\bamstrad\b/i],
  [Platform.MSX, /\bMSX2?\b/i],
  [Platform.PC, /\bPC\b|\bwindows\b/i],
  [Platform.MAC, /\bmac(?:intosh|\s*OS)?\b|\bOS\s*X\b/i],
  // A bare "PlayStation" is the first one.
  [Platform.PS1, /\bplaystation\b(?!\s*(?:vr|plus|network|move|hits))/i],
];

/** Editions worth keeping; "Standard Edition" is matched only to be removed. */
const EDITION =
  /\b(?:(?:digital|super)\s+)?(?:standard|deluxe|gold|ultimate|premium|collector'?s|limited|special|launch|day\s*(?:one|1)|complete|definitive|game\s+of\s+the\s+year|goty|legendary|anniversary|steelbook|signature|champions?|royal|founder'?s|bonus|enhanced)\s+edition\b/i;

const REGION_PATTERNS: ReadonlyArray<readonly [Region, RegExp]> = [
  [Region.REGION_FREE, /\bregion[\s-]*free\b/i],
  [Region.NTSC_J, /\bNTSC[\s-]*J\b|\bjap(?:an|anese)?\s+(?:import|version)\b/i],
  [Region.NTSC_U, /\bNTSC\b|\b(?:US|USA)\s+(?:import|version)\b/i],
  [Region.PAL, /\bPAL\b|\b(?:UK|EU|EUR|european)\s+(?:import|version)\b/i],
];

/** Shop listing filler that is never part of a game's name. */
const NOISE =
  /\b(?:brand\s+new|factory\s+sealed|sealed|pre[\s-]*owned|renewed|(?:(?:uk|eu|eur|us|usa|jap(?:an|anese)?)\s+)?import(?:ed)?|(?:uk|eu|eur|us|usa|jap(?:an|anese)?)\s+version|pal|ntsc(?:[\s-]*[ju])?|region[\s-]*free|video\s*games?|game\s+disc|disc\s+only)\b/gi;

/** Budget re-releases; dropped when they stand alone between separators. */
const RERELEASE_LABEL =
  /\b(?:playstation\s+hits|greatest\s+hits|platinum|player'?s\s+choice|nintendo\s+selects|essentials|classics)\b/gi;

const MANUFACTURER = /\b(?:sony|microsoft|nintendo|sega|atari|snk|nec)\b/gi;

const SEPARATOR = /\s+[-–—|/]\s+|\s*\|\s*/;

/** Platform names that can be cut from the start or the end of a title. */
const EXPLICIT_PLATFORM = String.raw`(?:sony\s+)?(?:playstation|ps)\s*(?:[1-5]|vita|portable)|psp|(?:microsoft\s+)?xbox\s*(?:360|one(?:\s*[xs])?|series(?:\s*(?:x\s*[|/]\s*s|[xs]))?)`;
/** These are cut only from the end: at the start they are usually part of the name ("Wii Sports"). */
const TRAILING_PLATFORM = String.raw`${EXPLICIT_PLATFORM}|nintendo\s+(?:switch(?:\s*2)?|wii(?:\s*u)?|[23]?ds|game\s*cube|64)|switch(?:\s*2)?|wii\s*u|(?:new\s+)?[23]ds(?:\s*xl)?|nds|game\s*cube|n64|snes|gba|dreamcast|sega\s+(?:saturn|genesis|mega\s*drive)|mega\s*drive|xbox|pc(?:[\s-]*(?:dvd|cd)(?:[\s-]*rom)?)?|windows(?:\s*(?:xp|vista|7|8|10|11))?`;
const LEADING_PLATFORM_RE = new RegExp(
  String.raw`^(?:${EXPLICIT_PLATFORM})\b[\s:,-]*`,
  'i',
);
const TRAILING_PLATFORM_RE = new RegExp(
  String.raw`(?:\s+(?:for|on))?[\s:,-]+(?:${TRAILING_PLATFORM})\s*$`,
  'i',
);

export interface ParsedProductTitle {
  /** The game's name without platform, edition and shop filler. */
  title: string;
  platform: Platform | null;
  edition: string | null;
  region: Region | null;
}

/** The first platform named in `texts`, earlier texts taking precedence. */
export function detectPlatform(
  ...texts: (string | null | undefined)[]
): Platform | null {
  for (const text of texts) {
    if (!text) continue;
    const match = PLATFORM_PATTERNS.find(([, pattern]) => pattern.test(text));
    if (match) return match[0];
  }
  return null;
}

/**
 * Splits a shop listing title such as "Fallout 4 Game of the Year Edition (PS4)"
 * into the game's name, platform, edition and region. `context` (category,
 * description) is used only to find the platform when the title does not name it.
 */
export function parseProductTitle(
  rawTitle: string,
  context: (string | null | undefined)[] = [],
): ParsedProductTitle {
  const text = tidy(
    rawTitle
      .replace(/[‘’`´]/g, "'")
      .replace(/[“”]/g, '"')
      .replace(/[™®©]/g, ''),
  );

  const editionMatch = EDITION.exec(text)?.[0];
  const edition =
    editionMatch && !/^standard\b/i.test(editionMatch)
      ? titleCase(tidy(editionMatch))
      : null;
  const region =
    REGION_PATTERNS.find(([, pattern]) => pattern.test(text))?.[0] ?? null;
  const platform = detectPlatform(text, ...context);

  const withoutFiller = tidy(
    text
      .replace(/\[[^\]]*\]|\([^)]*\)|\{[^}]*\}/g, ' ')
      .replace(new RegExp(EDITION.source, 'gi'), ' ')
      .replace(NOISE, ' '),
  );
  const segments = withoutFiller
    .split(SEPARATOR)
    .map(tidy)
    .filter((segment) => !isPlatformOrLabelOnly(segment));

  let title = segments.join(' - ');
  for (let previous = ''; previous !== title;) {
    previous = title;
    title = tidy(
      title.replace(LEADING_PLATFORM_RE, '').replace(TRAILING_PLATFORM_RE, ''),
    );
  }

  return {
    title: title || withoutFiller || text,
    platform,
    edition,
    region,
  };
}

function isPlatformOrLabelOnly(segment: string): boolean {
  let rest = segment.replace(RERELEASE_LABEL, ' ');
  for (const [, pattern] of PLATFORM_PATTERNS) {
    rest = rest.replace(new RegExp(pattern.source, 'gi'), ' ');
  }
  rest = rest.replace(MANUFACTURER, ' ').replace(/\b(?:for|on|game)\b/gi, ' ');
  return !/[\p{L}\p{N}]/u.test(rest);
}

/** Collapses whitespace and trims separators and dangling punctuation. */
function tidy(text: string): string {
  return text
    .replace(/\s+/g, ' ')
    .replace(/^[\s\-–—:|,/.]+|[\s\-–—:|,/]+$/g, '')
    .trim();
}

/** Re-cases text written all in lower or upper case ("COLLECTOR'S EDITION"). */
function titleCase(text: string): string {
  if (text !== text.toLowerCase() && text !== text.toUpperCase()) return text;
  return text
    .toLowerCase()
    .replace(
      /(^|\s)(\p{L})/gu,
      (_, space: string, letter: string) => space + letter.toUpperCase(),
    );
}
