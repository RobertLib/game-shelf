import {
  Breadcrumb,
  Button,
  DescriptionList,
  Spinner,
} from "../../../../components/ui";
import { useNavigate, useParams } from "react-router-dom";
import useQuery from "../../../../hooks/useQuery";

export default function UserDetail() {
  const { id } = useParams();

  const navigate = useNavigate();

  const { data, isLoading } = useQuery(`/api/admin/users/${id}`, [id], {
    cacheKey: "/api/admin/users",
    findId: id,
  });

  const user = data;

  if (isLoading && !user) {
    return <Spinner />;
  }

  return (
    <div className="container container-lg">
      <Breadcrumb
        items={[
          { label: "Home", link: "/" },
          { label: "Admin", link: "/admin" },
          { label: "Users", link: "/admin/users" },
          { label: user?.email ?? "User" },
        ]}
      />

      <Button
        onClick={() => {
          navigate(-1);
        }}
        style={{ marginTop: "2rem" }}
        variant="link"
      >
        &lt; Back to Users
      </Button>

      <br />
      <br />

      <div className="panel" style={{ padding: "1.5rem" }}>
        <DescriptionList
          items={[
            { term: "Email", description: user?.email },
            { term: "Role", description: user?.role },
          ]}
        />
      </div>
    </div>
  );
}
