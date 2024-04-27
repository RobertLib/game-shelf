import "./Timeline.css";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Timeline({ className, children, style }) {
  return (
    <ul className={cn("Timeline", className)} style={style}>
      {children}
    </ul>
  );
}

Timeline.propTypes = {
  className: PropTypes.string,
  children: PropTypes.node,
  style: PropTypes.object,
};

export const TimelineItem = ({ children, label, time }) => {
  return (
    <li className="Timeline-item">
      {time && <div className="Timeline-time">{time}</div>}
      <div className="Timeline-content">
        {label && <div className="Timeline-label">{label}</div>}
        <div className="Timeline-details">{children}</div>
      </div>
    </li>
  );
};

TimelineItem.propTypes = {
  children: PropTypes.node,
  label: PropTypes.string,
  time: PropTypes.string,
};
