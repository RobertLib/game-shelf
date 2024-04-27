import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Button({
  children,
  className,
  disabled,
  loading,
  size,
  style = {},
  type = "button",
  variant = "primary",
  ...rest
}) {
  return (
    <button
      {...rest}
      className={cn(
        "btn",
        `btn-${variant}`,
        size && `btn-${size}`,
        loading && "loading",
        className
      )}
      disabled={disabled}
      style={{ ...style, opacity: disabled ? 0.5 : 1 }}
      type={type}
    >
      {children}
    </button>
  );
}

Button.propTypes = {
  children: PropTypes.node.isRequired,
  className: PropTypes.string,
  disabled: PropTypes.bool,
  loading: PropTypes.bool,
  size: PropTypes.oneOf(["sm", "md", "lg"]),
  style: PropTypes.object,
  type: PropTypes.oneOf(["button", "submit", "reset"]),
  variant: PropTypes.oneOf([
    "primary",
    "danger",
    "warning",
    "default",
    "link",
    "icon",
  ]),
};
