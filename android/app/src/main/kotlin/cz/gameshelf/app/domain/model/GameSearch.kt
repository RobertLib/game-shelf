package cz.gameshelf.app.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Games found by title in the game database (OpenAPI `GameSearchResponse`). */
@Serializable
data class GameSearchResponse(
    /** At most 20 games, the most likely first; empty when nothing matches. */
    val items: List<GameSearchResult>,
    /** Databases the games come from, shown as attribution. */
    val sources: List<String> = emptyList(),
)

/** A game found by title (OpenAPI `GameSearchResult`), used to fill in the form. */
@Serializable
data class GameSearchResult(
    val igdbId: Int,
    val title: String,
    /** Platforms the game came out on, in the order of [Platform]; those this app version doesn't know are left out. */
    @Serializable(with = KnownPlatformsSerializer::class)
    val platforms: List<Platform>,
    val genre: String?,
    val developer: String?,
    val publisher: String?,
    val releaseYear: Int?,
    val coverImageUrl: String?,
)

/**
 * A list of platforms without the ones unknown to this app version: they decode to [Platform.OTHER],
 * which the API never lists as a platform a game came out on.
 */
internal object KnownPlatformsSerializer : KSerializer<List<Platform>> {
    private val delegate = ListSerializer(PlatformSerializer)

    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<Platform>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<Platform> =
        delegate.deserialize(decoder).filter { it != Platform.OTHER }
}
