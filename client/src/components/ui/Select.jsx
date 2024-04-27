import "./Select.css";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Select({
  className,
  dim,
  error,
  fullWidth,
  label,
  options,
  required,
  style,
  ...rest
}) {
  return (
    <label className={className} style={style}>
      {label && (
        <span className="Select-label">
          {label}: {required && <span className="text-danger">*</span>}
        </span>
      )}
      <select
        {...rest}
        className={cn(
          "form-control",
          dim && `form-control-${dim}`,
          error && "is-invalid",
          fullWidth && "w-full"
        )}
        required={required}
      >
        {options.map(({ label, value }) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
      {error && (
        <div className="Select-error collapse-down text-danger text-sm">
          {error}
        </div>
      )}
    </label>
  );
}

Select.propTypes = {
  className: PropTypes.string,
  dim: PropTypes.oneOf(["sm", "md", "lg"]),
  error: PropTypes.string,
  fullWidth: PropTypes.bool,
  label: PropTypes.string,
  options: PropTypes.arrayOf(
    PropTypes.shape({
      label: PropTypes.string.isRequired,
      value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    })
  ).isRequired,
  required: PropTypes.bool,
  style: PropTypes.object,
};
