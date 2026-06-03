import { fireEvent, screen } from "@testing-library/react";
import { Button } from "@/components/ui/button";
import { renderWithProviders } from "@/test/render";

describe("Button", () => {
  it("renders the label and fires click handlers", () => {
    const onClick = vi.fn();
    renderWithProviders(<Button onClick={onClick}>Launch</Button>);

    fireEvent.click(screen.getByRole("button", { name: "Launch" }));

    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it("disables interactions while loading", () => {
    const onClick = vi.fn();
    renderWithProviders(
      <Button loading onClick={onClick}>
        Launch
      </Button>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Launch" }));

    expect(onClick).not.toHaveBeenCalled();
  });
});
