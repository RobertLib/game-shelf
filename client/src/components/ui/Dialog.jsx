import { useEffect, useRef } from "react";
import Button from "./Button";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Dialog({
  className,
  children,
  onClose,
  size,
  style,
  title,
}) {
  const dialogRef = useRef(null);

  useEffect(() => {
    dialogRef.current?.showModal();
  }, []);

  return (
    <dialog
      className={cn("dialog", size && `dialog-${size}`, className)}
      ref={dialogRef}
      style={style}
    >
      <header>
        {title}
        <form method="dialog">
          <Button
            onClick={onClose}
            style={{ minWidth: 33 }}
            type="submit"
            variant="icon"
          >
            X
          </Button>
        </form>
      </header>
      {children}
    </dialog>
  );
}

Dialog.propTypes = {
  className: PropTypes.string,
  children: PropTypes.node.isRequired,
  onClose: PropTypes.func,
  size: PropTypes.oneOf(["sm", "md", "lg"]),
  style: PropTypes.object,
  title: PropTypes.string,
};
