import {
  clearAllMocks,
  renderWithRouter,
  setupMocks,
} from "../../../testUtils";
import { fireEvent, screen } from "@testing-library/react";
import NewGame from "./index";

describe("NewGame Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("renders NewGame component", () => {
    renderWithRouter(<NewGame />, { route: "/games/new" });

    expect(
      screen.getByRole("heading", { name: "Add Game" })
    ).toBeInTheDocument();
    expect(screen.getByText("< Back to Home")).toBeInTheDocument();
    expect(screen.getAllByRole("textbox")[0]).toBeInTheDocument();
  });

  it("navigates back to home when < Back to Home link is clicked", () => {
    renderWithRouter(<NewGame />, { route: "/games/new" });

    fireEvent.click(screen.getByText("< Back to Home"));

    expect(window.location.pathname).toBe("/");
  });
});
