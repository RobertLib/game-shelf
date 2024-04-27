import {
  Accordion,
  Input,
  Pagination,
  Select,
  Spinner,
} from "../../components/ui";
import { Link, useSearchParams } from "react-router-dom";
import { useEffect, useRef } from "react";
import debounce from "../../utils/debounce";
import GameItem from "./GameItem";
import useQuery from "../../hooks/useQuery";

export default function GameList() {
  const [searchParams, setSearchParams] = useSearchParams();

  const searchTerm = searchParams.get("searchTerm") ?? "";

  const sortKey = searchParams.get("sortKey") ?? "";
  const sortOrder = searchParams.get("sortOrder") ?? "";

  const page = parseInt(searchParams.get("page") ?? "1", 10);
  const limit = 20;

  const { data, isLoading, refetch, total } = useQuery(
    `/api/games?${new URLSearchParams({
      name: searchTerm,
      sortKey,
      sortOrder,
      limit: limit.toString(),
      offset: String((page - 1) * limit),
    }).toString()}`
  );

  const games = data;

  const prevGamesRef = useRef(null);

  useEffect(() => {
    prevGamesRef.current = games;
  }, [games]);

  return (
    <>
      <div className="header">
        <h2>Games List</h2>

        <Link className="btn btn-primary btn-sm" to="/games/new">
          + Add Game
        </Link>
      </div>

      <Accordion summary="Filters">
        <div className="row" style={{ gap: "0.25rem" }}>
          <Input
            defaultValue={searchTerm}
            fullWidth
            onChange={debounce(({ target: { value } }) => {
              searchParams.set("searchTerm", value);
              searchParams.set("page", "1");
              setSearchParams(searchParams);
            }, 500)}
            placeholder="Search"
            type="search"
          />

          <Select
            defaultValue={sortKey}
            fullWidth
            onChange={({ target: { value } }) => {
              searchParams.set("sortKey", value);
              setSearchParams(searchParams);
            }}
            options={[
              { label: "", value: "" },
              { label: "Name", value: "name" },
              { label: "Genre", value: "genre" },
            ]}
          />

          <Select
            defaultValue={sortOrder}
            fullWidth
            onChange={({ target: { value } }) => {
              searchParams.set("sortOrder", value);
              setSearchParams(searchParams);
            }}
            options={[
              { label: "", value: "" },
              { label: "Asc", value: "asc" },
              { label: "Desc", value: "desc" },
            ]}
          />
        </div>
      </Accordion>

      {isLoading && games?.length === 0 ? (
        <Spinner />
      ) : (
        <>
          <ul className="col list-style-none" style={{ gap: "1rem" }}>
            {games?.map((game) => (
              <GameItem
                game={game}
                isNew={
                  prevGamesRef.current
                    ? !prevGamesRef.current.some(({ id }) => id === game.id)
                    : false
                }
                key={game.id}
                onDelete={() => refetch()}
              />
            ))}
          </ul>

          <Pagination
            limit={limit}
            onChange={(page) => {
              searchParams.set("page", String(page));
              setSearchParams(searchParams);
            }}
            page={page}
            style={{ marginTop: "1.35rem" }}
            total={total}
          />
        </>
      )}
    </>
  );
}
