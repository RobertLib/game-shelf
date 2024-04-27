import {
  clearAllMocks,
  renderWithRouter,
  setupMocks,
} from "../../../testUtils";
import { screen } from "@testing-library/react";
import Games from "./index";

describe("Games Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("should render the Breadcrumb and DataTable components", async () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        json: async () => ({
          data: [
            {
              id: 1,
              name: "Game 1",
              genre: "Action",
              releaseDate: "2021-01-01",
              developer: "Dev 1",
              publisher: "Pub 1",
              platform: "PC",
              rating: 5,
            },
          ],
          total: 1,
        }),
      })
    );

    renderWithRouter(<Games />, { route: "/admin/games?page=1&limit=20" });

    expect(screen.getByText("Home")).toBeInTheDocument();

    const gamesElements = screen.getAllByText("Games");
    expect(gamesElements[0]).toBeInTheDocument();
    expect(gamesElements[1]).toBeInTheDocument();
    expect(await screen.findByText("Game 1")).toBeInTheDocument();
    expect(screen.getByText("Action")).toBeInTheDocument();
    expect(screen.getByText(/01. 01. 2021/i)).toBeInTheDocument();
    expect(screen.getByText("Dev 1")).toBeInTheDocument();
    expect(screen.getByText("Pub 1")).toBeInTheDocument();
    expect(screen.getByText("PC")).toBeInTheDocument();
    expect(screen.getByText("5/5")).toBeInTheDocument();
  });

  it("should display loading state", () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Promise((resolve) =>
        setTimeout(
          () =>
            resolve({
              ok: true,
              json: async () => ({
                data: [],
                total: 0,
              }),
            }),
          100
        )
      )
    );

    renderWithRouter(<Games />, { route: "/admin/games?page=1&limit=20" });

    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  it("should handle fetch error", async () => {
    global.fetch = vi.fn().mockRejectedValueOnce(new Error("API fetch failed"));

    renderWithRouter(<Games />, { route: "/admin/games?page=1&limit=20" });

    expect(await screen.findByText("No data available")).toBeInTheDocument();
  });
});
