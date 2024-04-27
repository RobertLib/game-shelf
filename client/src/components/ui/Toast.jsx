import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Toast({
  className,
  message,
  style,
  variant = "primary",
}) {
  return (
    <div
      className={cn("toast", `toast-${variant}`, className)}
      role="alert"
      style={style}
    >
      {message}
    </div>
  );
}

Toast.propTypes = {
  className: PropTypes.string,
  message: PropTypes.string.isRequired,
  style: PropTypes.object,
  variant: PropTypes.oneOf(["primary", "danger", "default"]),
};
