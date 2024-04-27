const db = require("../db");

const properties = [
  "name",
  "genre",
  "releaseDate",
  "developer",
  "publisher",
  "platform",
  "rating",
  "note",
  "ownerId",
];

function serialize(data) {
  const obj = {};

  properties.forEach((prop) => {
    if (data[prop] !== undefined) {
      obj[prop] = data[prop];
    }
  });

  return obj;
}

function validate(data) {
  if (!data.name || !data.genre) {
    return {
      errorMessage: "Name and genre are required.",
      errors: {},
    };
  }
}

class Game {
  constructor(data = {}) {
    this.id = data.id;
    this.createdAt = data.createdAt;
    this.updatedAt = data.updatedAt;
    this.deletedAt = data.deletedAt;
    this.name = data.name;
    this.genre = data.genre;
    this.releaseDate = data.releaseDate;
    this.developer = data.developer;
    this.publisher = data.publisher;
    this.platform = data.platform;
    this.rating = data.rating;
    this.note = data.note;
    this.ownerId = data.ownerId;
  }

  static async findAll({
    sortKey = "id",
    sortOrder = "desc",
    limit = 20,
    offset = 0,
    deleted = "false",
    name = "",
  } = {}) {
    let whereQuery = `WHERE "deletedAt" IS ${
      deleted === "true" ? "NOT" : ""
    } NULL`;

    const values = [];

    if (name) {
      whereQuery += ` AND unaccent("name") ILIKE unaccent($1)`;
      values.push(`%${name}%`);
    }

    const gamesQuery = `
        SELECT * FROM "games"
        ${whereQuery}
        ORDER BY "${sortKey}" ${sortOrder}
        LIMIT $${values.length + 1} OFFSET $${values.length + 2}`;

    const countQuery = `SELECT COUNT(*) FROM "games" ${whereQuery}`;

    const gamesResult = await db.query(gamesQuery, [...values, limit, offset]);

    const countResult = await db.query(countQuery, values);

    const games = gamesResult.rows.map((game) => new Game(game));
    const total = parseInt(countResult.rows[0].count, 10);

    return { data: games, total };
  }

  static async findById(id) {
    const { rows } = await db.query('SELECT * FROM "games" WHERE "id" = $1', [
      id,
    ]);

    if (rows.length === 0) {
      return null;
    }

    return new Game(rows[0]);
  }

  static async create(data) {
    const gameData = serialize(data);

    const errors = validate(gameData);

    if (errors) {
      return errors;
    }

    const fields = Object.keys(gameData);
    const values = Object.values(gameData);

    const quotedFields = fields.map((field) => `"${field}"`).join(", ");
    const placeholders = fields.map((_, index) => `$${index + 1}`).join(", ");

    const query = `
        INSERT INTO "games" (${quotedFields})
        VALUES (${placeholders})
        RETURNING *`;

    const { rows } = await db.query(query, values);

    return new Game(rows[0]);
  }

  static async update(id, data) {
    const gameData = serialize(data);

    const errors = validate(gameData);

    if (errors) {
      return errors;
    }

    gameData.updatedAt = new Date();

    const fields = Object.keys(gameData);
    const values = Object.values(gameData);

    const set = fields
      .map((field, index) => `"${field}" = $${index + 1}`)
      .join(", ");

    values.push(id);

    const query = `
        UPDATE "games"
        SET ${set}
        WHERE "id" = $${values.length}
        RETURNING *`;

    const { rows } = await db.query(query, values);

    if (rows.length === 0) {
      return null;
    }

    return new Game(rows[0]);
  }

  static async delete(id) {
    const { rows } = await db.query(
      'UPDATE "games" SET "deletedAt" = NOW() WHERE "id" = $1 RETURNING *',
      [id]
    );

    if (rows.length === 0) {
      return null;
    }

    return new Game(rows[0]);
  }
}

module.exports = Game;
