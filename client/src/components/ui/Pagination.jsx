import "./Pagination.css";
import Button from "./Button";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Pagination({
  className,
  limit,
  onChange,
  page,
  style,
  total,
}) {
  return (
    <ul className={cn("pagination", className)} style={style}>
      <li className="Pagination-info text-sm">
        {page} of{" "}
        {typeof total === "undefined" || typeof limit === "undefined"
          ? 1
          : Math.ceil(total / limit) || 1}
      </li>
      <li>
        <Button
          disabled={page <= 1}
          onClick={() => onChange(1)}
          size="sm"
          variant="default"
        >
          &lt;&lt;
        </Button>
      </li>
      <li>
        <Button
          disabled={page <= 1}
          onClick={() => onChange(page - 1)}
          size="sm"
          variant="default"
        >
          Prev
        </Button>
      </li>
      <li>
        <Button
          disabled={
            typeof limit === "undefined" || typeof total === "undefined"
              ? true
              : page * limit >= total
          }
          onClick={() => onChange(page + 1)}
          size="sm"
          variant="default"
        >
          Next
        </Button>
      </li>
    </ul>
  );
}

Pagination.propTypes = {
  className: PropTypes.string,
  limit: PropTypes.number,
  onChange: PropTypes.func.isRequired,
  page: PropTypes.number.isRequired,
  style: PropTypes.object,
  total: PropTypes.number,
};
