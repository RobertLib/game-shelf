package cz.gameshelf.app.data.sync

/**
 * A local change of one game that the server has not confirmed yet (offline-sync.md, "Local data").
 * There is at most one per game; further local changes are folded in by [afterEdit] and [afterDelete].
 */
data class PendingChange(
    val gameId: String,
    val kind: Kind,
    /** `UPDATE`: the changed fields. `CREATE`: the fields edited after the first push attempt. */
    val fields: Set<String> = emptySet(),
    /** Increases with every local change of the game, so a push can tell whether it changed meanwhile. */
    val revision: Long = 1,
    /** Set when the change is first sent; from then on the server may have it even without a response. */
    val attempted: Boolean = false,
    /** Epoch millis when the change was queued; changes are pushed oldest first. */
    val queuedAt: Long,
) {
    enum class Kind { CREATE, UPDATE, DELETE }

    companion object {
        fun create(gameId: String, now: Long) = PendingChange(gameId, Kind.CREATE, queuedAt = now)
    }
}

/** The pending change after the user edited [fields] of the game; `this` is the change before, if any. */
fun PendingChange?.afterEdit(gameId: String, fields: Set<String>, now: Long): PendingChange = when {
    this == null -> PendingChange(gameId, PendingChange.Kind.UPDATE, fields, queuedAt = now)
    kind == PendingChange.Kind.UPDATE -> copy(fields = this.fields + fields, revision = revision + 1)
    // The server may already have the first version; remember what changed since then.
    kind == PendingChange.Kind.CREATE && attempted -> copy(fields = this.fields + fields, revision = revision + 1)
    // Not sent yet: the CREATE will carry the current content anyway.
    kind == PendingChange.Kind.CREATE -> copy(revision = revision + 1)
    // A deleted game is no longer stored locally, so it cannot be edited.
    else -> this
}

/** The pending change after the user deleted the game; `null` when there is nothing to push. */
fun PendingChange?.afterDelete(gameId: String, now: Long): PendingChange? = when {
    this == null -> PendingChange(gameId, PendingChange.Kind.DELETE, queuedAt = now)
    // The server never saw the game.
    kind == PendingChange.Kind.CREATE && !attempted -> null
    else -> copy(kind = PendingChange.Kind.DELETE, fields = emptySet(), revision = revision + 1)
}
