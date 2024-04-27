import { Button, Editor, Input, Textarea } from "../../components/ui";
import { useNavigate } from "react-router-dom";
import { useSnackbar } from "../../contexts/snackbar";
import { useState } from "react";
import apiFetch from "../../utils/apiFetch";
import formatDate from "../../utils/formatDate";
import logger from "../../utils/logger";
import PropTypes from "prop-types";

export default function GameForm({ game }) {
  const [data, setData] = useState({
    name: game?.name ?? "",
    note: game?.note ?? "",
    genre: game?.genre ?? "",
    releaseDate: formatDate(game?.releaseDate ?? new Date(), "YYYY-MM-DD"),
    developer: game?.developer ?? "",
    publisher: game?.publisher ?? "",
    platform: game?.platform ?? "",
    rating: game?.rating ?? "0",
  });

  const [isLoading, setIsLoading] = useState(false);

  const { enqueueSnackbar } = useSnackbar();

  const navigate = useNavigate();

  const handleChange = ({ target: { name, value } }) => {
    setData((prevState) => ({ ...prevState, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    try {
      setIsLoading(true);

      let response;

      if (game) {
        response = await apiFetch(`/api/games/${game.id}`, {
          method: "PUT",
          body: JSON.stringify(data),
        });
      } else {
        response = await apiFetch("/api/games", {
          method: "POST",
          body: JSON.stringify(data),
        });
      }

      if (!response.ok) {
        const message = await response.text();
        throw new Error(message);
      }

      const result = await response.json();

      logger.info(`Game ${game ? "updated" : "created"}:`, result);

      enqueueSnackbar(`Game ${game ? "updated" : "created"} successfully.`);

      navigate("/");
    } catch (error) {
      logger.error("Error creating game:", error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <form
      className="panel"
      onSubmit={handleSubmit}
      style={{ padding: "1.5rem" }}
    >
      <div className="form-horizontal">
        <Textarea
          label="Name"
          maxLength={255}
          name="name"
          onChange={handleChange}
          required
          value={data.name}
        />

        <Input
          label="Genre"
          maxLength={100}
          name="genre"
          onChange={handleChange}
          required
          value={data.genre}
        />

        <Input
          label="Release Date"
          name="releaseDate"
          onChange={handleChange}
          type="date"
          value={data.releaseDate}
        />

        <Input
          label="Developer"
          maxLength={255}
          name="developer"
          onChange={handleChange}
          value={data.developer}
        />

        <Input
          label="Publisher"
          maxLength={255}
          name="publisher"
          onChange={handleChange}
          value={data.publisher}
        />

        <Input
          label="Platform"
          maxLength={255}
          name="platform"
          onChange={handleChange}
          value={data.platform}
        />

        <Input
          label="Rating"
          name="rating"
          onChange={handleChange}
          step="0.1"
          type="number"
          value={data.rating}
        />

        <Editor
          label="Note"
          onChange={(value) => {
            setData((prevState) => ({ ...prevState, note: value }));
          }}
          value={data.note}
        />
      </div>

      <Button
        loading={isLoading}
        style={{ marginTop: "1.25rem" }}
        type="submit"
      >
        {game ? "Update" : "Add"} Game
      </Button>
    </form>
  );
}

GameForm.propTypes = {
  game: PropTypes.object,
};
