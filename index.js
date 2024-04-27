require("dotenv").config();

const express = require("express");
const path = require("path");
const helmet = require("helmet");
const rateLimit = require("express-rate-limit");
const compression = require("compression");
const logger = require("./middlewares/logger");

const app = express();

app.use(helmet());

const limiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 300,
});

app.use(limiter);

app.use(compression());

app.use(express.json());
app.use(express.static(path.join(__dirname, "client", "dist")));

app.use((req, res, next) => {
  const currentTime = new Date().toISOString();
  logger.info(`[${currentTime}] Request: ${req.method} ${req.url}`);
  next();
});

const authRoutes = require("./routes/auth");
const gamesRoutes = require("./routes/games");
const adminGamesRoutes = require("./routes/admin/games");
const adminUsersRoutes = require("./routes/admin/users");

app.use("/api/auth", authRoutes);
app.use("/api/games", gamesRoutes);
app.use("/api/admin/games", adminGamesRoutes);
app.use("/api/admin/users", adminUsersRoutes);

app.get("*", (req, res) => {
  res.sendFile(path.join(__dirname, "client", "dist", "index.html"));
});

app.use((err, req, res, next) => {
  logger.error(err.stack);

  res.status(500).send("Something broke!");
});

const PORT = process.env.PORT || 3000;

app.listen(PORT, () => {
  console.log(`Server is running on port ${PORT}`);
});
