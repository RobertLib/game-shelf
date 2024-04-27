import { clearAllMocks, renderWithRouter, setupMocks } from "../../testUtils";
import { screen } from "@testing-library/react";
import AdminLayout from "./Layout";

vi.mock("./index", () => ({ default: () => <div>Admin Page Content</div> }));
vi.mock("./games", () => ({ default: () => <div>Games Page Content</div> }));
vi.mock("./users", () => ({ default: () => <div>Users Page Content</div> }));
vi.mock("./NoPage", () => ({ default: () => <div>404 - Page Not Found</div> }));

describe("AdminLayout Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("renders drawer with navigation links", () => {
    renderWithRouter(<AdminLayout />, { route: "/" });

    expect(screen.getByText("Users")).toBeInTheDocument();
    expect(screen.getByText("Games")).toBeInTheDocument();
  });

  it("navigates to Users page", () => {
    renderWithRouter(<AdminLayout />, { route: "/users" });

    expect(screen.getByText("Users Page Content")).toBeInTheDocument();
  });

  it("navigates to Games page", () => {
    renderWithRouter(<AdminLayout />, { route: "/games" });

    expect(screen.getByText("Games Page Content")).toBeInTheDocument();
  });

  it("renders NoPage for invalid route", () => {
    renderWithRouter(<AdminLayout />, { route: "/invalid" });

    expect(screen.getByText("404 - Page Not Found")).toBeInTheDocument();
  });
});
