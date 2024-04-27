import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Accordion({ className, children, style, summary }) {
  return (
    <details className={cn("details", className)} open style={style}>
      <summary>{summary}</summary>
      <div className="details-content">{children}</div>
    </details>
  );
}

Accordion.propTypes = {
  className: PropTypes.string,
  children: PropTypes.node.isRequired,
  style: PropTypes.object,
  summary: PropTypes.node.isRequired,
};
