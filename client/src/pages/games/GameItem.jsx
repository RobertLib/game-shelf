import { Button, Rating } from "../../components/ui";
import { Link } from "react-router-dom";
import { useSession } from "../../contexts/session";
import { useSnackbar } from "../../contexts/snackbar";
import { useState } from "react";
import apiFetch from "../../utils/apiFetch";
import cn from "../../utils/cn";
import formatDate from "../../utils/formatDate";
import logger from "../../utils/logger";
import PropTypes from "prop-types";

export default function GameItem({ game, isNew, onDelete }) {
  const { currentUser } = useSession();
  const { enqueueSnackbar } = useSnackbar();

  const [isDeleting, setIsDeleting] = useState(false);

  const handleDelete = async () => {
    if (window.confirm("Are you sure you want to delete this game?")) {
      try {
        const response = await apiFetch(`/api/games/${game.id}`, {
          method: "DELETE",
        });

        if (!response.ok) {
          const message = await response.text();
          throw new Error(message);
        }

        enqueueSnackbar("Game deleted successfully.");

        setIsDeleting(true);

        setTimeout(() => {
          onDelete();
        }, 250);
      } catch (error) {
        logger.error(error);
      }
    }
  };

  return (
    <li
      className={cn("panel", isNew && "fade-in", isDeleting && "fade-out")}
      style={{ padding: "1rem" }}
    >
      {game.name} - {game.genre}
      <p className="text-muted text-sm">
        {formatDate(game.releaseDate, "date")}
      </p>
      {game.developer} - {game.publisher} - {game.platform}
      <Rating style={{ marginTop: "0.5rem" }} value={Number(game.rating)} />
      <div className="btn-group" style={{ marginTop: "1rem" }}>
        {currentUser?.id === game.ownerId && (
          <>
            <Link
              className="btn btn-sm btn-warning"
              to={`/games/${game.id}/edit`}
            >
              Edit
            </Link>
            <Button onClick={handleDelete} size="sm" variant="danger">
              Delete
            </Button>
          </>
        )}
      </div>
    </li>
  );
}

GameItem.propTypes = {
  game: PropTypes.object.isRequired,
  isNew: PropTypes.bool.isRequired,
  onDelete: PropTypes.func.isRequired,
};
