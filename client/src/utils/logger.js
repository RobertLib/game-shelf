const logger = Object.keys(console).reduce((acc, key) => {
  acc[key] = (...args) => {
    if (
      typeof window === "undefined" ||
      process.env.NODE_ENV === "development"
    ) {
      console[key](...args);
    }
  };

  return acc;
}, {});

export default logger;
