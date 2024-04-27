const express = require("express");
const request = require("supertest");
const db = require("./db");

process.env.JWT_SECRET = "testsecret";

jest.mock("bcrypt");
jest.mock("jsonwebtoken");

jest.mock("./middlewares/is-auth", () => (req, res, next) => {
  req.user = { id: 1, role: "ADMIN" };
  next();
});

jest.mock("./db");

const mockDbQuery = (data, count) => {
  db.query.mockResolvedValueOnce({ rows: data });
  if (count !== undefined) {
    db.query.mockResolvedValueOnce({
      rows: [{ count: count.toString() }],
    });
  }
};

function createApp(basePath, router) {
  const app = express();
  app.use(express.json());
  app.use(basePath, router);

  return app;
}

beforeEach(() => {
  jest.resetAllMocks();
});

module.exports = {
  createApp,
  db,
  mockDbQuery,
  request,
};
