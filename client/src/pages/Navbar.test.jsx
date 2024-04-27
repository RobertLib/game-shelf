import { clearAllMocks, renderWithRouter, setupMocks } from "../testUtils";
import { fireEvent, screen } from "@testing-library/react";
import { useSession } from "../contexts/session";
import Navbar from "./Navbar";

describe("Navbar Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("renders GameShelf link and does not show dropdown when no user is logged in", () => {
    useSession.mockReturnValue({
      currentUser: null,
    });

    renderWithRouter(<Navbar />);

    expect(screen.getByText("GameShelf")).toBeInTheDocument();
    expect(screen.queryByText("Logout")).not.toBeInTheDocument();
  });

  it("shows dropdown with admin link and logout when admin user is logged in", () => {
    useSession.mockReturnValue({
      currentUser: { email: "admin@example.com", role: "ADMIN" },
    });

    renderWithRouter(<Navbar />);

    expect(screen.getByText("admin@example.com")).toBeInTheDocument();
    fireEvent.click(screen.getByText("admin@example.com"));

    expect(screen.getByText("Admin")).toBeInTheDocument();
    expect(screen.getByText("Logout")).toBeInTheDocument();
  });

  it("shows only logout in dropdown when non-admin user is logged in", () => {
    useSession.mockReturnValue({
      currentUser: { email: "user@example.com", role: "USER" },
    });

    renderWithRouter(<Navbar />);

    expect(screen.getByText("user@example.com")).toBeInTheDocument();
    fireEvent.click(screen.getByText("user@example.com"));

    expect(screen.queryByText("Admin")).not.toBeInTheDocument();
    expect(screen.getByText("Logout")).toBeInTheDocument();
  });

  it("renders admin menu button when on admin route", () => {
    useSession.mockReturnValue({
      currentUser: { email: "admin@example.com", role: "ADMIN" },
    });

    renderWithRouter(<Navbar />, { route: "/admin/dashboard" });

    expect(screen.getByAltText("menu")).toBeInTheDocument();
  });

  it("does not render admin menu button when not on admin route", () => {
    useSession.mockReturnValue({
      currentUser: { email: "admin@example.com", role: "ADMIN" },
    });

    renderWithRouter(<Navbar />);

    expect(screen.queryByAltText("menu")).not.toBeInTheDocument();
  });
});
