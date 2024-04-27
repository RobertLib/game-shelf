const express = require("express");
const router = express.Router();
const bcrypt = require("bcrypt");
const jwt = require("jsonwebtoken");
const User = require("../models/user");

router.post("/login", async (req, res, next) => {
  try {
    const { email, password } = req.body;

    const user = await User.findByEmail(email);

    if (!user) {
      return res
        .status(400)
        .send({ errorMessage: "Invalid email or password." });
    }

    if (user.deletedAt) {
      return res
        .status(400)
        .send({ errorMessage: "Your account has been deleted." });
    }

    const match = await bcrypt.compare(password, user.password);

    if (!match) {
      return res
        .status(400)
        .send({ errorMessage: "Invalid email or password." });
    }

    const token = jwt.sign(
      {
        id: user.id,
        email: user.email,
        role: user.role,
      },
      process.env.JWT_SECRET ?? "",
      {
        expiresIn: "7d",
      }
    );

    res.status(200).send({ token });
  } catch (error) {
    next(error);
  }
});

router.post("/register", async (req, res, next) => {
  try {
    const user = await User.create(req.body);

    if (user.errors) {
      return res.status(400).send(user.errors);
    }

    const token = jwt.sign(
      {
        id: user.id,
        email: user.email,
        role: user.role,
      },
      process.env.JWT_SECRET ?? "",
      {
        expiresIn: "7d",
      }
    );

    res.status(201).send({ ...user, token });
  } catch (error) {
    next(error);
  }
});

module.exports = router;
