package cz.gameshelf.app.domain.model

import cz.gameshelf.app.domain.serialization.ApiEnum
import cz.gameshelf.app.domain.serialization.LenientEnumSerializer
import kotlinx.serialization.Serializable

/** Manufacturer groups used to organise platform pickers, in display order. */
enum class PlatformGroup { PC_MAC, SONY, MICROSOFT, NINTENDO, SEGA, ATARI, OTHER_CONSOLES, HOME_COMPUTERS, OTHER }

@Serializable(with = PlatformSerializer::class)
enum class Platform(val group: PlatformGroup) : ApiEnum {
    PC(PlatformGroup.PC_MAC),
    MAC(PlatformGroup.PC_MAC),
    PS1(PlatformGroup.SONY),
    PS2(PlatformGroup.SONY),
    PS3(PlatformGroup.SONY),
    PS4(PlatformGroup.SONY),
    PS5(PlatformGroup.SONY),
    PSP(PlatformGroup.SONY),
    PS_VITA(PlatformGroup.SONY),
    XBOX(PlatformGroup.MICROSOFT),
    XBOX_360(PlatformGroup.MICROSOFT),
    XBOX_ONE(PlatformGroup.MICROSOFT),
    XBOX_SERIES(PlatformGroup.MICROSOFT),
    NES(PlatformGroup.NINTENDO),
    SNES(PlatformGroup.NINTENDO),
    N64(PlatformGroup.NINTENDO),
    GAMECUBE(PlatformGroup.NINTENDO),
    WII(PlatformGroup.NINTENDO),
    WII_U(PlatformGroup.NINTENDO),
    SWITCH(PlatformGroup.NINTENDO),
    SWITCH_2(PlatformGroup.NINTENDO),
    GAME_BOY(PlatformGroup.NINTENDO),
    GAME_BOY_COLOR(PlatformGroup.NINTENDO),
    GAME_BOY_ADVANCE(PlatformGroup.NINTENDO),
    NINTENDO_DS(PlatformGroup.NINTENDO),
    NINTENDO_3DS(PlatformGroup.NINTENDO),
    VIRTUAL_BOY(PlatformGroup.NINTENDO),
    MASTER_SYSTEM(PlatformGroup.SEGA),
    MEGA_DRIVE(PlatformGroup.SEGA),
    MEGA_CD(PlatformGroup.SEGA),
    SEGA_32X(PlatformGroup.SEGA),
    SATURN(PlatformGroup.SEGA),
    DREAMCAST(PlatformGroup.SEGA),
    GAME_GEAR(PlatformGroup.SEGA),
    ATARI_2600(PlatformGroup.ATARI),
    ATARI_7800(PlatformGroup.ATARI),
    ATARI_LYNX(PlatformGroup.ATARI),
    ATARI_JAGUAR(PlatformGroup.ATARI),
    NEO_GEO(PlatformGroup.OTHER_CONSOLES),
    NEO_GEO_POCKET(PlatformGroup.OTHER_CONSOLES),
    PC_ENGINE(PlatformGroup.OTHER_CONSOLES),
    THREE_DO(PlatformGroup.OTHER_CONSOLES),
    ZX_SPECTRUM(PlatformGroup.HOME_COMPUTERS),
    COMMODORE_64(PlatformGroup.HOME_COMPUTERS),
    AMIGA(PlatformGroup.HOME_COMPUTERS),
    AMSTRAD_CPC(PlatformGroup.HOME_COMPUTERS),
    ATARI_8BIT(PlatformGroup.HOME_COMPUTERS),
    ATARI_ST(PlatformGroup.HOME_COMPUTERS),
    MSX(PlatformGroup.HOME_COMPUTERS),
    OTHER(PlatformGroup.OTHER);

    override val apiValue: String get() = name
}

object PlatformSerializer : LenientEnumSerializer<Platform>("Platform", Platform.entries, Platform.OTHER)

@Serializable(with = CollectionStatusSerializer::class)
enum class CollectionStatus : ApiEnum {
    OWNED, WISHLIST, PREORDERED, LENT, FOR_SALE, SOLD,

    /** A value introduced by a newer API version. */
    UNKNOWN;

    override val apiValue: String get() = name

    companion object {
        val selectable: List<CollectionStatus> = entries - UNKNOWN
    }
}

object CollectionStatusSerializer :
    LenientEnumSerializer<CollectionStatus>("CollectionStatus", CollectionStatus.entries, CollectionStatus.UNKNOWN)

@Serializable(with = GameFormatSerializer::class)
enum class GameFormat : ApiEnum {
    PHYSICAL, DIGITAL, UNKNOWN;

    override val apiValue: String get() = name

    companion object {
        val selectable: List<GameFormat> = entries - UNKNOWN
    }
}

object GameFormatSerializer : LenientEnumSerializer<GameFormat>("GameFormat", GameFormat.entries, GameFormat.UNKNOWN)

@Serializable(with = RegionSerializer::class)
enum class Region : ApiEnum {
    PAL, NTSC_U, NTSC_J, REGION_FREE, OTHER;

    override val apiValue: String get() = name
}

object RegionSerializer : LenientEnumSerializer<Region>("Region", Region.entries, Region.OTHER)

@Serializable(with = CompletenessSerializer::class)
enum class Completeness : ApiEnum {
    SEALED, CIB, GAME_AND_BOX, GAME_AND_MANUAL, LOOSE, BOX_ONLY, UNKNOWN;

    override val apiValue: String get() = name

    companion object {
        val selectable: List<Completeness> = entries - UNKNOWN
    }
}

object CompletenessSerializer :
    LenientEnumSerializer<Completeness>("Completeness", Completeness.entries, Completeness.UNKNOWN)

@Serializable(with = ConditionSerializer::class)
enum class Condition : ApiEnum {
    MINT, NEAR_MINT, VERY_GOOD, GOOD, FAIR, POOR, UNKNOWN;

    override val apiValue: String get() = name

    companion object {
        val selectable: List<Condition> = entries - UNKNOWN
    }
}

object ConditionSerializer : LenientEnumSerializer<Condition>("Condition", Condition.entries, Condition.UNKNOWN)

@Serializable(with = PlayStatusSerializer::class)
enum class PlayStatus : ApiEnum {
    UNPLAYED, PLAYING, COMPLETED, ABANDONED, UNKNOWN;

    override val apiValue: String get() = name

    companion object {
        val selectable: List<PlayStatus> = entries - UNKNOWN
    }
}

object PlayStatusSerializer : LenientEnumSerializer<PlayStatus>("PlayStatus", PlayStatus.entries, PlayStatus.UNKNOWN)

enum class GameSortField(override val apiValue: String) : ApiEnum {
    TITLE("title"),
    PLATFORM("platform"),
    RELEASE_YEAR("releaseYear"),
    PURCHASE_DATE("purchaseDate"),
    PURCHASE_PRICE("purchasePrice"),
    ESTIMATED_VALUE("estimatedValue"),
    RATING("rating"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
}

enum class SortOrder(override val apiValue: String) : ApiEnum {
    ASC("asc"),
    DESC("desc"),
}

@Serializable(with = ErrorCodeSerializer::class)
enum class ErrorCode : ApiEnum {
    VALIDATION_FAILED,
    UNAUTHORIZED,
    INVALID_CREDENTIALS,
    INVALID_REFRESH_TOKEN,
    INVALID_CURRENT_PASSWORD,
    EMAIL_ALREADY_REGISTERED,
    GAME_NOT_FOUND,
    SYNC_RESET_REQUIRED,
    NOT_FOUND,
    TOO_MANY_REQUESTS,
    BAD_REQUEST,
    FORBIDDEN,
    CONFLICT,
    INTERNAL_ERROR,
    UNKNOWN;

    override val apiValue: String get() = name
}

object ErrorCodeSerializer : LenientEnumSerializer<ErrorCode>("ErrorCode", ErrorCode.entries, ErrorCode.UNKNOWN)
