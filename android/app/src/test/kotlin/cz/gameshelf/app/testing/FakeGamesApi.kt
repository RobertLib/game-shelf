package cz.gameshelf.app.testing

import cz.gameshelf.app.data.api.ApiJson
import cz.gameshelf.app.data.api.GamesApi
import cz.gameshelf.app.data.api.dto.GameChanges
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.SaveGameRequest
import cz.gameshelf.app.domain.model.toNewGame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.time.Clock

/**
 * In-memory API with a per-user change feed, like the real one: every write gets the next version,
 * deletions leave tombstones. Calls are recorded in [calls]; [beforeCall] may fail or hold a call
 * before the server handles it, [afterCall] runs after it was applied (e.g. to lose the response).
 */
class FakeGamesApi(private val clock: Clock) : GamesApi {

    data class Call(
        val name: String,
        val id: String? = null,
        val fields: Set<String> = emptySet(),
        val cursor: String? = null,
    )

    val calls = mutableListOf<Call>()
    var beforeCall: suspend (Call) -> Unit = {}
    var afterCall: suspend (Call) -> Unit = {}

    private var version = 0L
    private val games = mutableMapOf<String, Pair<Game, Long>>()
    private val tombstones = mutableMapOf<String, Long>()

    fun serverGame(id: String): Game? = games[id]?.first

    /** A change made on another device. */
    fun putOnServer(game: Game) {
        tombstones.remove(game.id)
        games[game.id] = game to ++version
    }

    /** A deletion made on another device. */
    fun deleteOnServer(id: String) {
        games.remove(id)
        tombstones[id] = ++version
    }

    /** Gone without a trace, e.g. after the server was restored from a backup. */
    fun forgetOnServer(id: String) {
        games.remove(id)
        tombstones.remove(id)
    }

    /** Holds the next call named [name] until [Gate.release] completes. */
    fun hold(name: String): Gate {
        val gate = Gate()
        val previous = beforeCall
        beforeCall = { call ->
            previous(call)
            if (call.name == name && !gate.reached.isCompleted) {
                gate.reached.complete(Unit)
                gate.release.await()
            }
        }
        return gate
    }

    class Gate {
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    override suspend fun changes(cursor: String?, limit: Int): GameChanges = handle(Call("changes", cursor = cursor)) {
        val from = cursor?.toLong() ?: 0L
        val entries = (games.mapValues { it.value.second } + tombstones).entries
            .filter { it.value > from }
            .sortedBy { it.value }
        val page = entries.take(limit)
        GameChanges(
            games = page.mapNotNull { games[it.key]?.first },
            deletedIds = page.map { it.key }.filter { it in tombstones },
            cursor = (page.lastOrNull()?.value ?: from).toString(),
            hasMore = entries.size > limit,
        )
    }

    override suspend fun game(id: String): Game = handle(Call("GET", id)) {
        games[id]?.first ?: throw httpError(404, "GAME_NOT_FOUND")
    }

    override suspend fun createGame(body: JsonObject): Response<Game> {
        val id = body.getValue("id").jsonPrimitive.content
        return handle(Call("POST", id, fields = body.keys - "id")) {
            val existing = games[id]?.first
            if (id in tombstones) throw httpError(404, "GAME_NOT_FOUND")
            if (existing != null) {
                Response.success(200, existing)
            } else {
                val request = ApiJson.decodeFromJsonElement(SaveGameRequest.serializer(), JsonObject(body - "id"))
                val game = request.toNewGame(id, clock.instant())
                putOnServer(game)
                Response.success(201, game)
            }
        }
    }

    override suspend fun updateGame(id: String, body: JsonObject): Game = handle(Call("PATCH", id, body.keys)) {
        val existing = games[id]?.first ?: throw httpError(404, "GAME_NOT_FOUND")
        val json = ApiJson.encodeToJsonElement(Game.serializer(), existing).jsonObject
        val updatedAt = "updatedAt" to JsonPrimitive(clock.instant().toString())
        val updated = ApiJson.decodeFromJsonElement(Game.serializer(), JsonObject(json + body + updatedAt))
        putOnServer(updated)
        updated
    }

    override suspend fun deleteGame(id: String) = handle(Call("DELETE", id)) {
        when (id) {
            in games -> deleteOnServer(id)
            in tombstones -> Unit
            else -> throw httpError(404, "GAME_NOT_FOUND")
        }
    }

    private suspend fun <T> handle(call: Call, block: () -> T): T {
        calls += call
        beforeCall(call)
        val result = block()
        afterCall(call)
        return result
    }
}

/** An HTTP failure with an `ErrorResponse` body, as Retrofit reports it. */
fun httpError(status: Int, code: String): HttpException = HttpException(
    Response.error<Any>(
        status,
        """{"statusCode":$status,"code":"$code","message":"$code"}""".toResponseBody("application/json".toMediaType()),
    ),
)

/** An HTTP failure whose body is not an API error body, e.g. an HTML page from a proxy or a firewall. */
fun httpErrorWithBody(status: Int, body: String, mediaType: String = "text/html"): HttpException = HttpException(
    Response.error<Any>(status, body.toResponseBody(mediaType.toMediaType())),
)
