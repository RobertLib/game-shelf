const { createApp, db, mockDbQuery, request } = require("../setup-tests");
const authRouter = require("./auth");
const bcrypt = require("bcrypt");
const jwt = require("jsonwebtoken");

const app = createApp("/auth", authRouter);

describe("POST /auth/login", () => {
  it("should log in a user with correct email and password", async () => {
    const user = {
      id: 1,
      email: "user@example.com",
      password: "hashedpassword",
      role: "USER",
    };
    const token = "testtoken";

    mockDbQuery([user]);

    bcrypt.compare.mockResolvedValueOnce(true);
    jwt.sign.mockReturnValueOnce(token);

    const res = await request(app)
      .post("/auth/login")
      .send({ email: "user@example.com", password: "password" });

    expect(res.statusCode).toBe(200);
    expect(res.body).toEqual({ token });
  });

  it("should return 400 for invalid email or password", async () => {
    mockDbQuery([]);

    const res = await request(app)
      .post("/auth/login")
      .send({ email: "invalid@example.com", password: "password" });

    expect(res.statusCode).toBe(400);
    expect(res.body).toEqual({ errorMessage: "Invalid email or password." });
  });

  it("should return 400 for incorrect password", async () => {
    const user = {
      id: 1,
      email: "user@example.com",
      password: "hashedpassword",
      role: "USER",
    };

    mockDbQuery([user]);

    bcrypt.compare.mockResolvedValueOnce(false);

    const res = await request(app)
      .post("/auth/login")
      .send({ email: "user@example.com", password: "wrongpassword" });

    expect(res.statusCode).toBe(400);
    expect(res.body).toEqual({ errorMessage: "Invalid email or password." });
  });

  it("should return 500 for server error", async () => {
    db.query.mockRejectedValueOnce(new Error("Database error"));

    const res = await request(app)
      .post("/auth/login")
      .send({ email: "user@example.com", password: "password" });

    expect(res.statusCode).toBe(500);
    expect(res.text).toMatch("Database error");
  });
});

describe("POST /auth/register", () => {
  it("should register a new user", async () => {
    const user = { id: 1, email: "user@example.com", role: "USER" };
    const token = "testtoken";

    mockDbQuery([user]);

    bcrypt.hash.mockResolvedValueOnce("hashedpassword");
    jwt.sign.mockReturnValueOnce(token);

    const res = await request(app).post("/auth/register").send({
      email: "user@example.com",
      password: "password",
      passwordConfirm: "password",
    });

    expect(res.statusCode).toBe(201);
    expect(res.body).toEqual({ ...user, token });
  });

  it("should return 400 for missing fields", async () => {
    const res = await request(app).post("/auth/register").send({
      email: "",
      password: "",
      passwordConfirm: "",
    });

    expect(res.statusCode).toBe(400);
    expect(res.body).toEqual({
      email: "Email is required.",
      password: "Password is required.",
      passwordConfirm: "Password confirmation is required.",
    });
  });

  it("should return 400 for invalid password confirmation", async () => {
    const res = await request(app).post("/auth/register").send({
      email: "user@example.com",
      password: "password",
      passwordConfirm: "differentpassword",
    });

    expect(res.statusCode).toBe(400);
    expect(res.body).toEqual({
      passwordConfirm: "Passwords do not match.",
    });
  });

  it("should return 400 for short password", async () => {
    const res = await request(app).post("/auth/register").send({
      email: "user@example.com",
      password: "123",
      passwordConfirm: "123",
    });

    expect(res.statusCode).toBe(400);
    expect(res.body).toEqual({
      password: "Password must be at least 6 characters long.",
    });
  });

  it("should return 400 if email already exists", async () => {
    db.query.mockRejectedValueOnce({
      code: "23505",
    });

    const res = await request(app).post("/auth/register").send({
      email: "user@example.com",
      password: "password",
      passwordConfirm: "password",
    });

    expect(res.statusCode).toBe(400);
    expect(res.body).toEqual({ email: "User with this email already exists." });
  });

  it("should return 500 for server error", async () => {
    db.query.mockRejectedValueOnce(new Error("Database error"));

    const res = await request(app).post("/auth/register").send({
      email: "user@example.com",
      password: "password",
      passwordConfirm: "password",
    });

    expect(res.statusCode).toBe(500);
    expect(res.text).toMatch("Database error");
  });
});
