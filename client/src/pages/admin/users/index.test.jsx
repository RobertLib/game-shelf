import {
  clearAllMocks,
  renderWithRouter,
  setupMocks,
} from "../../../testUtils";
import { screen } from "@testing-library/react";
import Users from "./index";

describe("Users Component", () => {
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
              email: "user1@example.com",
              role: "ADMIN",
            },
            {
              id: 2,
              email: "user2@example.com",
              role: "USER",
            },
          ],
          total: 2,
        }),
      })
    );

    renderWithRouter(<Users />, { route: "/admin/users?page=1&limit=20" });

    expect(screen.getByText("Home")).toBeInTheDocument();

    const usersElements = screen.getAllByText("Users");
    expect(usersElements[0]).toBeInTheDocument();
    expect(usersElements[1]).toBeInTheDocument();
    expect(await screen.findByText("user1@example.com")).toBeInTheDocument();
    expect(screen.getByText("ADMIN")).toBeInTheDocument();
    expect(screen.getByText("user2@example.com")).toBeInTheDocument();
    expect(screen.getByText("USER")).toBeInTheDocument();
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

    renderWithRouter(<Users />, { route: "/admin/users?page=1&limit=20" });

    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  it("should handle fetch error", async () => {
    global.fetch = vi.fn().mockRejectedValueOnce(new Error("API fetch failed"));

    renderWithRouter(<Users />, { route: "/admin/users?page=1&limit=20" });

    expect(await screen.findByText("No data available")).toBeInTheDocument();
  });
});
