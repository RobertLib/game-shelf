const db = require("../db");

const checkOwner = (tableName, ownerField = "ownerId") => {
  return async (req, res, next) => {
    try {
      const { id } = req.params;

      const ownerCheck = await db.query(
        `SELECT "${ownerField}" FROM "${tableName}" WHERE "id" = $1`,
        [id]
      );

      if (ownerCheck.rows.length === 0) {
        return res.status(404).send(`Record not found in ${tableName}.`);
      }

      const record = ownerCheck.rows[0];

      if (record[ownerField] !== req.user?.id) {
        return res
          .status(403)
          .send(`You are not the owner of this record in ${tableName}.`);
      }

      next();
    } catch (error) {
      res.status(500).send(error.message);
    }
  };
};

module.exports = checkOwner;
