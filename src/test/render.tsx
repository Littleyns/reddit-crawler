import { render } from "@testing-library/react";
import type { ReactElement } from "react";
import { Providers } from "@/components/providers";

export function renderWithProviders(ui: ReactElement) {
  return render(<Providers>{ui}</Providers>);
}
