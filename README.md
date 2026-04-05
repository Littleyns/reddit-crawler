# Reddit Crawler Frontend

Internal control surface for ArabTooling's Reddit crawler. The frontend is built with Next.js, React Query, React Hook Form, Zod, and a local design-system layer tailored to operations tooling.

## Development

```bash
npm install
npm run dev
```

Core quality checks:

```bash
npm run lint
npm run test
npm run build
```

## Routes

- `/dashboard` operational overview with live status cards
- `/controls` crawler launch and runtime control panel
- `/data` searchable export surface for posts and comments
- `/settings` credentials, defaults, and operator management
- `/design-system` component showcase and prop documentation

## Design System

The component library is implemented directly in `src/components/ui` and used by the app screens.

- `Button` supports `primary`, `secondary`, `ghost`, and `danger` variants
- `Card` supports `default`, `elevated`, `outline`, and `spotlight` layouts
- `Input`, `Textarea`, `Select`, and `CheckboxField` expose shared validation shells
- `Dialog`, `Popover`, and `Tooltip` provide reusable overlay primitives
- `DataTable` provides typed columns, client-side sorting, local filtering, and pagination controls
- `component-docs.ts` stores prop metadata rendered on the showcase page

Design tokens live in `src/app/globals.css` as CSS variables prefixed with `--ds-` for colors, surfaces, spacing, radii, and shadows.

Additional documentation: `docs/component-library.md`
