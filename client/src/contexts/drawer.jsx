import { createContext, useContext, useEffect, useState } from "react";
import PropTypes from "prop-types";

const DrawerContext = createContext({
  isOpen: false,
  toggleDrawer: () => {},
});

export const DrawerProvider = ({ children }) => {
  const [isOpen, setIsOpen] = useState(window.innerWidth > 768);

  useEffect(() => {
    const handleResize = () => {
      setIsOpen(window.innerWidth > 768);
    };

    window.addEventListener("resize", handleResize);

    return () => {
      window.removeEventListener("resize", handleResize);
    };
  }, []);

  const toggleDrawer = () => {
    setIsOpen((prevState) => !prevState);
  };

  return (
    <DrawerContext.Provider value={{ isOpen, toggleDrawer }}>
      {children}
    </DrawerContext.Provider>
  );
};

DrawerProvider.propTypes = {
  children: PropTypes.node,
};

export function useDrawer() {
  return useContext(DrawerContext);
}
