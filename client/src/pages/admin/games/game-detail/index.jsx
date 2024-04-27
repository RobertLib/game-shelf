import {
  Breadcrumb,
  Button,
  DescriptionList,
  Spinner,
} from "../../../../components/ui";
import { useNavigate, useParams } from "react-router-dom";
import formatDate from "../../../../utils/formatDate";
import useQuery from "../../../../hooks/useQuery";

export default function GameDetail() {
  const { id } = useParams();

  const navigate = useNavigate();

  const { data, isLoading } = useQuery(`/api/admin/games/${id}`, [id], {
    cacheKey: "/api/admin/games",
    findId: id,
  });

  const game = data;

  if (isLoading && !game) {
    return <Spinner />;
  }

  return (
    <div className="container container-lg">
      <Breadcrumb
        items={[
          { label: "Home", link: "/" },
          { label: "Admin", link: "/admin" },
          { label: "Games", link: "/admin/games" },
          { label: game?.name ?? "Game" },
        ]}
      />

      <Button
        onClick={() => {
          navigate(-1);
        }}
        style={{ marginTop: "2rem" }}
        variant="link"
      >
        &lt; Back to Games
      </Button>

      <br />
      <br />

      <div className="panel" style={{ padding: "1.5rem" }}>
        <DescriptionList
          items={[
            { term: "Name", description: game?.name },
            { term: "Genre", description: game?.genre },
            {
              term: "Release Date",
              description: formatDate(game?.releaseDate),
            },
            { term: "Developer", description: game?.developer },
            { term: "Publisher", description: game?.publisher },
            { term: "Platform", description: game?.platform },
            { term: "Rating", description: game?.rating },
            {
              term: "Note",
              description: (
                <div dangerouslySetInnerHTML={{ __html: game?.note ?? "" }} />
              ),
            },
          ]}
        />
      </div>
    </div>
  );
}
