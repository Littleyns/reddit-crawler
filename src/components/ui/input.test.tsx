import { screen } from "@testing-library/react";
import { Input } from "@/components/ui/input";
import { renderWithProviders } from "@/test/render";

describe("Input", () => {
  it("shows validation error messaging", () => {
    renderWithProviders(<Input label="Email" error="Email is required" />);

    expect(screen.getByText("Email is required")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
  });
});
