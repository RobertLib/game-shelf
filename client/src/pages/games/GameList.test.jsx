import { clearAllMocks, renderWithRouter, setupMocks } from "../../testUtils";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import GameList from "./GameList";

const mockGames = [
  { id: 1, name: "Game 1", genre: "Genre 1" },
  { id: 2, name: "Game 2", genre: "Genre 2" },
];

describe("GameList Component", () => {
  beforeEach(() => {
    setupMocks();

    vi.spyOn(global, "fetch").mockImplementation((url) => {
      if (typeof url === "string" && url.includes("/api/games")) {
        return Promise.resolve({
          ...new Response(),
          json: () =>
            Promise.resolve({ data: mockGames, total: mockGames.length }),
        });
      }
      return Promise.reject(new Error("not found"));
    });
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("renders the game list with loading spinner initially", () => {
    renderWithRouter(<GameList />);

    expect(screen.getByText(/Games List/i)).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "+ Add Game" })
    ).toBeInTheDocument();
    expect(screen.getByText(/Filters/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Search/i)).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Name" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Genre" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Asc" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Desc" })).toBeInTheDocument();
  });

  it("renders game items after loading", async () => {
    renderWithRouter(<GameList />);

    expect(await screen.findByText(/Game 1/i)).toBeInTheDocument();
    expect(screen.getByText(/Game 2/i)).toBeInTheDocument();
  });

  it("updates search params on input change", async () => {
    renderWithRouter(<GameList />);

    const searchInput = screen.getByPlaceholderText(/Search/i);
    fireEvent.change(searchInput, { target: { value: "new search term" } });

    await waitFor(() => {
      expect(searchInput.value).toBe("new search term");
    });
  });

  it("updates sort key on select change", async () => {
    renderWithRouter(<GameList />);

    const sortSelect = screen.getAllByRole("combobox", { name: "" })[0];
    fireEvent.change(sortSelect, { target: { value: "name" } });

    await waitFor(() => {
      expect(sortSelect.value).toBe("name");
    });
  });
});
