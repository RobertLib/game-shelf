import PropTypes from "prop-types";

export default function Rating({ className, style, value = 0 }) {
  const roundedRating = Math.round(value * 2) / 2;

  const stars = [];

  for (let i = 1; i <= 5; i++) {
    if (i <= roundedRating) {
      stars.push(
        <span key={i} style={{ color: "gold" }}>
          ★
        </span>
      );
    } else if (i - 0.5 === roundedRating) {
      stars.push(
        <span key={i} style={{ color: "gold" }}>
          ★
        </span>
      );
    } else {
      stars.push(
        <span key={i} style={{ color: "lightgray" }}>
          ★
        </span>
      );
    }
  }

  return (
    <div className={className} style={style}>
      {stars} {roundedRating}/5
    </div>
  );
}

Rating.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.number,
};
