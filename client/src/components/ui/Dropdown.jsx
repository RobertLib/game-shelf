import { useEffect, useState } from "react";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Dropdown({ className, children, label, style }) {
  const [isOpen, setIsOpen] = useState(false);

  const handleClickOutside = (event) => {
    if (!(event.target instanceof HTMLElement)) {
      return;
    }

    if (!event.target.closest(".dropdown")) {
      setIsOpen(false);
    } else if (event.target.closest(".dropdown-content")) {
      setTimeout(() => setIsOpen(false), 250);
    }
  };

  useEffect(() => {
    document.addEventListener("mousedown", handleClickOutside);

    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, []);

  const handleClickToggle = () => setIsOpen((prevState) => !prevState);

  return (
    <div className={cn("dropdown", className)} style={style}>
      <button
        aria-expanded={isOpen}
        className="btn"
        onClick={handleClickToggle}
        title="Open settings"
        type="button"
      >
        {label}
      </button>

      {isOpen && (
        <div
          aria-label="Dropdown content"
          className="dropdown-content"
          role="region"
        >
          {children}
        </div>
      )}
    </div>
  );
}

Dropdown.propTypes = {
  className: PropTypes.string,
  children: PropTypes.node.isRequired,
  label: PropTypes.string.isRequired,
  style: PropTypes.object,
};
