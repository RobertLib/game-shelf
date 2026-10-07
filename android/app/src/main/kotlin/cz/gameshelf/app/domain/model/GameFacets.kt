package cz.gameshelf.app.domain.model

import cz.gameshelf.app.domain.serialization.apiEnumOrNull

data class FacetValue(
    val value: String,
    val count: Int,
)

/** Distinct values in the user's collection with counts, in the shape of the API's `GameFacets`. */
data class GameFacets(
    val totalItems: Int,
    val platforms: List<FacetValue>,
    val statuses: List<FacetValue>,
    val genres: List<FacetValue>,
    val publishers: List<FacetValue>,
    val developers: List<FacetValue>,
    val storageLocations: List<FacetValue>,
    val releaseYearMin: Int?,
    val releaseYearMax: Int?,
) {
    /** Platform counts keyed by enum; platforms unknown to this app version are skipped. */
    val platformCounts: Map<Platform, Int> by lazy {
        platforms.mapNotNull { facet -> apiEnumOrNull<Platform>(facet.value)?.let { it to facet.count } }.toMap()
    }

    companion object {
        val Empty = GameFacets(0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, null)
    }
}
