import "./snackbar.css";
import { createContext, useCallback, useContext, useState } from "react";
import { Toast } from "../components/ui";
import PropTypes from "prop-types";

const SnackbarContext = createContext({
  enqueueSnackbar: () => {},
});

export const SnackbarProvider = ({ children }) => {
  const [toasts, setToasts] = useState([]);

  const enqueueSnackbar = useCallback((message, variant) => {
    const id = Math.random().toString();

    setToasts((prevState) => [...prevState, { id, message, variant }]);

    setTimeout(() => {
      setToasts((prevState) => prevState.filter((toast) => toast.id !== id));
    }, 3000);
  }, []);

  return (
    <SnackbarContext.Provider value={{ enqueueSnackbar }}>
      <div className="snackbar-container">
        {toasts.map((toast) => (
          <Toast
            key={toast.id}
            message={toast.message}
            variant={toast.variant}
          />
        ))}
      </div>

      {children}
    </SnackbarContext.Provider>
  );
};

SnackbarProvider.propTypes = {
  children: PropTypes.node,
};

export const useSnackbar = () => useContext(SnackbarContext);
