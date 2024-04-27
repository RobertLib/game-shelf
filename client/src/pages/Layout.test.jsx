import { clearAllMocks, renderWithRouter, setupMocks } from "../testUtils";
import { screen } from "@testing-library/react";
import Layout from "./Layout";

vi.mock("./Navbar", () => ({ default: () => <div>Navbar Mock</div> }));

describe("Layout Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("renders Navbar component", () => {
    renderWithRouter(<Layout />, { route: "/" });

    expect(screen.getByText("Navbar Mock")).toBeInTheDocument();
  });
});
