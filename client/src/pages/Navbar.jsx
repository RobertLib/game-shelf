import { Dropdown } from "../components/ui";
import { Link, useLocation } from "react-router-dom";
import { useDrawer } from "../contexts/drawer";
import { useSession } from "../contexts/session";

export default function Navbar() {
  const { currentUser } = useSession();
  const { pathname } = useLocation();
  const { toggleDrawer } = useDrawer();

  return (
    <nav className="navbar">
      <div className="navbar-container">
        <span style={{ alignItems: "center", display: "flex", gap: "0.5rem" }}>
          {pathname.startsWith("/admin") && (
            <button
              className="btn visible-sm"
              onClick={toggleDrawer}
              style={{ marginLeft: "-1rem" }}
              type="button"
            >
              <img
                alt="menu"
                className="icon"
                height={30}
                src="/icons/menu.svg"
                width={30}
              />
            </button>
          )}

          <Link className="link font-semibold text-lg" to="/">
            GameShelf
          </Link>
        </span>

        {currentUser && (
          <Dropdown
            label={currentUser.email}
            style={{ marginRight: "-0.5rem" }}
          >
            {currentUser.role === "ADMIN" && (
              <Link className="btn justify-start w-full" to="/admin/games">
                Admin
              </Link>
            )}
            <Link className="btn justify-start w-full" to="/auth/logout">
              Logout
            </Link>
          </Dropdown>
        )}
      </div>
    </nav>
  );
}
