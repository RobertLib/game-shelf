import "./Switch.css";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Switch({ className, label, style, ...rest }) {
  return (
    <label className={cn("Switch", className)} style={style}>
      {label && <span className="font-semibold">{label}:</span>}
      <span className="switch">
        <input {...rest} type="checkbox" />
        <span className="switch-slider" />
      </span>
    </label>
  );
}

Switch.propTypes = {
  className: PropTypes.string,
  label: PropTypes.string,
  style: PropTypes.object,
};
