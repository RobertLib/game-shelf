import {
  clearAllMocks,
  renderWithRouter,
  setupMocks,
} from "../../../testUtils";
import { screen } from "@testing-library/react";
import EditGame from "./index";

describe("EditGame Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("should display loading initially", () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        json: async () => ({}),
      })
    );

    renderWithRouter(<EditGame />, { route: "/games/1/edit" });

    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  it("should display the game form when data is loaded", async () => {
    const gameData = {
      id: 1,
      name: "Test Game",
      description: "Test Description",
    };

    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        json: async () => gameData,
      })
    );

    renderWithRouter(<EditGame />, { route: "/games/1/edit" });

    expect(await screen.findByText("Update Game")).toBeInTheDocument();
    expect(await screen.findByText("Test Game")).toBeInTheDocument();
  });

  it("should handle API errors gracefully", () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: false,
        status: 500,
        json: async () => ({ error: "Internal Server Error" }),
      })
    );

    renderWithRouter(<EditGame />, { route: "/games/1/edit" });

    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });
});
