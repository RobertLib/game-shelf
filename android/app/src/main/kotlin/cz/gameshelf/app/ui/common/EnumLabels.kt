package cz.gameshelf.app.ui.common

import androidx.annotation.StringRes
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Completeness
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.GameSortField
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.PlatformGroup
import cz.gameshelf.app.domain.model.PlayStatus
import cz.gameshelf.app.domain.model.Region

@get:StringRes
val Platform.labelRes: Int
    get() = when (this) {
        Platform.PC -> R.string.platform_pc
        Platform.MAC -> R.string.platform_mac
        Platform.PS1 -> R.string.platform_ps1
        Platform.PS2 -> R.string.platform_ps2
        Platform.PS3 -> R.string.platform_ps3
        Platform.PS4 -> R.string.platform_ps4
        Platform.PS5 -> R.string.platform_ps5
        Platform.PSP -> R.string.platform_psp
        Platform.PS_VITA -> R.string.platform_ps_vita
        Platform.XBOX -> R.string.platform_xbox
        Platform.XBOX_360 -> R.string.platform_xbox_360
        Platform.XBOX_ONE -> R.string.platform_xbox_one
        Platform.XBOX_SERIES -> R.string.platform_xbox_series
        Platform.NES -> R.string.platform_nes
        Platform.SNES -> R.string.platform_snes
        Platform.N64 -> R.string.platform_n64
        Platform.GAMECUBE -> R.string.platform_gamecube
        Platform.WII -> R.string.platform_wii
        Platform.WII_U -> R.string.platform_wii_u
        Platform.SWITCH -> R.string.platform_switch
        Platform.SWITCH_2 -> R.string.platform_switch_2
        Platform.GAME_BOY -> R.string.platform_game_boy
        Platform.GAME_BOY_COLOR -> R.string.platform_game_boy_color
        Platform.GAME_BOY_ADVANCE -> R.string.platform_game_boy_advance
        Platform.NINTENDO_DS -> R.string.platform_nintendo_ds
        Platform.NINTENDO_3DS -> R.string.platform_nintendo_3ds
        Platform.VIRTUAL_BOY -> R.string.platform_virtual_boy
        Platform.MASTER_SYSTEM -> R.string.platform_master_system
        Platform.MEGA_DRIVE -> R.string.platform_mega_drive
        Platform.MEGA_CD -> R.string.platform_mega_cd
        Platform.SEGA_32X -> R.string.platform_sega_32x
        Platform.SATURN -> R.string.platform_saturn
        Platform.DREAMCAST -> R.string.platform_dreamcast
        Platform.GAME_GEAR -> R.string.platform_game_gear
        Platform.ATARI_2600 -> R.string.platform_atari_2600
        Platform.ATARI_7800 -> R.string.platform_atari_7800
        Platform.ATARI_LYNX -> R.string.platform_atari_lynx
        Platform.ATARI_JAGUAR -> R.string.platform_atari_jaguar
        Platform.NEO_GEO -> R.string.platform_neo_geo
        Platform.NEO_GEO_POCKET -> R.string.platform_neo_geo_pocket
        Platform.PC_ENGINE -> R.string.platform_pc_engine
        Platform.THREE_DO -> R.string.platform_three_do
        Platform.ZX_SPECTRUM -> R.string.platform_zx_spectrum
        Platform.COMMODORE_64 -> R.string.platform_commodore_64
        Platform.AMIGA -> R.string.platform_amiga
        Platform.AMSTRAD_CPC -> R.string.platform_amstrad_cpc
        Platform.ATARI_8BIT -> R.string.platform_atari_8bit
        Platform.ATARI_ST -> R.string.platform_atari_st
        Platform.MSX -> R.string.platform_msx
        Platform.OTHER -> R.string.platform_other
    }

@get:StringRes
val Platform.shortLabelRes: Int
    get() = when (this) {
        Platform.PC -> R.string.platform_short_pc
        Platform.MAC -> R.string.platform_short_mac
        Platform.PS1 -> R.string.platform_short_ps1
        Platform.PS2 -> R.string.platform_short_ps2
        Platform.PS3 -> R.string.platform_short_ps3
        Platform.PS4 -> R.string.platform_short_ps4
        Platform.PS5 -> R.string.platform_short_ps5
        Platform.PSP -> R.string.platform_short_psp
        Platform.PS_VITA -> R.string.platform_short_ps_vita
        Platform.XBOX -> R.string.platform_short_xbox
        Platform.XBOX_360 -> R.string.platform_short_xbox_360
        Platform.XBOX_ONE -> R.string.platform_short_xbox_one
        Platform.XBOX_SERIES -> R.string.platform_short_xbox_series
        Platform.NES -> R.string.platform_short_nes
        Platform.SNES -> R.string.platform_short_snes
        Platform.N64 -> R.string.platform_short_n64
        Platform.GAMECUBE -> R.string.platform_short_gamecube
        Platform.WII -> R.string.platform_short_wii
        Platform.WII_U -> R.string.platform_short_wii_u
        Platform.SWITCH -> R.string.platform_short_switch
        Platform.SWITCH_2 -> R.string.platform_short_switch_2
        Platform.GAME_BOY -> R.string.platform_short_game_boy
        Platform.GAME_BOY_COLOR -> R.string.platform_short_game_boy_color
        Platform.GAME_BOY_ADVANCE -> R.string.platform_short_game_boy_advance
        Platform.NINTENDO_DS -> R.string.platform_short_nintendo_ds
        Platform.NINTENDO_3DS -> R.string.platform_short_nintendo_3ds
        Platform.VIRTUAL_BOY -> R.string.platform_short_virtual_boy
        Platform.MASTER_SYSTEM -> R.string.platform_short_master_system
        Platform.MEGA_DRIVE -> R.string.platform_short_mega_drive
        Platform.MEGA_CD -> R.string.platform_short_mega_cd
        Platform.SEGA_32X -> R.string.platform_short_sega_32x
        Platform.SATURN -> R.string.platform_short_saturn
        Platform.DREAMCAST -> R.string.platform_short_dreamcast
        Platform.GAME_GEAR -> R.string.platform_short_game_gear
        Platform.ATARI_2600 -> R.string.platform_short_atari_2600
        Platform.ATARI_7800 -> R.string.platform_short_atari_7800
        Platform.ATARI_LYNX -> R.string.platform_short_atari_lynx
        Platform.ATARI_JAGUAR -> R.string.platform_short_atari_jaguar
        Platform.NEO_GEO -> R.string.platform_short_neo_geo
        Platform.NEO_GEO_POCKET -> R.string.platform_short_neo_geo_pocket
        Platform.PC_ENGINE -> R.string.platform_short_pc_engine
        Platform.THREE_DO -> R.string.platform_short_three_do
        Platform.ZX_SPECTRUM -> R.string.platform_short_zx_spectrum
        Platform.COMMODORE_64 -> R.string.platform_short_commodore_64
        Platform.AMIGA -> R.string.platform_short_amiga
        Platform.AMSTRAD_CPC -> R.string.platform_short_amstrad_cpc
        Platform.ATARI_8BIT -> R.string.platform_short_atari_8bit
        Platform.ATARI_ST -> R.string.platform_short_atari_st
        Platform.MSX -> R.string.platform_short_msx
        Platform.OTHER -> R.string.platform_short_other
    }

@get:StringRes
val PlatformGroup.labelRes: Int
    get() = when (this) {
        PlatformGroup.PC_MAC -> R.string.platform_group_pc_mac
        PlatformGroup.SONY -> R.string.platform_group_sony
        PlatformGroup.MICROSOFT -> R.string.platform_group_microsoft
        PlatformGroup.NINTENDO -> R.string.platform_group_nintendo
        PlatformGroup.SEGA -> R.string.platform_group_sega
        PlatformGroup.ATARI -> R.string.platform_group_atari
        PlatformGroup.OTHER_CONSOLES -> R.string.platform_group_other_consoles
        PlatformGroup.HOME_COMPUTERS -> R.string.platform_group_home_computers
        PlatformGroup.OTHER -> R.string.platform_group_other
    }

@get:StringRes
val CollectionStatus.labelRes: Int
    get() = when (this) {
        CollectionStatus.OWNED -> R.string.status_owned
        CollectionStatus.WISHLIST -> R.string.status_wishlist
        CollectionStatus.PREORDERED -> R.string.status_preordered
        CollectionStatus.LENT -> R.string.status_lent
        CollectionStatus.FOR_SALE -> R.string.status_for_sale
        CollectionStatus.SOLD -> R.string.status_sold
        CollectionStatus.UNKNOWN -> R.string.enum_unknown
    }

@get:StringRes
val GameFormat.labelRes: Int
    get() = when (this) {
        GameFormat.PHYSICAL -> R.string.format_physical
        GameFormat.DIGITAL -> R.string.format_digital
        GameFormat.UNKNOWN -> R.string.enum_unknown
    }

@get:StringRes
val Region.labelRes: Int
    get() = when (this) {
        Region.PAL -> R.string.region_pal
        Region.NTSC_U -> R.string.region_ntsc_u
        Region.NTSC_J -> R.string.region_ntsc_j
        Region.REGION_FREE -> R.string.region_region_free
        Region.OTHER -> R.string.region_other
    }

@get:StringRes
val Completeness.labelRes: Int
    get() = when (this) {
        Completeness.SEALED -> R.string.completeness_sealed
        Completeness.CIB -> R.string.completeness_cib
        Completeness.GAME_AND_BOX -> R.string.completeness_game_and_box
        Completeness.GAME_AND_MANUAL -> R.string.completeness_game_and_manual
        Completeness.LOOSE -> R.string.completeness_loose
        Completeness.BOX_ONLY -> R.string.completeness_box_only
        Completeness.UNKNOWN -> R.string.enum_unknown
    }

@get:StringRes
val Condition.labelRes: Int
    get() = when (this) {
        Condition.MINT -> R.string.condition_mint
        Condition.NEAR_MINT -> R.string.condition_near_mint
        Condition.VERY_GOOD -> R.string.condition_very_good
        Condition.GOOD -> R.string.condition_good
        Condition.FAIR -> R.string.condition_fair
        Condition.POOR -> R.string.condition_poor
        Condition.UNKNOWN -> R.string.enum_unknown
    }

@get:StringRes
val PlayStatus.labelRes: Int
    get() = when (this) {
        PlayStatus.UNPLAYED -> R.string.play_status_unplayed
        PlayStatus.PLAYING -> R.string.play_status_playing
        PlayStatus.COMPLETED -> R.string.play_status_completed
        PlayStatus.ABANDONED -> R.string.play_status_abandoned
        PlayStatus.UNKNOWN -> R.string.enum_unknown
    }

@get:StringRes
val GameSortField.labelRes: Int
    get() = when (this) {
        GameSortField.TITLE -> R.string.sort_title
        GameSortField.PLATFORM -> R.string.sort_platform
        GameSortField.RELEASE_YEAR -> R.string.sort_release_year
        GameSortField.PURCHASE_DATE -> R.string.sort_purchase_date
        GameSortField.PURCHASE_PRICE -> R.string.sort_purchase_price
        GameSortField.ESTIMATED_VALUE -> R.string.sort_estimated_value
        GameSortField.RATING -> R.string.sort_rating
        GameSortField.CREATED_AT -> R.string.sort_created_at
        GameSortField.UPDATED_AT -> R.string.sort_updated_at
    }
