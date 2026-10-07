package cz.gameshelf.app.domain.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** An enum whose JSON representation is [apiValue]. */
interface ApiEnum {
    val apiValue: String
}

/**
 * Serializes an [ApiEnum] by its wire value. Values unknown to this app version (e.g. a platform
 * added to a newer API) decode to [fallback] instead of failing the whole response.
 */
open class LenientEnumSerializer<T>(
    serialName: String,
    private val entries: List<T>,
    private val fallback: T,
) : KSerializer<T> where T : Enum<T>, T : ApiEnum {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.apiValue)

    override fun deserialize(decoder: Decoder): T {
        val raw = decoder.decodeString()
        return entries.firstOrNull { it.apiValue == raw } ?: fallback
    }
}

/** Looks up an enum entry by its wire value; `null` when the value is unknown. */
inline fun <reified T> apiEnumOrNull(raw: String): T? where T : Enum<T>, T : ApiEnum =
    enumValues<T>().firstOrNull { it.apiValue == raw }
