import { render, screen } from "@testing-library/react";
import NoPage from "./NoPage";

describe("NoPage Component", () => {
  it("renders the 404 message", () => {
    render(<NoPage />);

    expect(screen.getByText("404 - Page Not Found")).toBeInTheDocument();
  });
});
