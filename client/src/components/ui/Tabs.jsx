import { Link, useLocation } from "react-router-dom";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Tabs({ className, items, style }) {
  const { pathname } = useLocation();

  return (
    <ul className={cn("tabs", className)} style={style}>
      {items.map((item) => (
        <li
          className={cn(pathname.startsWith(item.link) && "active")}
          key={item.link}
        >
          <Link className="link" to={item.link}>
            {item.label}
          </Link>
        </li>
      ))}
    </ul>
  );
}

Tabs.propTypes = {
  className: PropTypes.string,
  items: PropTypes.arrayOf(
    PropTypes.shape({
      label: PropTypes.string.isRequired,
      link: PropTypes.string.isRequired,
    })
  ).isRequired,
  style: PropTypes.object,
};
