const express = require("express");
const router = express.Router();
const isAuth = require("../middlewares/is-auth");
const checkOwner = require("../middlewares/check-owner");
const processTableQuery = require("../middlewares/process-table-query");
const Game = require("../models/game");

const SORT_COLUMNS = [
  "id",
  "name",
  "genre",
  "releaseDate",
  "developer",
  "publisher",
  "platform",
  "rating",
];

router.get(
  "/",
  isAuth,
  processTableQuery(SORT_COLUMNS),
  async (req, res, next) => {
    try {
      const games = await Game.findAll(req.query);

      res.json(games);
    } catch (error) {
      next(error);
    }
  }
);

router.get("/:id", isAuth, async (req, res, next) => {
  try {
    const game = await Game.findById(req.params.id);

    if (!game) {
      return res.status(404).send("Game not found.");
    }

    res.json(game);
  } catch (error) {
    next(error);
  }
});

router.post("/", isAuth, async (req, res, next) => {
  try {
    const game = await Game.create({ ...req.body, ownerId: req.user?.id });

    if (game.errors) {
      return res.status(400).send(game.errorMessage);
    }

    res.status(201).json(game);
  } catch (error) {
    next(error);
  }
});

router.put("/:id", isAuth, checkOwner("games"), async (req, res, next) => {
  try {
    const game = await Game.update(req.params.id, req.body);

    if (game.errors) {
      return res.status(400).send(game.errorMessage);
    }

    res.json(game);
  } catch (error) {
    next(error);
  }
});

router.delete("/:id", isAuth, checkOwner("games"), async (req, res, next) => {
  try {
    const game = await Game.delete(req.params.id);

    if (!game) {
      return res.status(404).send("Game not found.");
    }

    res.status(204).send();
  } catch (error) {
    next(error);
  }
});

module.exports = router;
