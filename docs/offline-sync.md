# Offline-first sync

Both mobile apps are **offline-first**: every screen reads the collection from a local database on
the device, and every change (create, edit, favorite, delete) is written locally first and takes
effect immediately, with or without a connection. A sync engine pushes local changes to the API and
pulls changes made on other devices whenever the server can be reached.

This document is the contract between the API and the apps. The screens and texts are in
[mobile-spec.md](mobile-spec.md).

## Principles

- **The device keeps a full copy of the user's collection.** A personal collection is small (hundreds,
  at most a few thousand games), so search, filters, sorting and facets run locally in memory with the
  same semantics as `GET /games` and `GET /games/facets`. The apps no longer call those two endpoints.
- **Ids are generated on the device** (random UUIDs), so a game created offline keeps its id forever.
- **Changes are pushed per field.** An edit sends only the fields the user changed (`PATCH`), so
  edits of different fields on two devices both survive. When two devices change the *same* field,
  the change that reaches the server last wins.
- **Delete wins.** An edit of a game that was deleted on another device is dropped.
- **Every request is idempotent**, so a request whose response was lost can simply be sent again.

## API

### Change feed – `GET /games/changes?cursor=…&limit=…`

Every create, update and delete of a game gets the next number from a per-user counter
(`games.version`, `users.gamesVersion`). Deleted games are kept as tombstones (`games.deletedAt`)
so that other devices learn about the deletion; a tombstone keeps only what the feed needs, its
content (notes, prices, places …) is cleared.

| param | |
|---|---|
| `cursor` | Opaque string from the previous response. Omit it to start from the beginning. |
| `limit` | 1–1000, default 500. |

Response `GameChanges`:

```json
{
  "games": [ /* Game – created or changed since the cursor */ ],
  "deletedIds": [ "uuid", "…" ],
  "cursor": "1234",
  "hasMore": false
}
```

- Call again with the returned `cursor` while `hasMore` is `true`. Store the cursor only together
  with the data of the page it belongs to (one local transaction).
- An id appears at most once per page, either in `games` or in `deletedIds`.
- Your own pushed changes come back in the feed too; applying them is harmless.
- `410 SYNC_RESET_REQUIRED` – the cursor cannot be continued (e.g. the server was restored from a
  backup). Throw away all local games that have no pending change, forget the cursor and pull from
  the beginning. The cursor carries a per-user *sync epoch*; after a restore the operator gives every
  user a new one (`npm run db:reset-sync`, see [api/README.md](../api/README.md)), so every cursor
  handed out before the restore is answered with `410`, even once new changes have pushed the
  counter past it.
- `400 VALIDATION_FAILED` – malformed cursor; treat like `410`.

### Writes

| request | success | notes |
|---|---|---|
| `POST /games` with `CreateGameRequest` (= `SaveGameRequest` + optional `id`) | `201` + `Game` | Create with a client-generated id. |
| … same `id` again | `200` + `Game` | The game already exists: **nothing is changed**, the stored game is returned. |
| `PATCH /games/{id}` with `UpdateGameRequest` | `200` + `Game` | Only the fields present in the body change. `null` clears an optional field; required fields (`title`, `platform`, `status`, `format`, `quantity`, `currency`, `favorite`) cannot be `null`. |
| `DELETE /games/{id}` | `204` | Also `204` when the game is already deleted. |
| `PUT /games/{id}` | `200` + `Game` | Full replacement, kept for older app versions. |

Errors that matter to the sync engine:

| status / code | meaning for the pending change |
|---|---|
| `404 GAME_NOT_FOUND` | The game was deleted (on another device). Remove it locally and drop the change. For a `DELETE` this is success. |
| `400 VALIDATION_FAILED`, `409 CONFLICT`, other `4xx` except `401`/`408`/`429` **with an API error body** | Permanently rejected; see *Rejected changes*. |
| `401` | Handled by the token refresh; if the session ends, sync stops. |
| `408`, `429`, `5xx`, network failure, timeout | Temporary; keep the change and retry later. |
| any other response without an API error body | Temporary. |

An *API error body* is the JSON `ErrorResponse` with a string `code` (any code, also one this app
version doesn't know). A `4xx` without it – an HTML page from a proxy, a `403` from a firewall, a
`404` from a misrouted request – did not come from the Game Shelf API, so it says nothing about the
change and must never undo it or delete a game. Likewise a `404` counts as "deleted" only with the
code `GAME_NOT_FOUND`.

## Local data

| | |
|---|---|
| **games** | Every game of the signed-in user, keyed by id, in the `Game` shape. Deleted games are removed. |
| **pending changes** | At most one per game: `CREATE`, `UPDATE` (with the set of changed field names) or `DELETE`, plus a `revision` that increases with every local change of that game and an `attempted` flag set when it was first sent. |
| **sync state** | Owner user id, `cursor`, time of the last completed sync, version of the app that stored the cursor. |

The data belongs to one user. On sign-in, if the stored owner differs from the signed-in user, all
local data is wiped first. Signing out or deleting the account wipes it. When the session merely
*expires* (refresh token rejected), the data is kept, so unsynced changes survive and are pushed
after the same user signs in again.

**After an app update** (the stored app version differs from the running one) the cursor is
forgotten – games and pending changes are kept – so the next run pulls the whole collection again.
An older version stored values it did not know (e.g. a new platform) as its "unknown"/`OTHER`
fallback; the full pull replaces them with the real values. The pull merge table below keeps every
pending change intact.

A refresh of the tokens is applied only while the session it started from is still the current
one: when the user signs out (or another user signs in) during a refresh, its result – new tokens or
a rejection – is ignored.

### Local changes

Local writes never fail because of the network. Each one updates the game and its pending change in
one transaction and then asks the sync engine to run.

| user action | pending change before | pending change after |
|---|---|---|
| create | – | `CREATE` |
| edit (fields F) | – | `UPDATE F` |
| | `UPDATE G` | `UPDATE F ∪ G` |
| | `CREATE`, not attempted | `CREATE` |
| | `CREATE`, attempted | `CREATE` and remember F (see below) |
| delete | – or `UPDATE` | `DELETE` (the game disappears locally at once) |
| | `CREATE`, not attempted | nothing – the server never saw the game |
| | `CREATE`, attempted | `DELETE` |

"Changed fields" are the fields whose value in the saved `SaveGameRequest` differs from the request
the edit started from (the edit form's initial state; for a favorite toggle, the stored game). Only
those fields are written onto the stored game, so values a sync brought in while the form was open
are kept. Both requests are built the same way, so fields the user did not touch (including enum
values unknown to this app version) are never sent.
An edit that changes nothing creates no pending change. Every change sets `updatedAt` to now locally;
the server's timestamps replace it after sync.

## Sync run

A run pushes all pending changes (oldest first) and then pulls the change feed until `hasMore` is
false. Only one run is active at a time; a request for a run while one is active schedules exactly
one more run after it.

**Push**, for each pending change (remember its `revision`, set `attempted`):

- `CREATE` → `POST /games` with the id and all fields of the local game.
  - `201`: the server has the current local content.
  - `200` (it existed – an earlier attempt got through, but its response was lost): fields edited
    after the first attempt are not on the server yet; if there are any, the change becomes
    `UPDATE` with those fields and is pushed in the same run. That includes fields edited while
    this request was in flight – never all fields, which would overwrite changes other devices
    made since the first attempt.
- `UPDATE F` → `PATCH /games/{id}` with the fields F taken from the local game.
- `DELETE` → `DELETE /games/{id}`; `404` counts as success.

After a successful response, in one transaction: if the `revision` is unchanged, drop the pending
change and store the returned game. If the game was changed locally meanwhile, keep the pending
change (a `CREATE` becomes `UPDATE` of all fields) and store the returned game merged with the local
values of the pending fields.

A temporary failure ends the run; the remaining changes stay queued.

**Pull** – apply each page in one transaction together with its cursor:

| server says | no pending change | pending `UPDATE F` | pending `CREATE` | pending `DELETE` |
|---|---|---|---|---|
| game G | store G | store G with the local values of F | keep local | ignore |
| deleted | remove | remove game and change | remove game and change | drop the change |

The time of the last completed sync is stored when the pull reaches `hasMore = false`.

### Rejected changes

A change rejected permanently can never succeed, so it is undone: drop the pending change; for a
`CREATE` remove the local game, for an `UPDATE` or `DELETE` restore the server's version with
`GET /games/{id}` (or remove the game on `404`). A local delete made after the rejected change
stays queued (also after a rejected `CREATE`: the game is already gone locally, the `DELETE` is pushed
and a `404` counts as success). The apps show "Some changes were rejected by the server and have been
undone." – also when the screen that shows it opens only after the run. This should not happen in
practice because the apps validate with exactly the rules of the API
([mobile-spec.md](mobile-spec.md#validation)).

A game that is missing from the local store while its pending `CREATE` or `UPDATE` is pushed is a
local error (temporary), never a sign that the game was deleted on the server.

### When it runs

- after sign-in and when the app starts signed in,
- when the app comes to the foreground,
- when the device gets a network connection,
- right after every local change,
- on pull-to-refresh and "Sync now" (these report a failure to the user),
- after a temporary failure, with exponential backoff (2 s, 4 s, 8 s … at most 5 minutes) while the
  app is in the foreground. Any other trigger retries immediately.

Sign-out and account deletion cancel a running sync before wiping the data, and a run never writes
data of a user who is no longer signed in.

## Status shown in the apps

- **Offline** – the last attempt could not reach the server, or the device has no connection.
- **Syncing…** – a run is in progress.
- **N unsynced changes** – the number of pending changes.
- **Last synced** – time of the last completed sync.

Until the first complete pull, an empty collection is not shown as empty: the list shows the loading
state, or an error with "Try again" when the first sync failed.

## Not covered yet

- Sync while the app is closed (Android WorkManager, iOS background tasks).
- Purging old tombstones on the server – the change feed would answer `410` to cursors older than the
  purge horizon, which the apps already handle.
- Cover images are not stored for offline use; they come from the platform image caches.
