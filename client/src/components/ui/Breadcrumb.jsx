import { Link } from "react-router-dom";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Breadcrumb({ className, items, style }) {
  return (
    <nav
      aria-label="Breadcrumb"
      className={cn("breadcrumb", className)}
      style={style}
    >
      <ol>
        {items.map((item, index) => {
          const isLast = index === items.length - 1;

          return (
            <li
              aria-current={isLast ? "page" : undefined}
              className={cn(isLast && "active")}
              key={index}
            >
              {isLast ? (
                item.label
              ) : (
                <Link className="link" to={item.link ?? ""}>
                  {item.label}
                </Link>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

Breadcrumb.propTypes = {
  className: PropTypes.string,
  items: PropTypes.arrayOf(
    PropTypes.shape({
      label: PropTypes.node.isRequired,
      link: PropTypes.string,
    })
  ).isRequired,
  style: PropTypes.object,
};
