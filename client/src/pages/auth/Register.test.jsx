import {
  clearAllMocks,
  localStorageMock,
  mockSetCurrentUser,
  renderWithRouter,
  setupMocks,
} from "../../testUtils";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import Register from "./Register";

describe("Register Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("renders the register form", () => {
    renderWithRouter(<Register />, { route: "/auth/register" });

    expect(screen.getByLabelText(/Email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Confirm Password/i)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Register/i })
    ).toBeInTheDocument();
  });

  it("handles successful registration", async () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        json: async () => ({ token: "fakeToken" }),
      })
    );

    renderWithRouter(<Register />, { route: "/auth/register" });

    fireEvent.change(screen.getByLabelText(/Email/i), {
      target: { value: "test@example.com" },
    });
    fireEvent.change(screen.getByLabelText(/^Password/i), {
      target: { value: "password123" },
    });
    fireEvent.change(screen.getByLabelText(/Confirm Password/i), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Register/i }));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        "/api/auth/register",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({
            email: "test@example.com",
            password: "password123",
            passwordConfirm: "password123",
          }),
        })
      );
    });

    expect(localStorageMock.setItem).toHaveBeenCalledWith("token", "fakeToken");
    expect(mockSetCurrentUser).toHaveBeenCalledWith({ user: "decodedUser" });
  });

  it("handles registration errors", async () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: false,
        json: async () => ({ errorMessage: "Registration failed" }),
      })
    );

    renderWithRouter(<Register />, { route: "/auth/register" });

    fireEvent.change(screen.getByLabelText(/Email/i), {
      target: { value: "test@example.com" },
    });
    fireEvent.change(screen.getByLabelText(/^Password/i), {
      target: { value: "password123" },
    });
    fireEvent.change(screen.getByLabelText(/Confirm Password/i), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Register/i }));

    expect(await screen.findByText(/Registration failed/i)).toBeInTheDocument();
    expect(localStorageMock.setItem).not.toHaveBeenCalled();
    expect(mockSetCurrentUser).not.toHaveBeenCalled();
  });

  it("displays validation errors", async () => {
    global.fetch = vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: false,
        json: async () => ({
          email: "Invalid email",
          password: "Password too weak",
          passwordConfirm: "Passwords do not match",
        }),
      })
    );

    renderWithRouter(<Register />, { route: "/auth/register" });

    fireEvent.change(screen.getByLabelText(/Email/i), {
      target: { value: "invalid@email" },
    });
    fireEvent.change(screen.getByLabelText(/^Password/i), {
      target: { value: "123" },
    });
    fireEvent.change(screen.getByLabelText(/Confirm Password/i), {
      target: { value: "1234" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Register/i }));

    expect(await screen.findByText(/Invalid email/i)).toBeInTheDocument();
    expect(screen.getByText(/Password too weak/i)).toBeInTheDocument();
    expect(screen.getByText(/Passwords do not match/i)).toBeInTheDocument();
  });
});
