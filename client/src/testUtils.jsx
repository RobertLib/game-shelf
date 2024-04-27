import { DrawerProvider } from "./contexts/drawer";
import { MemoryRouter as Router, useNavigate } from "react-router-dom";
import { render } from "@testing-library/react";
import { SnackbarProvider } from "./contexts/snackbar";
import { useSession } from "./contexts/session";

vi.useFakeTimers({ shouldAdvanceTime: true });

vi.mock("jwt-decode", () => ({
  jwtDecode: () => ({ user: "decodedUser" }),
}));

vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal()),
  useNavigate: vi.fn(),
}));

vi.mock("./contexts/session", () => ({
  useSession: vi.fn(),
}));

vi.mock("./utils/logger", async (importOriginal) => ({
  ...(await importOriginal()),
  info: vi.fn(),
  error: vi.fn(),
}));

const localStorageMock = (() => {
  let store = {};

  return {
    getItem: vi.fn((key) => store[key] || null),
    setItem: vi.fn((key, value) => {
      store[key] = value;
    }),
    clear: vi.fn(() => {
      store = {};
    }),
    removeItem: vi.fn((key) => {
      delete store[key];
    }),
  };
})();

Object.defineProperty(window, "localStorage", {
  value: localStorageMock,
});

const sessionStorageMock = (() => {
  let store = {};

  return {
    getItem: vi.fn((key) => store[key] || null),
    setItem: vi.fn((key, value) => {
      store[key] = value;
    }),
    clear: vi.fn(() => {
      store = {};
    }),
    removeItem: vi.fn((key) => {
      delete store[key];
    }),
  };
})();

Object.defineProperty(window, "sessionStorage", {
  value: sessionStorageMock,
});

const reloadMock = vi.fn();
Object.defineProperty(window, "location", {
  value: {
    ...window.location,
    reload: reloadMock,
  },
  writable: true,
});

const navigateMock = vi.fn();
const mockSetCurrentUser = vi.fn();

const setupMocks = () => {
  window.confirm = vi.fn().mockReturnValue(true);

  useNavigate.mockReturnValue(navigateMock);

  useSession.mockReturnValue({
    setCurrentUser: mockSetCurrentUser,
  });
};

const setMockUser = (user) => {
  useSession.mockReturnValue({
    currentUser: user,
    setCurrentUser: mockSetCurrentUser,
  });
};

const wrapper = ({ children, route }) => (
  <SnackbarProvider>
    <DrawerProvider>
      <Router initialEntries={[route]}>{children}</Router>
    </DrawerProvider>
  </SnackbarProvider>
);

const renderWithRouter = (ui, { route = "/" } = {}) => {
  return render(ui, {
    wrapper: ({ children }) => wrapper({ children, route }),
  });
};

const clearAllMocks = () => {
  localStorageMock.clear();
  sessionStorageMock.clear();
  reloadMock.mockClear();
  navigateMock.mockClear();
  vi.clearAllMocks();
  vi.resetAllMocks();
  vi.restoreAllMocks();
};

export {
  clearAllMocks,
  localStorageMock,
  mockSetCurrentUser,
  navigateMock,
  reloadMock,
  renderWithRouter,
  sessionStorageMock,
  setMockUser,
  setupMocks,
};
