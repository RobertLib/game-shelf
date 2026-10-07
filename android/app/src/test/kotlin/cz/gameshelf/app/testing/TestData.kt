package cz.gameshelf.app.testing

import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.Platform
import java.time.Instant

/** A real `Game` body as returned by `GET games/{id}`, with every optional value set. */
const val FULL_GAME_JSON = """
{
  "id": "01a1163d-d903-7036-b56d-72a9d3b2a6c4",
  "title": "Banjo-Kazooie",
  "platform": "N64",
  "status": "OWNED",
  "format": "PHYSICAL",
  "region": "PAL",
  "edition": "Player's Choice",
  "completeness": "CIB",
  "condition": "NEAR_MINT",
  "playStatus": "COMPLETED",
  "genre": "Platformer",
  "developer": "Rare",
  "publisher": "Nintendo",
  "releaseYear": 1998,
  "barcode": "045496870058",
  "productCode": "NUS-NBKP-EUR",
  "quantity": 2,
  "purchasePrice": 1299.90,
  "purchaseDate": "2024-05-17",
  "purchasePlace": "Retro Game Store",
  "estimatedValue": 1500,
  "currency": "CZK",
  "storageLocation": "Shelf A",
  "rating": 9,
  "favorite": true,
  "coverImageUrl": "https://example.com/banjo.jpg",
  "notes": "No scratches",
  "createdAt": "2026-10-07T12:01:54.435Z",
  "updatedAt": "2026-10-07T12:01:54.435Z"
}
"""

fun testGame(
    id: String,
    title: String = "Game $id",
    platform: Platform = Platform.PS2,
    favorite: Boolean = false,
) = Game(
    id = id,
    title = title,
    platform = platform,
    status = CollectionStatus.OWNED,
    format = GameFormat.PHYSICAL,
    region = null,
    edition = null,
    completeness = null,
    condition = null,
    playStatus = null,
    genre = null,
    developer = null,
    publisher = null,
    releaseYear = null,
    barcode = null,
    productCode = null,
    quantity = 1,
    purchasePrice = null,
    purchaseDate = null,
    purchasePlace = null,
    estimatedValue = null,
    currency = "CZK",
    storageLocation = null,
    rating = null,
    favorite = favorite,
    coverImageUrl = null,
    notes = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
