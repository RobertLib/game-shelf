import GameList from "./games/GameList";

export default function Home() {
  return (
    <div className="container">
      <h1 style={{ marginBottom: "0.5rem" }}>Welcome to Game Database</h1>

      <GameList />
    </div>
  );
}
