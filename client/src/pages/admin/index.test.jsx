import {
  clearAllMocks,
  navigateMock,
  renderWithRouter,
  setupMocks,
} from "../../testUtils";
import Admin from "./index";

describe("Admin Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("should navigate to /admin/games on mount", () => {
    renderWithRouter(<Admin />, { route: "/admin" });

    expect(navigateMock).toHaveBeenCalledWith("/admin/games");
  });
});
