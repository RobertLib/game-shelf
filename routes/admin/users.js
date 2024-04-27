const express = require("express");
const router = express.Router();
const isAuth = require("../../middlewares/is-auth");
const isAdmin = require("../../middlewares/is-admin");
const processTableQuery = require("../../middlewares/process-table-query");
const User = require("../../models/user");

const SORT_COLUMNS = ["id", "email", "role"];

router.get(
  "/",
  isAuth,
  isAdmin,
  processTableQuery(SORT_COLUMNS),
  async (req, res, next) => {
    try {
      const users = await User.findAll(req.query);

      res.json(users);
    } catch (error) {
      next(error);
    }
  }
);

router.get("/:id", isAuth, isAdmin, async (req, res, next) => {
  try {
    const user = await User.findById(req.params.id);

    if (!user) {
      return res.status(404).send("User not found.");
    }

    res.json(user);
  } catch (error) {
    next(error);
  }
});

router.post("/", isAuth, isAdmin, async (req, res, next) => {
  try {
    const user = await User.create(req.body);

    if (user.errors) {
      return res.status(400).send(user.errors);
    }

    res.status(201).send(user);
  } catch (error) {
    next(error);
  }
});

router.put("/:id", isAuth, isAdmin, async (req, res, next) => {
  try {
    const user = await User.update(req.params.id, req.body);

    if (user.errors) {
      return res.status(400).send(user.errors);
    }

    res.json(user);
  } catch (error) {
    next(error);
  }
});

router.delete("/:id", isAuth, isAdmin, async (req, res, next) => {
  try {
    await User.delete(req.params.id);

    res.status(204).send();
  } catch (error) {
    next(error);
  }
});

router.get("/stats", isAuth, isAdmin, async (req, res, next) => {
  try {
    const users = await User.getStats();

    res.json(users);
  } catch (error) {
    next(error);
  }
});

module.exports = router;
