const { createApp, db, mockDbQuery, request } = require("../../setup-tests");
const gamesRouter = require("./games");

const app = createApp("/games", gamesRouter);

describe("GET /games", () => {
  it("should return all games with default pagination", async () => {
    mockDbQuery(
      [
        { id: 1, name: "Game 1", genre: "Adventure" },
        { id: 2, name: "Game 2", genre: "Action" },
      ],
      2
    );

    const res = await request(app).get("/games");

    expect(res.statusCode).toBe(200);
    expect(res.body.data).toEqual([
      { id: 1, name: "Game 1", genre: "Adventure" },
      { id: 2, name: "Game 2", genre: "Action" },
    ]);
    expect(res.body.total).toBe(2);
  });

  it("should return paginated games", async () => {
    mockDbQuery([{ id: 1, name: "Game 1", genre: "Adventure" }], 1);

    const res = await request(app).get("/games").query({ limit: 1, offset: 0 });

    expect(res.statusCode).toBe(200);
    expect(res.body.data).toEqual([
      { id: 1, name: "Game 1", genre: "Adventure" },
    ]);
    expect(res.body.total).toBe(1);
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
    const game = { id: 1, name: "Game 1", genre: "Adventure" };

    mockDbQuery([game], 1);

    const res = await request(app).get("/games/1");

    expect(res.statusCode).toBe(200);
    expect(res.body).toEqual(game);
  });

  it("should return 404 if game not found", async () => {
    mockDbQuery([], 0);

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

describe("DELETE /games/:id", () => {
  it("should delete a game by id", async () => {
    const game = { id: 1, name: "Game 1", genre: "Adventure" };

    mockDbQuery([game], 1);

    const res = await request(app).delete("/games/1");

    expect(res.statusCode).toBe(204);
  });

  it("should return 404 if game to delete not found", async () => {
    mockDbQuery([], 0);

    const res = await request(app).delete("/games/1");

    expect(res.statusCode).toBe(404);
    expect(res.text).toMatch("Game not found.");
  });

  it("should return 500 for server error", async () => {
    db.query.mockRejectedValueOnce(new Error("Database error"));

    const res = await request(app).delete("/games/1");

    expect(res.statusCode).toBe(500);
    expect(res.text).toMatch("Database error");
  });
});
