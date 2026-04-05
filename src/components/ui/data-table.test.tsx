import { fireEvent, screen } from "@testing-library/react";
import { DataTable } from "@/components/ui/data-table";
import { renderWithProviders } from "@/test/render";

describe("DataTable", () => {
  const rows = [
    { id: "1", name: "Zulu", score: 4 },
    { id: "2", name: "Alpha", score: 9 },
  ];

  it("sorts rows when a sortable header is clicked", () => {
    renderWithProviders(
      <DataTable
        columns={[
          { key: "name", label: "Name" },
          { key: "score", label: "Score" },
        ]}
        rows={rows}
        page={1}
        totalPages={1}
        onPageChange={() => undefined}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /name/i }));

    const cells = screen.getAllByRole("cell");
    expect(cells[0]).toHaveTextContent("Zulu");

    fireEvent.click(screen.getByRole("button", { name: /name/i }));
    expect(screen.getAllByRole("cell")[0]).toHaveTextContent("Alpha");
  });
});
