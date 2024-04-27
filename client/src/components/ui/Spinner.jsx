import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Spinner({ className, style }) {
  return (
    <div className={cn("spinner-container", className)} style={style}>
      <div className="spinner" role="status" />
      <span className="visually-hidden">Loading...</span>
    </div>
  );
}

Spinner.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};
