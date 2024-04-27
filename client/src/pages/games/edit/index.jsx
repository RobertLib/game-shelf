import { Link, useParams } from "react-router-dom";
import GameForm from "../GameForm";
import useQuery from "../../../hooks/useQuery";

export default function EditGame() {
  const { id } = useParams();

  const { data } = useQuery(`/api/games/${id}`, [id], {
    cacheKey: "/api/games",
    findId: id,
  });

  const game = data;

  return (
    <div className="container">
      <br />

      <Link className="link" to="/">
        &lt; Back to Home
      </Link>

      <h1>Update Game</h1>

      {game ? <GameForm game={game} /> : <p>Loading...</p>}
    </div>
  );
}
