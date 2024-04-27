import { Drawer } from "../../components/ui";
import { Route, Routes } from "react-router-dom";
import Admin from ".";
import GameDetail from "./games/game-detail";
import Games from "./games";
import NoPage from "../NoPage";
import UserDetail from "./users/user-detail";
import Users from "./users";

export default function AdminLayout() {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "max-content 1fr",
      }}
    >
      <Drawer
        items={[
          { label: "Games", link: "/admin/games" },
          { label: "Users", link: "/admin/users" },
        ]}
      />

      <div style={{ minWidth: 0 }}>
        <Routes>
          <Route path="" element={<Admin />} />
          <Route path="games" element={<Games />} />
          <Route path="games/:id" element={<GameDetail />} />
          <Route path="users" element={<Users />} />
          <Route path="users/:id" element={<UserDetail />} />
          <Route path="*" element={<NoPage />} />
        </Routes>
      </div>
    </div>
  );
}
