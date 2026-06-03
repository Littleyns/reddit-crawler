# Component Library

## Overview

The frontend now uses a shared component layer in `src/components/ui` instead of page-local one-off markup. The library is tuned for internal tooling: high contrast surfaces, large hit targets, explicit validation feedback, and strong information density without losing hierarchy.

## Design Tokens

All core tokens are defined in `src/app/globals.css` as CSS variables with a `--ds-` prefix.

- Color tokens: page, surface, text, border, state colors
- Radius tokens: `--radius-xs` through `--radius-xl`
- Spacing tokens: `--space-1` through `--space-8`
- Shadow tokens: panel, focus, floating overlays

These variables are also mapped into Tailwind theme aliases for consistent usage in utility classes.

## Components

### Button

- Variants: `primary`, `secondary`, `ghost`, `danger`
- Sizes: `sm`, `md`, `lg`
- Supports `leadingIcon`, `trailingIcon`, and `loading`

### Card

- Variants: `default`, `elevated`, `outline`, `spotlight`
- Companion building blocks: `CardHeader`, `CardContent`, `CardFooter`

### Form Primitives

- `Input`
- `Textarea`
- `Select`
- `CheckboxField`
- `FormSection`
- `FormGrid`
- `FormActions`

Validation states are standardized as `default`, `success`, and `error`. Error and success messaging automatically adjust the field shell styling.

### Overlays

- `Dialog` for modal flows
- `Popover` for compact floating content
- `Tooltip` for contextual hints

### DataTable

- Generic column typing via `DataTableColumn<T>`
- Client-side sort toggling per column
- Optional local search input
- Optional filter slot
- Empty state and pagination footer

## Documentation Surface

The `/design-system` route demonstrates:

- Visual tokens
- Button and card variants
- Form validation states
- Dialog, popover, and tooltip behavior
- Data table usage
- Props reference tables generated from `src/components/ui/component-docs.ts`

## Tests

Test helpers live in `src/test/render.tsx`.

Current component tests cover:

- Button click and loading behavior
- Input validation messaging
- DataTable sorting

Run them with:

```bash
npm run test
```
