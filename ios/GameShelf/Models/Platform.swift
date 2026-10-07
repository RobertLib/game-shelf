import Foundation

enum Platform: String, APIEnum {
    case pc = "PC"
    case mac = "MAC"
    case ps1 = "PS1"
    case ps2 = "PS2"
    case ps3 = "PS3"
    case ps4 = "PS4"
    case ps5 = "PS5"
    case psp = "PSP"
    case psVita = "PS_VITA"
    case xbox = "XBOX"
    case xbox360 = "XBOX_360"
    case xboxOne = "XBOX_ONE"
    case xboxSeries = "XBOX_SERIES"
    case nes = "NES"
    case snes = "SNES"
    case n64 = "N64"
    case gameCube = "GAMECUBE"
    case wii = "WII"
    case wiiU = "WII_U"
    case `switch` = "SWITCH"
    case switch2 = "SWITCH_2"
    case gameBoy = "GAME_BOY"
    case gameBoyColor = "GAME_BOY_COLOR"
    case gameBoyAdvance = "GAME_BOY_ADVANCE"
    case nintendoDS = "NINTENDO_DS"
    case nintendo3DS = "NINTENDO_3DS"
    case virtualBoy = "VIRTUAL_BOY"
    case masterSystem = "MASTER_SYSTEM"
    case megaDrive = "MEGA_DRIVE"
    case megaCD = "MEGA_CD"
    case sega32X = "SEGA_32X"
    case saturn = "SATURN"
    case dreamcast = "DREAMCAST"
    case gameGear = "GAME_GEAR"
    case atari2600 = "ATARI_2600"
    case atari7800 = "ATARI_7800"
    case atariLynx = "ATARI_LYNX"
    case atariJaguar = "ATARI_JAGUAR"
    case neoGeo = "NEO_GEO"
    case neoGeoPocket = "NEO_GEO_POCKET"
    case pcEngine = "PC_ENGINE"
    case threeDO = "THREE_DO"
    case zxSpectrum = "ZX_SPECTRUM"
    case commodore64 = "COMMODORE_64"
    case amiga = "AMIGA"
    case amstradCPC = "AMSTRAD_CPC"
    case atari8bit = "ATARI_8BIT"
    case atariST = "ATARI_ST"
    case msx = "MSX"
    case other = "OTHER"

    static var fallback: Platform { .other }

    var label: String {
        switch self {
        case .pc: "PC"
        case .mac: "Mac"
        case .ps1: "PlayStation"
        case .ps2: "PlayStation 2"
        case .ps3: "PlayStation 3"
        case .ps4: "PlayStation 4"
        case .ps5: "PlayStation 5"
        case .psp: "PSP"
        case .psVita: "PS Vita"
        case .xbox: "Xbox"
        case .xbox360: "Xbox 360"
        case .xboxOne: "Xbox One"
        case .xboxSeries: "Xbox Series X|S"
        case .nes: "NES"
        case .snes: "SNES"
        case .n64: "Nintendo 64"
        case .gameCube: "GameCube"
        case .wii: "Wii"
        case .wiiU: "Wii U"
        case .switch: "Nintendo Switch"
        case .switch2: "Nintendo Switch 2"
        case .gameBoy: "Game Boy"
        case .gameBoyColor: "Game Boy Color"
        case .gameBoyAdvance: "Game Boy Advance"
        case .nintendoDS: "Nintendo DS"
        case .nintendo3DS: "Nintendo 3DS"
        case .virtualBoy: "Virtual Boy"
        case .masterSystem: "Master System"
        case .megaDrive: "Mega Drive / Genesis"
        case .megaCD: "Mega-CD"
        case .sega32X: "32X"
        case .saturn: "Saturn"
        case .dreamcast: "Dreamcast"
        case .gameGear: "Game Gear"
        case .atari2600: "Atari 2600"
        case .atari7800: "Atari 7800"
        case .atariLynx: "Atari Lynx"
        case .atariJaguar: "Atari Jaguar"
        case .neoGeo: "Neo Geo"
        case .neoGeoPocket: "Neo Geo Pocket"
        case .pcEngine: "PC Engine / TurboGrafx-16"
        case .threeDO: "3DO"
        case .zxSpectrum: "ZX Spectrum"
        case .commodore64: "Commodore 64"
        case .amiga: "Amiga"
        case .amstradCPC: "Amstrad CPC"
        case .atari8bit: "Atari 8-bit (XL/XE)"
        case .atariST: "Atari ST"
        case .msx: "MSX"
        case .other: "Other"
        }
    }

    /// Compact name for cover placeholders.
    var shortName: String {
        switch self {
        case .pc: "PC"
        case .mac: "Mac"
        case .ps1: "PS1"
        case .ps2: "PS2"
        case .ps3: "PS3"
        case .ps4: "PS4"
        case .ps5: "PS5"
        case .psp: "PSP"
        case .psVita: "Vita"
        case .xbox: "Xbox"
        case .xbox360: "X360"
        case .xboxOne: "XOne"
        case .xboxSeries: "XSX"
        case .nes: "NES"
        case .snes: "SNES"
        case .n64: "N64"
        case .gameCube: "GC"
        case .wii: "Wii"
        case .wiiU: "Wii U"
        case .switch: "Switch"
        case .switch2: "Switch 2"
        case .gameBoy: "GB"
        case .gameBoyColor: "GBC"
        case .gameBoyAdvance: "GBA"
        case .nintendoDS: "DS"
        case .nintendo3DS: "3DS"
        case .virtualBoy: "VB"
        case .masterSystem: "SMS"
        case .megaDrive: "MD"
        case .megaCD: "MCD"
        case .sega32X: "32X"
        case .saturn: "Saturn"
        case .dreamcast: "DC"
        case .gameGear: "GG"
        case .atari2600: "2600"
        case .atari7800: "7800"
        case .atariLynx: "Lynx"
        case .atariJaguar: "Jaguar"
        case .neoGeo: "Neo Geo"
        case .neoGeoPocket: "NGP"
        case .pcEngine: "PCE"
        case .threeDO: "3DO"
        case .zxSpectrum: "ZX"
        case .commodore64: "C64"
        case .amiga: "Amiga"
        case .amstradCPC: "CPC"
        case .atari8bit: "A8"
        case .atariST: "ST"
        case .msx: "MSX"
        case .other: "?"
        }
    }

    var group: PlatformGroup {
        switch self {
        case .pc, .mac: .pcAndMac
        case .ps1, .ps2, .ps3, .ps4, .ps5, .psp, .psVita: .sony
        case .xbox, .xbox360, .xboxOne, .xboxSeries: .microsoft
        case .nes, .snes, .n64, .gameCube, .wii, .wiiU, .switch, .switch2, .gameBoy, .gameBoyColor,
             .gameBoyAdvance, .nintendoDS, .nintendo3DS, .virtualBoy: .nintendo
        case .masterSystem, .megaDrive, .megaCD, .sega32X, .saturn, .dreamcast, .gameGear: .sega
        case .atari2600, .atari7800, .atariLynx, .atariJaguar: .atari
        case .neoGeo, .neoGeoPocket, .pcEngine, .threeDO: .otherConsoles
        case .zxSpectrum, .commodore64, .amiga, .amstradCPC, .atari8bit, .atariST, .msx: .homeComputers
        case .other: .other
        }
    }

    /// All platforms grouped by manufacturer, in display order.
    static let grouped: [(group: PlatformGroup, platforms: [Platform])] = PlatformGroup.allCases.map { group in
        (group, allCases.filter { $0.group == group })
    }
}

enum PlatformGroup: String, CaseIterable, Identifiable, Sendable {
    case pcAndMac, sony, microsoft, nintendo, sega, atari, otherConsoles, homeComputers, other

    var id: Self { self }

    var label: String {
        switch self {
        case .pcAndMac: "PC & Mac"
        case .sony: "Sony"
        case .microsoft: "Microsoft"
        case .nintendo: "Nintendo"
        case .sega: "Sega"
        case .atari: "Atari"
        case .otherConsoles: "Other consoles"
        case .homeComputers: "Home computers"
        case .other: "Other"
        }
    }
}
