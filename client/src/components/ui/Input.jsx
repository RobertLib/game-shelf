import "./Input.css";
import { useState } from "react";
import Button from "./Button";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Input({
  className,
  dim,
  error,
  fullWidth,
  label,
  required,
  style,
  type = "text",
  ...rest
}) {
  const [typeState, setTypeState] = useState(type);

  return (
    <label className={className} style={style}>
      {label && (
        <span className="Input-label">
          {label}: {required && <span className="text-danger">*</span>}
        </span>
      )}
      <span>
        <span className="Input-container">
          <input
            {...rest}
            className={cn(
              "form-control",
              dim && `form-control-${dim}`,
              error && "is-invalid",
              fullWidth && "w-full"
            )}
            required={required}
            type={type === "password" ? typeState : type}
          />
          {type === "password" && (
            <Button
              onClick={() => {
                setTypeState((prevState) =>
                  prevState === "password" ? "text" : "password"
                );
              }}
              variant="default"
            >
              <img
                alt="eye"
                className="icon"
                src={
                  typeState === "password"
                    ? "/icons/eye.svg"
                    : "/icons/eye-slash.svg"
                }
              />
            </Button>
          )}
        </span>
        {error && (
          <div className="Input-error collapse-down text-danger text-sm">
            {error}
          </div>
        )}
      </span>
    </label>
  );
}

Input.propTypes = {
  className: PropTypes.string,
  dim: PropTypes.oneOf(["sm", "md", "lg"]),
  error: PropTypes.string,
  fullWidth: PropTypes.bool,
  label: PropTypes.string,
  required: PropTypes.bool,
  style: PropTypes.object,
  type: PropTypes.string,
};
