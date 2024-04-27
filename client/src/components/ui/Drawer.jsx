import "./Drawer.css";
import { Link, useLocation } from "react-router-dom";
import { useDrawer } from "../../contexts/drawer";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Drawer({ className, items, style }) {
  const { isOpen, toggleDrawer } = useDrawer();
  const { pathname } = useLocation();

  if (!isOpen) {
    return <div />;
  }

  return (
    <div className={className} style={style}>
      <div
        aria-label="Drawer backdrop"
        className="Drawer-backdrop backdrop"
        onClick={() => {
          toggleDrawer();
        }}
        onKeyDown={(event) => {
          if (["Escape", "Enter", " "].includes(event.key)) {
            toggleDrawer();
          }
        }}
        role="button"
        tabIndex={0}
      />

      <nav className="drawer">
        <div className="drawer-container">
          <ul>
            {items.map((item) => (
              <li key={item.link}>
                <Link
                  className={cn(
                    "link font-semibold",
                    pathname.startsWith(item.link) && "active"
                  )}
                  onClick={() => {
                    if (window.innerWidth <= 768) {
                      toggleDrawer();
                    }
                  }}
                  to={item.link}
                >
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </nav>
    </div>
  );
}

Drawer.propTypes = {
  className: PropTypes.string,
  items: PropTypes.arrayOf(
    PropTypes.shape({
      label: PropTypes.string.isRequired,
      link: PropTypes.string.isRequired,
    })
  ).isRequired,
  style: PropTypes.object,
};
