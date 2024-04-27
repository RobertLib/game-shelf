const processTableQuery = (
  sortColumns,
  defaultSortKey = "id",
  defaultSortOrder = "desc",
  defaultLimit = 20,
  defaultOffset = 0
) => {
  return (req, res, next) => {
    const sortKey = req.query.sortKey || defaultSortKey;
    const sortOrder = req.query.sortOrder || defaultSortOrder;
    const limit = req.query.limit || defaultLimit;
    const offset = req.query.offset || defaultOffset;

    if (!sortColumns.includes(sortKey)) {
      return res.status(400).send("Invalid sort column.");
    }

    const parsedLimit = parseInt(String(limit), 10);
    const parsedOffset = parseInt(String(offset), 10);

    if (isNaN(parsedLimit) || parsedLimit <= 0) {
      return res.status(400).send("Invalid query parameters.");
    }

    if (isNaN(parsedOffset) || parsedOffset < 0) {
      return res.status(400).send("Invalid query parameters.");
    }

    req.query.sortKey = sortKey;
    req.query.sortOrder = sortOrder === "asc" ? "asc" : "desc";
    req.query.limit = String(parsedLimit);
    req.query.offset = String(parsedOffset);

    next();
  };
};

module.exports = processTableQuery;
