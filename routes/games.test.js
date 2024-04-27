const { createApp, db, mockDbQuery, request } = require("../setup-tests");
const gamesRouter = require("./games");

const app = createApp("/games", gamesRouter);

const mockGameData = [
  {
    id: 1,
    name: "Game 1",
    genre: "RPG",
    releaseDate: "2020-01-01",
    developer: "Dev 1",
    publisher: "Pub 1",
    platform: "PC",
    rating: 5,
    ownerId: 1,
  },
  {
    id: 2,
    name: "Game 2",
    genre: "FPS",
    releaseDate: "2021-01-01",
    developer: "Dev 2",
    publisher: "Pub 2",
    platform: "Console",
    rating: 4,
    ownerId: 2,
  },
];

describe("Games API", () => {
  describe("GET /games", () => {
    it("should return all games with default pagination and sorting", async () => {
      mockDbQuery(mockGameData, 2);

      const res = await request(app).get("/games");

      expect(res.statusCode).toBe(200);
      expect(res.body.data).toEqual(mockGameData);
      expect(res.body.total).toBe(2);
    });

    it("should return filtered games by name and genre", async () => {
      mockDbQuery([mockGameData[0]], 1);

      const res = await request(app)
        .get("/games")
        .query({ name: "Game 1", genre: "RPG" });

      expect(res.statusCode).toBe(200);
      expect(res.body.data).toEqual([mockGameData[0]]);
      expect(res.body.total).toBe(1);
    });

    it("should return 400 for invalid sort column", async () => {
      const res = await request(app)
        .get("/games")
        .query({ sortKey: "invalidColumn" });

      expect(res.statusCode).toBe(400);
      expect(res.text).toBe("Invalid sort column.");
    });

    it("should return 400 for invalid limit", async () => {
      const res = await request(app).get("/games").query({ limit: -1 });

      expect(res.statusCode).toBe(400);
      expect(res.text).toBe("Invalid query parameters.");
    });

    it("should return 500 for server error", async () => {
      db.query.mockRejectedValueOnce(new Error("Database error"));

      const res = await request(app).get("/games");

      expect(res.statusCode).toBe(500);
      expect(res.text).toMatch("Database error");
    });
  });

  describe("GET /games/:id", () => {
    it("should return a game by id", async () => {
      mockDbQuery([mockGameData[0]]);

      const res = await request(app).get("/games/1");

      expect(res.statusCode).toBe(200);
      expect(res.body).toEqual(mockGameData[0]);
    });

    it("should return 404 if game not found", async () => {
      mockDbQuery([]);

      const res = await request(app).get("/games/1");

      expect(res.statusCode).toBe(404);
      expect(res.text).toMatch("Game not found.");
    });

    it("should return 500 for server error", async () => {
      db.query.mockRejectedValueOnce(new Error("Database error"));

      const res = await request(app).get("/games/1");

      expect(res.statusCode).toBe(500);
      expect(res.text).toMatch("Database error");
    });
  });

  describe("POST /games", () => {
    const newGame = {
      name: "Game 1",
      genre: "RPG",
      releaseDate: "2020-01-01",
      developer: "Dev 1",
      publisher: "Pub 1",
      platform: "PC",
      rating: 5,
    };

    it("should create a new game", async () => {
      mockDbQuery([mockGameData[0]]);

      const res = await request(app).post("/games").send(newGame);

      expect(res.statusCode).toBe(201);
      expect(res.body).toEqual(mockGameData[0]);
    });

    it("should return 400 if name or genre is missing", async () => {
      const res = await request(app).post("/games").send({
        name: "Game 1",
        releaseDate: "2020-01-01",
        developer: "Dev 1",
        publisher: "Pub 1",
        platform: "PC",
        rating: 5,
      });

      expect(res.statusCode).toBe(400);
      expect(res.text).toMatch("Name and genre are required.");
    });

    it("should return 500 for server error", async () => {
      db.query.mockRejectedValueOnce(new Error("Database error"));

      const res = await request(app).post("/games").send(newGame);

      expect(res.statusCode).toBe(500);
      expect(res.text).toMatch("Database error");
    });
  });

  describe("PUT /games/:id", () => {
    const updatedGame = {
      name: "Updated Game",
      genre: "RPG",
      releaseDate: "2020-01-01",
      developer: "Dev 1",
      publisher: "Pub 1",
      platform: "PC",
      rating: 5,
    };

    it("should update a game by id", async () => {
      mockDbQuery([{ ownerId: 1 }]);
      mockDbQuery([updatedGame]);

      const res = await request(app).put("/games/1").send(updatedGame);

      expect(res.statusCode).toBe(200);
      expect(res.body).toEqual(updatedGame);
    });

    it("should return 404 if game not found", async () => {
      mockDbQuery([]);

      const res = await request(app).put("/games/1").send(updatedGame);

      expect(res.statusCode).toBe(404);
      expect(res.text).toBe("Record not found in games.");
    });

    it("should return 400 if name or genre is missing", async () => {
      mockDbQuery([{ ownerId: 1 }]);

      const res = await request(app).put("/games/1").send({
        name: "Updated Game",
        releaseDate: "2020-01-01",
        developer: "Dev 1",
        publisher: "Pub 1",
        platform: "PC",
        rating: 5,
      });

      expect(res.statusCode).toBe(400);
      expect(res.text).toMatch("Name and genre are required.");
    });

    it("should return 403 if user is not the owner of the game", async () => {
      mockDbQuery([{ ownerId: 2 }]);

      const res = await request(app).put("/games/1").send(updatedGame);

      expect(res.statusCode).toBe(403);
      expect(res.text).toBe("You are not the owner of this record in games.");
    });

    it("should return 500 for server error", async () => {
      db.query.mockRejectedValueOnce(new Error("Database error"));

      const res = await request(app).put("/games/1").send(updatedGame);

      expect(res.statusCode).toBe(500);
      expect(res.text).toMatch("Database error");
    });
  });

  describe("DELETE /games/:id", () => {
    it("should delete a game by id", async () => {
      mockDbQuery([{ ownerId: 1 }]);
      mockDbQuery([mockGameData[0]]);

      const res = await request(app).delete("/games/1");

      expect(res.statusCode).toBe(204);
    });

    it("should return 404 if game not found", async () => {
      mockDbQuery([]);

      const res = await request(app).delete("/games/1");

      expect(res.statusCode).toBe(404);
      expect(res.text).toBe("Record not found in games.");
    });

    it("should return 403 if user is not the owner of the game", async () => {
      mockDbQuery([{ ownerId: 2 }]);

      const res = await request(app).delete("/games/1");

      expect(res.statusCode).toBe(403);
      expect(res.text).toBe("You are not the owner of this record in games.");
    });

    it("should return 500 for server error", async () => {
      db.query.mockRejectedValueOnce(new Error("Database error"));

      const res = await request(app).delete("/games/1");

      expect(res.statusCode).toBe(500);
      expect(res.text).toMatch("Database error");
    });
  });
});
