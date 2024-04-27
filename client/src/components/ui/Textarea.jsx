import "./Textarea.css";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Textarea({
  className,
  error,
  fullWidth,
  label,
  required,
  style,
  ...rest
}) {
  return (
    <label className={className} style={style}>
      {label && (
        <span className="Textarea-label">
          {label}: {required && <span className="text-danger">*</span>}
        </span>
      )}
      <span>
        <textarea
          {...rest}
          className={cn(
            "form-control",
            error && "is-invalid",
            fullWidth && "w-full"
          )}
          required={required}
        />
        {error && (
          <div className="Textarea-error collapse-down text-danger text-sm">
            {error}
          </div>
        )}
      </span>
    </label>
  );
}

Textarea.propTypes = {
  className: PropTypes.string,
  error: PropTypes.string,
  fullWidth: PropTypes.bool,
  label: PropTypes.string,
  required: PropTypes.bool,
  style: PropTypes.object,
};
