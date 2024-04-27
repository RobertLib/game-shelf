import "./Map.css";
import { useEffect, useRef } from "react";
import cn from "../../utils/cn";
import PropTypes from "prop-types";

export default function Map({ center, className, markers, style }) {
  const mapRef = useRef(null);

  useEffect(() => {
    if (!window.google) {
      return;
    }

    const map = new window.google.maps.Map(mapRef.current, {
      center,
      zoom: 10,
    });

    markers?.forEach((marker) => {
      new window.google.maps.Marker({
        map,
        position: marker.position,
        title: marker.title,
      });
    });
  }, [center, markers]);

  return <div className={cn("Map", className)} ref={mapRef} style={style} />;
}

Map.propTypes = {
  center: PropTypes.shape({
    lat: PropTypes.number.isRequired,
    lng: PropTypes.number.isRequired,
  }).isRequired,
  className: PropTypes.string,
  markers: PropTypes.arrayOf(
    PropTypes.shape({
      position: PropTypes.shape({
        lat: PropTypes.number.isRequired,
        lng: PropTypes.number.isRequired,
      }).isRequired,
      title: PropTypes.string.isRequired,
    })
  ),
  style: PropTypes.object,
};
