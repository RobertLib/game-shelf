import { clearAllMocks, renderWithRouter, setupMocks } from "../testUtils";
import { screen } from "@testing-library/react";
import GameList from "./games/GameList";
import Home from "./Home";

vi.mock("./games/GameList");

describe("Home Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("renders Home component correctly", () => {
    GameList.mockImplementation(() => <div>Mocked GameList</div>);

    renderWithRouter(<Home />, { route: "/" });

    expect(screen.getByText("Welcome to Game Database")).toBeInTheDocument();
    expect(screen.getByText("Mocked GameList")).toBeInTheDocument();
  });
});
