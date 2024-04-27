import {
  clearAllMocks,
  navigateMock,
  renderWithRouter,
  setupMocks,
} from "../../../../testUtils";
import { screen } from "@testing-library/react";
import GameDetail from "./index";

describe("GameDetail Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("should display a loading spinner while fetching data", () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        json: async () => null,
      })
    );

    renderWithRouter(<GameDetail />, { route: "/admin/games/1" });

    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("should display game details when data is fetched", async () => {
    const gameData = {
      data: {
        name: "Test Game",
        genre: "Adventure",
        releaseDate: "2023-01-01",
        developer: "Test Developer",
        publisher: "Test Publisher",
        platform: "PC",
        rating: 4.5,
      },
    };

    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        json: async () => gameData,
      })
    );

    renderWithRouter(<GameDetail />, { route: "/admin/games/1" });

    expect(await screen.findAllByText("Test Game")).toHaveLength(2);
    expect(screen.getAllByText("Adventure")).toHaveLength(1);
    expect(screen.getAllByText("Test Developer")).toHaveLength(1);
    expect(screen.getAllByText("Test Publisher")).toHaveLength(1);
    expect(screen.getAllByText("PC")).toHaveLength(1);
    expect(screen.getAllByText("4.5")).toHaveLength(1);
    expect(screen.getAllByText(/^01. 01. 2023/i)).toHaveLength(1);
  });

  it("should navigate back to the games list when the back button is clicked", async () => {
    const gameData = {
      data: {
        name: "Test Game",
        genre: "Adventure",
        releaseDate: "2023-01-01",
        developer: "Test Developer",
        publisher: "Test Publisher",
        platform: "PC",
        rating: 5,
      },
    };

    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        json: async () => gameData,
      })
    );

    renderWithRouter(<GameDetail />, { route: "/admin/games/1" });

    const backButton = await screen.findByRole("button", {
      name: /Back to Games/i,
    });
    backButton.click();

    expect(navigateMock).toHaveBeenCalledWith(-1);
  });
});
