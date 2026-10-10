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
so that other devices learn about the deletion; a tombstone keeps only what the feed needs (id,
owner, version, timestamps): its content is cleared – optional fields are emptied, required ones are
reset to fixed values.

| param | |
|---|---|
| `cursor` | Opaque string from the previous response. Omit it to start from the beginning. |
| `limit` | 1–1000, default 500. |

Response `GameChanges`:

```json
{
  "games": [ /* Game – created or changed since the cursor */ ],
  "deletedIds": [ "uuid", "…" ],
  "cursor": "5f1d3c9a2b.42",
  "hasMore": false
}
```

- Call again with the returned `cursor` while `hasMore` is `true`. Store the cursor only together
  with the data of the page it belongs to (one local transaction).
- An id appears at most once per page, either in `games` or in `deletedIds`.
- Your own pushed changes come back in the feed too; applying them is harmless.
- `410 SYNC_RESET_REQUIRED` – the cursor cannot be continued (e.g. the server was restored from a
  backup). See *Reset* below. The cursor carries a per-user *sync epoch*; after a restore the
  operator gives every user a new one (`npm run db:reset-sync`, see [api/README.md](../api/README.md)),
  so every cursor handed out before the restore is answered with `410`, even once new changes have
  pushed the counter past it. Cursors without an epoch (a plain number, from app versions before the
  epoch existed) are answered with `410` too.
- `400 VALIDATION_FAILED` – malformed cursor; treat like `410`.

### Writes

| request | success | notes |
|---|---|---|
| `POST /games` with `CreateGameRequest` (= `SaveGameRequest` + optional `id`) | `201` + `Game` | Create with a client-generated id. |
| … same `id` again | `200` + `Game` | The game already exists: **nothing is changed**, the stored game is returned. |
| `PATCH /games/{id}` with `UpdateGameRequest` | `200` + `Game` | Only the fields present in the body change. `null` clears an optional field; required fields (`title`, `platform`, `status`, `format`, `quantity`, `currency`, `favorite`) cannot be `null`. |
| `DELETE /games/{id}` | `204` | Also `204` when the game is already deleted, and when the server has never seen the id: it then stores a tombstone, so that a create with that id arriving later (a delayed retry) is answered `404` and cannot bring the game back. |
| `PUT /games/{id}` | `200` + `Game` | Full replacement, kept for older app versions. |

Errors that matter to the sync engine:

| status / code | meaning for the pending change |
|---|---|
| `404 GAME_NOT_FOUND` | The game was deleted (on another device). Remove it locally and drop the change. For a `DELETE` this is success. |
| `404 GAME_UNKNOWN` (`PATCH`, `PUT`, `GET`) | The server has no record of the game at all – it was restored from a backup older than the game. The change becomes a `CREATE` (not attempted) and is pushed again in the same run (for the `GET` of an undo see *Rejected changes*). |
| `400 VALIDATION_FAILED`, `409 CONFLICT` **with an API error body** | Permanently rejected; see *Rejected changes*. |
| `401` | Handled by the token refresh; if the session ends, sync stops. |
| `401 ACCOUNT_DELETED` | The account was deleted (on another device). Never refreshed: the app signs out and wipes the local data like a sign-out. |
| any other `4xx` or `5xx` **with an API error body**, except `408` / `429` | *This change failed*: it stays queued, the push goes on with the next change, and the run counts as failed (it is retried later). |
| `2xx` whose game this app version can't decode | *This change failed* too – one game the app can't read must not stop the push or the pull. |
| `408`, `429`, network failure, timeout, any response **without** an API error body | *The server can't be used now*: the run ends, every change stays queued. |

An *API error body* is the JSON `ErrorResponse` with a string `code` (any code, also one this app
version doesn't know). A response without it – an HTML page from a proxy, a `403` from a firewall, a
`404` from a misrouted request – did not come from the Game Shelf API, so it says nothing about the
change and must never undo it or delete a game. Likewise a `404` counts as "deleted" only with the
code `GAME_NOT_FOUND`.

One change that keeps failing (a server bug with one game, a game the app can no longer read) must
never hold up the others or stop the device from receiving changes from other devices; that is why
only failures that concern the whole server end the run.

## Local data

| | |
|---|---|
| **games** | Every game of the signed-in user, keyed by id, in the `Game` shape. Deleted games are removed. |
| **pending changes** | At most one per game: `CREATE`, `UPDATE` (with the set of changed field names) or `DELETE`, plus a `revision` that increases with every local change of that game and an `attempted` flag set when it was first sent. |
| **sync state** | Owner user id, `cursor`, time of the last completed sync, version of the app that stored the cursor, whether undone rejected changes still have to be reported to the user. |
| **unconfirmed games** | Ids of local games a reset has not yet found on the server (see *Reset*). |

The data belongs to one user. On sign-in, if the stored owner differs from the signed-in user, all
local data is wiped first. Signing out or deleting the account wipes it, and so does
`401 ACCOUNT_DELETED` on the user's other devices: the API answers it to every access token of a deleted
account, also an expired one, so a device learns it at its next request. When the session merely
*expires* (refresh token rejected), the data is kept, so unsynced changes survive and are pushed
after the same user signs in again.

A wipe survives the app being killed half-way: before the stored session is cleared and the data
wiped, the app stores a *wipe pending* marker, and removes it only when both are done. An app that
starts with the marker clears the stored session and wipes the data before anything is shown.

Enum values this app version doesn't know (e.g. a platform added to a newer API) are shown as their
fallback ("Unknown", or `OTHER` for platform and region), but the stored game keeps the value the API
sent, verbatim. It is never sent in a `PATCH` unless the user changed that field (see *Local
changes*), and it is sent unchanged when the whole game is sent (a `CREATE`), so pushing a game back
to a server that lost it doesn't replace such values with defaults.

**After an app update** (the stored app version differs from the running one) the cursor is
forgotten – games and pending changes are kept – so the next run pulls the whole collection again.
An older version may have stored games it could not read; the full pull brings them again. The pull
merge table below keeps every pending change intact. Like every pull that starts from the beginning
while games are stored, it marks them *unconfirmed* first (see *Reset*), so games the server lost in
the meantime (a restore from a backup) are pushed back even though no `410` told the app.

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

- `CREATE` → `POST /games` with the id and all fields of the local game (unknown enum values
  verbatim, see *Local data*).
  - `201`: the server has the current local content.
  - `200` (it existed – an earlier attempt got through, but its response was lost): fields edited
    after the first attempt are not on the server yet; if there are any, the change becomes
    `UPDATE` with those fields and is pushed in the same run. That includes fields edited while
    this request was in flight – never all fields, which would overwrite changes other devices
    made since the first attempt.
- `UPDATE F` → `PATCH /games/{id}` with the fields F taken from the local game.
- `DELETE` → `DELETE /games/{id}`; `404` counts as success.
- `UPDATE F` that gets `404 GAME_UNKNOWN` → in one transaction the pending change becomes a `CREATE`
  that is not attempted (a later delete then needs no request), and it is pushed again at once.

After a successful response, in one transaction: if the `revision` is unchanged, drop the pending
change and store the returned game. If the game was changed locally meanwhile, keep the pending
change and store the returned game merged with the local values of the pending fields. A `CREATE`
becomes `UPDATE` of the fields edited since it was first attempted (the ones it remembers, see
*Local changes*) – never of all fields, which would overwrite changes other devices made meanwhile;
an `UPDATE` keeps its fields.

When *this change failed* (see the table above), the push continues with the next change and the
pull still runs; the run counts as failed, so it is retried with backoff. The run reports the error
that ended it if there was one (so a pull that can't reach the server still shows "Offline"),
otherwise the error of the first change that failed. A game that is missing from
the local store (or can no longer be read) while its pending `CREATE` or `UPDATE` is pushed is such a
failure too – a local problem, never a sign that the game is gone on the server. When *the server
can't be used now*, the run ends at once: the remaining changes stay queued and there is no pull.

**Pull** – apply each page in one transaction together with its cursor:

| server says | no pending change | pending `UPDATE F` | pending `CREATE` | pending `DELETE` |
|---|---|---|---|---|
| game G | store G | store G with the local values of F | keep local | ignore |
| deleted | remove | remove game and change | remove game and change | drop the change |

When the pending change is an `UPDATE` but the stored game can't be read by this app version, the
server's game is ignored like for a pending `CREATE`: storing it would replace the unpushed values.

A game in a page that this app version can't decode is skipped – the rest of the page and its cursor
are applied – so that one game never stops the device from receiving changes. Its local copy, if any,
stays as it is; the full pull after the next app update brings it again. Its id still counts as
found on the server (it is no longer *unconfirmed*, see *Reset*), so a reset never pushes it back as
a new game.

The time of the last completed sync is stored when the pull reaches `hasMore = false`.

**Reset** – on `410` (or `400` for a malformed cursor), in one transaction: forget the cursor and mark
every local game that has no pending change as *unconfirmed*; nothing is removed, so the list stays as
it is. Then pull from the beginning. Any other pull that starts without a cursor while games are stored
(after an app update, or when the app dropped its cursor itself) marks them the same way first, because
it has no `410` to tell it that the server was restored. Every game or deleted id the pull brings is
applied as above and is no longer unconfirmed. In the transaction of the page with `hasMore = false`,
the games still unconfirmed are not on the server (e.g. created after the backup it was restored from):
each gets a `CREATE` that is not attempted, and another run is requested, which pushes them. A local
edit or delete of an unconfirmed game just creates its pending change as usual (an `UPDATE` then gets
`404 GAME_UNKNOWN`, a `DELETE` gets `204`). The marks are stored, so a reset interrupted by a failure
or a restart continues in the next run; signing out clears them. At most one reset per run.

### Rejected changes

A change rejected permanently (`400 VALIDATION_FAILED`, `409 CONFLICT`) can never succeed as it is.

- An `UPDATE` or `DELETE` is undone: drop the pending change and restore the server's version with
  `GET /games/{id}` (or remove the game on `404 GAME_NOT_FOUND`; any other failure of that request
  is a failure of this change, and the undo is tried again in the next run). The apps show "Some
  changes were rejected by the server and have been undone." – also when the screen that shows it
  opens only after the run, or after a restart (the flag is stored with the sync state until shown).
  - `404 GAME_UNKNOWN` from that `GET`: the server has no record of the game (restored from an older
    backup), so there is nothing to restore and the local game is the only copy. An `UPDATE` becomes a
    `CREATE` that is not attempted – the game is kept and pushed as a new one; a `DELETE` is dropped.
  - Only a change that is unchanged since the rejected request was sent (same `revision`) is undone.
    One the user edited meanwhile stays queued as *this change failed* and is sent again in the next
    run: the newer edit may have fixed the rejected value, and undoing it would lose that edit. If the
    same change is rejected again, it is undone then.
- A `CREATE` is **not** undone: the game exists only on this device, and removing it would lose
  everything the user typed. The game and its pending `CREATE` stay; it counts as *this change
  failed* and is sent again in later runs, so it goes through once the game is edited into a valid
  state or the app or the server is updated (e.g. a field a newer app sends that the server doesn't
  know yet).

A local delete made after the rejected change stays queued (the game is already gone locally, the
`DELETE` is pushed and a `404` counts as success). Rejections should not happen in practice because
the apps validate with exactly the rules of the API ([mobile-spec.md](mobile-spec.md#validation)).

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
- Purging old tombstones on the server. Answering `410` to cursors older than the purge horizon is
  not enough: a reset pushes back every local game the server doesn't know (see *Reset*), so a device
  that missed a purged tombstone would bring the deleted game back.
- Cover images are not stored for offline use; they come from the platform image caches.
