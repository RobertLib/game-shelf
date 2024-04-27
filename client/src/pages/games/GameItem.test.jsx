import {
  clearAllMocks,
  renderWithRouter,
  setMockUser,
  setupMocks,
} from "../../testUtils";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import GameItem from "./GameItem";

const mockGame = {
  id: 1,
  createdAt: new Date("2021-01-01"),
  updatedAt: new Date("2021-01-01"),
  name: "Test Game",
  genre: "Adventure",
  releaseDate: new Date("2022-01-01"),
  developer: "Test Developer",
  publisher: "Test Publisher",
  platform: "PC",
  rating: 5,
  ownerId: 1,
};

describe("GameItem Component", () => {
  beforeEach(() => {
    setupMocks();
    setMockUser({ id: 1, role: "ADMIN" });
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("renders game details correctly", () => {
    renderWithRouter(
      <GameItem game={mockGame} isNew={false} onDelete={vi.fn()} />
    );

    expect(screen.getByText(/Test Game - Adventure/i)).toBeInTheDocument();
    expect(screen.getByText("01. 01. 2022")).toBeInTheDocument();
    expect(screen.getByText(/Test Developer/i)).toBeInTheDocument();
    expect(screen.getByText(/Test Publisher/i)).toBeInTheDocument();
    expect(screen.getByText(/PC/i)).toBeInTheDocument();
    expect(screen.getByText(/(5)/i)).toBeInTheDocument();
  });

  it("calls onDelete after deletion", async () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({}),
      })
    );

    const onDeleteMock = vi.fn();

    renderWithRouter(
      <GameItem game={mockGame} isNew={false} onDelete={onDeleteMock} />
    );

    fireEvent.click(screen.getByText("Delete"));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(`/api/games/1`, {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${localStorage.getItem("token")}`,
          "Content-Type": "application/json",
        },
      });
    });

    expect(
      await screen.findByText("Game deleted successfully.")
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(onDeleteMock).toHaveBeenCalled();
    });
  });

  it("handles deletion errors gracefully", async () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        status: 500,
        json: () => Promise.reject(new Error("Failed to delete")),
      })
    );

    renderWithRouter(
      <GameItem game={mockGame} isNew={false} onDelete={vi.fn()} />
    );

    fireEvent.click(screen.getByText("Delete"));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(`/api/games/1`, {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${localStorage.getItem("token")}`,
          "Content-Type": "application/json",
        },
      });
    });

    expect(
      screen.queryByText("Game deleted successfully.")
    ).not.toBeInTheDocument();
  });

  it("calls onDelete after the set timeout", async () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({}),
      })
    );

    const onDeleteMock = vi.fn();

    renderWithRouter(
      <GameItem game={mockGame} isNew={false} onDelete={onDeleteMock} />
    );

    fireEvent.click(screen.getByText("Delete"));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(`/api/games/1`, {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${localStorage.getItem("token")}`,
          "Content-Type": "application/json",
        },
      });
    });

    expect(
      await screen.findByText("Game deleted successfully.")
    ).toBeInTheDocument();

    vi.runAllTimers();

    expect(onDeleteMock).toHaveBeenCalled();
  });

  it("confirms before deleting", () => {
    window.confirm = vi.fn().mockReturnValue(false);

    const onDeleteMock = vi.fn();

    renderWithRouter(
      <GameItem game={mockGame} isNew={false} onDelete={onDeleteMock} />
    );

    fireEvent.click(screen.getByText("Delete"));

    expect(global.fetch).not.toHaveBeenCalled();
    expect(
      screen.queryByText("Game deleted successfully.")
    ).not.toBeInTheDocument();
    expect(onDeleteMock).not.toHaveBeenCalled();
  });
});
