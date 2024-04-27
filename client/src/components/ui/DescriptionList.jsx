import { Fragment } from "react";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function DescriptionList({ className, items, style }) {
  return (
    <dl className={cn("description-list", className)} style={style}>
      {items.map((item, index) => (
        <Fragment key={index}>
          <dt>{item.term}:</dt>
          <dd>{item.description}</dd>
        </Fragment>
      ))}
    </dl>
  );
}

DescriptionList.propTypes = {
  className: PropTypes.string,
  items: PropTypes.arrayOf(
    PropTypes.shape({
      term: PropTypes.string.isRequired,
      description: PropTypes.node,
    })
  ).isRequired,
  style: PropTypes.object,
};
