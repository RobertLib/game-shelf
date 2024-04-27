const jwt = require("jsonwebtoken");
const User = require("../models/user");

async function isAuth(req, res, next) {
  const token = req.headers.authorization?.split(" ")[1];

  if (!token) {
    return res.status(401).send("No token provided.");
  }

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET ?? "");

    if (typeof decoded !== "object") {
      return res.status(401).send("Invalid token.");
    }

    const user = await User.findById(decoded.id);

    if (!user || user.deletedAt) {
      res.status(401).send("User not found or has been deleted.");
    }

    req.user = {
      id: decoded.id,
      email: decoded.email,
      role: decoded.role,
    };

    next();
  } catch (error) {
    res.status(401).send("Not authorized.");
  }
}

module.exports = isAuth;
