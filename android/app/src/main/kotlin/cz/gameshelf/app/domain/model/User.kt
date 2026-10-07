@file:UseSerializers(InstantSerializer::class)

package cz.gameshelf.app.domain.model

import cz.gameshelf.app.domain.serialization.InstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

/** The signed-in user (OpenAPI `User`). */
@Serializable
data class User(
    val id: String,
    val email: String,
    val displayName: String?,
    val createdAt: Instant,
)
