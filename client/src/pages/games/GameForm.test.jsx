import { clearAllMocks, renderWithRouter, setupMocks } from "../../testUtils";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import GameForm from "./GameForm";

describe("GameForm Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("renders form fields correctly", () => {
    renderWithRouter(<GameForm />);

    expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/genre/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/release date/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/developer/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/publisher/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/platform/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/rating/i)).toBeInTheDocument();
  });

  it("submits form data correctly", async () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve({}),
      })
    );

    renderWithRouter(<GameForm />);

    fireEvent.change(screen.getByLabelText(/name/i), {
      target: { value: "Test Game" },
    });
    fireEvent.change(screen.getByLabelText(/genre/i), {
      target: { value: "Adventure" },
    });
    fireEvent.change(screen.getByLabelText(/release date/i), {
      target: { value: "2023-05-19" },
    });
    fireEvent.change(screen.getByLabelText(/developer/i), {
      target: { value: "Test Developer" },
    });
    fireEvent.change(screen.getByLabelText(/publisher/i), {
      target: { value: "Test Publisher" },
    });
    fireEvent.change(screen.getByLabelText(/platform/i), {
      target: { value: "PC" },
    });
    fireEvent.change(screen.getByLabelText(/rating/i), {
      target: { value: "4.5" },
    });

    fireEvent.click(screen.getByText(/add game/i));

    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));

    expect(global.fetch).toHaveBeenCalledWith(
      "/api/games",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          name: "Test Game",
          note: "",
          genre: "Adventure",
          releaseDate: "2023-05-19",
          developer: "Test Developer",
          publisher: "Test Publisher",
          platform: "PC",
          rating: "4.5",
        }),
      })
    );
  });
});
