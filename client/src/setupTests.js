import "@testing-library/jest-dom";
import { afterEach, expect } from "vitest";
import { cleanup } from "@testing-library/react";
import * as matchers from "@testing-library/jest-dom/matchers";

expect.extend(matchers);

afterEach(() => {
  cleanup();
});

const originalError = console.error;

console.error = (...args) => {
  if (
    typeof args[0] === "string" &&
    args[0].startsWith(
      "Warning: An update to %s inside a test was not wrapped in act(...)."
    )
  ) {
    return;
  }

  originalError(...args);
};
