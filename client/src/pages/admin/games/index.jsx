import {
  Breadcrumb,
  Button,
  DataTable,
  Dialog,
  Rating,
  Switch,
} from "../../../components/ui";
import { Link, useSearchParams } from "react-router-dom";
import { useDataTable } from "../../../components/ui/DataTable";
import { useMemo } from "react";
import { useSnackbar } from "../../../contexts/snackbar";
import apiFetch from "../../../utils/apiFetch";
import formatDate from "../../../utils/formatDate";
import GameForm from "./GameForm";
import logger from "../../../utils/logger";
import useQuery from "../../../hooks/useQuery";

export default function Games() {
  const [searchParams, setSearchParams] = useSearchParams();

  const { enqueueSnackbar } = useSnackbar();

  const dataTableParams = useDataTable();

  const deleted = searchParams.get("deleted") ?? "false";
  const dialog = searchParams.get("dialog") ?? "";
  const gameId = parseInt(searchParams.get("gameId") ?? "", 10);

  const { data, isLoading, refetch, total } = useQuery(
    `/api/admin/games?${new URLSearchParams({
      ...dataTableParams,
      deleted,
    }).toString()}`
  );

  const games = data;

  const columns = useMemo(
    () => [
      {
        filter: "input",
        key: "name",
        label: "Name",
        sortable: true,
      },
      {
        key: "genre",
        label: "Genre",
      },
      {
        key: "releaseDate",
        label: "Release date",
        render: (row) => formatDate(row.releaseDate),
      },
      {
        key: "developer",
        label: "Developer",
      },
      {
        key: "publisher",
        label: "Publisher",
      },
      {
        key: "platform",
        label: "Platform",
      },
      {
        key: "rating",
        label: "Rating",
        render: (row) => <Rating value={Number(row.rating)} />,
      },
      {
        key: "note",
        label: "Note",
      },
    ],
    []
  );

  const openGameForm = (gameId) => {
    searchParams.set("dialog", "gameForm");
    searchParams.set("gameId", gameId ? gameId.toString() : "");
    setSearchParams(searchParams);
  };

  const closeGameForm = () => {
    searchParams.delete("dialog");
    searchParams.delete("gameId");
    setSearchParams(searchParams);
  };

  const game = games?.find(({ id }) => id === gameId);

  const handleDelete = async (id) => {
    if (window.confirm("Are you sure you want to delete this game?")) {
      try {
        const response = await apiFetch(`/api/admin/games/${id}`, {
          method: "DELETE",
        });

        if (!response.ok) {
          const message = await response.text();
          throw new Error(message);
        }

        enqueueSnackbar("Game deleted successfully.");

        refetch();
      } catch (error) {
        logger.error(error);
      }
    }
  };

  return (
    <div className="container container-fluid">
      <Breadcrumb
        items={[
          { label: "Home", link: "/" },
          { label: "Admin", link: "/admin" },
          { label: "Games" },
        ]}
      />

      <div className="header">
        <h1>Games</h1>

        <Button onClick={() => openGameForm()} size="sm">
          + New Game
        </Button>
      </div>

      <DataTable
        actions={(row) => (
          <div className="btn-group">
            <Link
              className="btn btn-primary btn-sm"
              to={`/admin/games/${row.id}`}
            >
              Detail
            </Link>
            <Button
              onClick={() => openGameForm(row.id)}
              size="sm"
              variant="warning"
            >
              Edit
            </Button>
            {deleted !== "true" && (
              <Button
                onClick={() => handleDelete(row.id)}
                size="sm"
                variant="danger"
              >
                Delete
              </Button>
            )}
          </div>
        )}
        columns={columns}
        data={games}
        loading={isLoading}
        toolbar={
          <Switch
            defaultChecked={deleted === "true"}
            label="Deleted"
            onChange={() => {
              const newDeletedValue = deleted === "true" ? "false" : "true";
              searchParams.set("deleted", newDeletedValue);
              searchParams.set("page", "1");
              setSearchParams(searchParams);
            }}
          />
        }
        total={total}
      />

      {dialog === "gameForm" && (
        <Dialog
          onClose={() => closeGameForm()}
          size="lg"
          title={`${game ? "Edit" : "New"} Game`}
        >
          <GameForm
            game={game}
            onSubmit={() => {
              closeGameForm();
              refetch();
            }}
          />
        </Dialog>
      )}
    </div>
  );
}
