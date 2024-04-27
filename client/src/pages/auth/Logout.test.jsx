import {
  clearAllMocks,
  localStorageMock,
  navigateMock,
  reloadMock,
  renderWithRouter,
  sessionStorageMock,
  setupMocks,
} from "../../testUtils";
import Logout from "./Logout";

describe("Logout Component", () => {
  beforeEach(() => {
    setupMocks();
  });

  afterEach(() => {
    clearAllMocks();
  });

  it("clears localStorage, sessionStorage, and navigates to /auth/login", () => {
    renderWithRouter(<Logout />, { route: "/auth/logout" });

    expect(localStorageMock.removeItem).toHaveBeenCalledWith("token");
    expect(sessionStorageMock.clear).toHaveBeenCalled();
    expect(navigateMock).toHaveBeenCalledWith("/auth/login");
    expect(reloadMock).toHaveBeenCalled();
  });
});
