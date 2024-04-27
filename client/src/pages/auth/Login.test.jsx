import {
  clearAllMocks,
  mockSetCurrentUser,
  renderWithRouter,
  setupMocks,
} from "../../testUtils";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import Login from "./Login";

describe("Login Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("renders login form", () => {
    renderWithRouter(<Login />, { route: "/auth/login" });

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /login/i })).toBeInTheDocument();
  });

  it("handles input changes", () => {
    renderWithRouter(<Login />, { route: "/auth/login" });

    fireEvent.change(screen.getByLabelText(/email/i), {
      target: { value: "test@example.com" },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: "password123" },
    });

    expect(screen.getByLabelText(/email/i).value).toBe("test@example.com");
    expect(screen.getByLabelText(/password/i).value).toBe("password123");
  });

  it("submits the form and handles successful login", async () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ token: "mockToken" }),
      })
    );

    renderWithRouter(<Login />, { route: "/auth/login" });

    fireEvent.change(screen.getByLabelText(/email/i), {
      target: { value: "test@example.com" },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /login/i }));

    await waitFor(() => {
      expect(localStorage.setItem).toHaveBeenCalledWith("token", "mockToken");
    });

    expect(mockSetCurrentUser).toHaveBeenCalledWith({ user: "decodedUser" });
  });

  it("displays error message on failed login", async () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: false,
        json: () => Promise.resolve({ errorMessage: "Invalid credentials" }),
      })
    );

    renderWithRouter(<Login />, { route: "/auth/login" });

    fireEvent.change(screen.getByLabelText(/email/i), {
      target: { value: "test@example.com" },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /login/i }));

    expect(await screen.findByText(/invalid credentials/i)).toBeInTheDocument();
  });
});
