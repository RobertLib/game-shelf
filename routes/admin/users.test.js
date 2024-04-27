const { createApp, db, mockDbQuery, request } = require("../../setup-tests");
const usersRouter = require("./users");

const app = createApp("/users", usersRouter);

describe("GET /users", () => {
  it("should return all users with default pagination and sorting", async () => {
    mockDbQuery(
      [
        { id: 1, email: "user1@example.com", role: "USER" },
        { id: 2, email: "user2@example.com", role: "ADMIN" },
      ],
      2
    );

    const res = await request(app).get("/users");

    expect(res.statusCode).toBe(200);
    expect(res.body.data).toEqual([
      { id: 1, email: "user1@example.com", role: "USER" },
      { id: 2, email: "user2@example.com", role: "ADMIN" },
    ]);
    expect(res.body.total).toBe(2);
  });

  it("should return filtered users by email and role", async () => {
    mockDbQuery([{ id: 1, email: "user1@example.com", role: "USER" }], 1);

    const res = await request(app)
      .get("/users")
      .query({ email: "user1", role: "USER" });

    expect(res.statusCode).toBe(200);
    expect(res.body.data).toEqual([
      { id: 1, email: "user1@example.com", role: "USER" },
    ]);
    expect(res.body.total).toBe(1);
  });

  it("should return 400 for invalid sort column", async () => {
    const res = await request(app)
      .get("/users")
      .query({ sortKey: "invalidColumn" });

    expect(res.statusCode).toBe(400);
    expect(res.text).toBe("Invalid sort column.");
  });

  it("should return 500 for server error", async () => {
    db.query.mockRejectedValueOnce(new Error("Database error"));

    const res = await request(app).get("/users");

    expect(res.statusCode).toBe(500);
    expect(res.text).toMatch("Database error");
  });
});
