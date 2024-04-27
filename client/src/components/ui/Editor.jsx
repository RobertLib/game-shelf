import "./Editor.css";
import { useEffect, useRef } from "react";
import Button from "./Button";
import cn from "../../utils/cn";
import PropTypes from "prop-types";
import sanitizeHTML from "../../utils/sanitizeHTML";

export default function Editor({
  className,
  error,
  label,
  name,
  onChange,
  required,
  style,
  value,
}) {
  const editorRef = useRef(null);

  useEffect(() => {
    if (editorRef.current) {
      editorRef.current.innerHTML = sanitizeHTML(value ?? "");
    }
  }, []);

  const formatText = (commandId, showUI, value) => {
    document.execCommand(commandId, showUI, value);
  };

  const handleFocus = (event) => {
    event.preventDefault();
    editorRef.current?.focus();
  };

  const handleInput = () => {
    const sanitizedContent = sanitizeHTML(editorRef.current?.innerHTML ?? "");
    onChange?.(sanitizedContent, name);
  };

  return (
    <label className={className} onClick={handleFocus} style={style}>
      {label && (
        <span className="Editor-label">
          {label}: {required && <span className="text-danger">*</span>}
        </span>
      )}
      <div>
        <header className="Editor-toolbar">
          <Button
            onClick={() => formatText("bold")}
            size="sm"
            variant="default"
          >
            Bold
          </Button>
          <Button
            onClick={() => formatText("italic")}
            size="sm"
            variant="default"
          >
            Italic
          </Button>
          <Button
            onClick={() => formatText("underline")}
            size="sm"
            variant="default"
          >
            Underline
          </Button>
          <Button
            onClick={() => formatText("insertOrderedList")}
            size="sm"
            variant="default"
          >
            OL
          </Button>
          <Button
            onClick={() => formatText("insertUnorderedList")}
            size="sm"
            variant="default"
          >
            UL
          </Button>
        </header>
        <div
          className={cn("Editor-content", error && "is-invalid")}
          contentEditable
          onInput={handleInput}
          ref={editorRef}
        />
        {error && (
          <div className="Editor-error collapse-down text-danger text-sm">
            {error}
          </div>
        )}
      </div>
    </label>
  );
}

Editor.propTypes = {
  className: PropTypes.string,
  error: PropTypes.string,
  label: PropTypes.string,
  name: PropTypes.string,
  onChange: PropTypes.func,
  required: PropTypes.bool,
  style: PropTypes.object,
  value: PropTypes.string,
};
