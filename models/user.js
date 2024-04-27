const bcrypt = require("bcrypt");
const db = require("../db");

const properties = ["email", "password", "role"];

function serialize(data) {
  const obj = {};

  properties.forEach((prop) => {
    if (data[prop] !== undefined) {
      obj[prop] = data[prop];
    }
  });

  return obj;
}

class User {
  constructor(data = {}) {
    this.id = data.id;
    this.createdAt = data.createdAt;
    this.updatedAt = data.updatedAt;
    this.deletedAt = data.deletedAt;
    this.email = data.email;
    this.password = data.password;
    this.role = data.role;
  }

  static async findAll({
    sortKey = "id",
    sortOrder = "desc",
    limit = 20,
    offset = 0,
    deleted = "false",
    email = "",
    role = "",
  } = {}) {
    let whereQuery = `WHERE "deletedAt" IS ${
      deleted === "true" ? "NOT" : ""
    } NULL`;

    const values = [];

    if (email) {
      whereQuery += ` AND unaccent("email") ILIKE unaccent($1)`;
      values.push(`%${email}%`);
    }

    if (role) {
      whereQuery += ` AND "role" = $${values.length + 1}`;
      values.push(role);
    }

    const usersQuery = `
        SELECT "id", "email", "role" FROM "users"
        ${whereQuery}
        ORDER BY "${sortKey}" ${sortOrder}
        LIMIT $${values.length + 1} OFFSET $${values.length + 2}`;

    const countQuery = `SELECT COUNT(*) FROM "users" ${whereQuery}`;

    const usersResult = await db.query(usersQuery, [...values, limit, offset]);

    const countResult = await db.query(countQuery, values);

    const users = usersResult.rows.map((user) => new User(user));
    const total = parseInt(countResult.rows[0].count, 10);

    return { data: users, total };
  }

  static async findById(id) {
    const { rows } = await db.query(
      `SELECT "id", "email", "role" FROM "users" WHERE "id" = $1`,
      [id]
    );

    return rows[0] ? new User(rows[0]) : null;
  }

  static async findByEmail(email) {
    const { rows } = await db.query(
      `SELECT "id", "email", "password", "role" FROM "users" WHERE "email" = $1`,
      [email]
    );

    return rows[0] ? new User(rows[0]) : null;
  }

  static async create({ email, password, passwordConfirm, role }) {
    try {
      const errors = {};

      if (!email) {
        errors.email = "Email is required.";
      } else if (!/^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$/.test(email)) {
        errors.email = "Invalid email format.";
      }
      if (!password) {
        errors.password = "Password is required.";
      }
      if (!passwordConfirm) {
        errors.passwordConfirm = "Password confirmation is required.";
      }

      if (Object.keys(errors).length > 0) {
        return {
          errorMessage: "Invalid input.",
          errors,
        };
      }

      if (password.length < 6) {
        errors.password = "Password must be at least 6 characters long.";
      }
      if (password !== passwordConfirm) {
        errors.passwordConfirm = "Passwords do not match.";
      }

      if (Object.keys(errors).length > 0) {
        return {
          errorMessage: "Invalid input.",
          errors,
        };
      }

      const hash = await bcrypt.hash(password, 12);

      const userData = {
        email,
        password: hash,
        role: role ?? "USER",
      };

      const fields = Object.keys(userData);
      const values = Object.values(userData);

      const quotedFields = fields.map((field) => `"${field}"`).join(", ");
      const placeholders = fields.map((_, index) => `$${index + 1}`).join(", ");

      const query = `
        INSERT INTO "users" (${quotedFields})
        VALUES (${placeholders})
        RETURNING "id", "email", "role"`;

      const { rows } = await db.query(query, values);

      return new User(rows[0]);
    } catch (error) {
      if (error.code === "23505") {
        return {
          errorMessage: "Invalid input.",
          errors: {
            email: "User with this email already exists.",
          },
        };
      }

      throw error;
    }
  }

  static async update(id, data) {
    try {
      const userData = serialize(data);

      delete userData.password;

      userData.updatedAt = new Date();

      const fields = Object.keys(userData);
      const values = Object.values(userData);

      const set = fields
        .map((field, index) => `"${field}" = $${index + 1}`)
        .join(", ");

      values.push(id);

      const query = `
        UPDATE "users"
        SET ${set}
        WHERE "id" = $${values.length}
        RETURNING "id", "email", "role"`;

      const { rows } = await db.query(query, values);

      return new User(rows[0]);
    } catch (error) {
      if (error.code === "23505") {
        return {
          errorMessage: "Invalid input.",
          errors: {
            email: "User with this email already exists.",
          },
        };
      }

      throw error;
    }
  }

  static async delete(id) {
    const { rows } = await db.query(
      'UPDATE "users" SET "deletedAt" = NOW() WHERE "id" = $1 RETURNING "id", "email", "role"',
      [id]
    );

    if (rows.length === 0) {
      return null;
    }

    return new User(rows[0]);
  }

  static async getStats() {
    const query = `
        SELECT "users"."id", "users"."email", "users"."role", COUNT("games"."id") AS "gamesCount"
        FROM "users"
        LEFT JOIN "games" ON "users"."id" = "games"."ownerId"
        WHERE "users"."deletedAt" IS NULL
        GROUP BY "users"."id"
        ORDER BY "users"."id"`;

    const { rows } = await db.query(query);

    return rows.map((item) => ({
      ...item,
      gamesCount: parseInt(item.gamesCount, 10),
    }));
  }
}

module.exports = User;
