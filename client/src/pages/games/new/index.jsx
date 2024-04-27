import { Link } from "react-router-dom";
import GameForm from "../GameForm";

export default function NewGame() {
  return (
    <div className="container">
      <br />

      <Link className="link" to="/">
        &lt; Back to Home
      </Link>

      <h1>Add Game</h1>

      <GameForm />
    </div>
  );
}
