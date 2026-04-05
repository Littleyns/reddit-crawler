"use client";

import { Blocks, Filter, Info, LayoutTemplate, ShieldCheck } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { componentDocs } from "@/components/ui/component-docs";
import { DataTable, type DataTableColumn } from "@/components/ui/data-table";
import { Dialog } from "@/components/ui/dialog";
import { FormActions, FormGrid, FormSection } from "@/components/ui/form";
import { CheckboxField, Input, Select, Textarea } from "@/components/ui/input";
import { Popover, Tooltip } from "@/components/ui/popover";
import { StatusBadge } from "@/components/ui/status-badge";

interface DemoRow {
  id: string;
  component: string;
  status: "Ready" | "Preview";
  owner: string;
}

const demoRows: DemoRow[] = [
  { id: "1", component: "Button", status: "Ready", owner: "UI Core" },
  { id: "2", component: "Dialog", status: "Ready", owner: "UI Core" },
  { id: "3", component: "Table filters", status: "Preview", owner: "Data UI" },
];

const columns: readonly DataTableColumn<DemoRow>[] = [
  { key: "component", label: "Component" },
  {
    key: "status",
    label: "Status",
    render: (row) => (
      <StatusBadge tone={row.status === "Ready" ? "success" : "warning"} label={row.status} />
    ),
  },
  { key: "owner", label: "Owner" },
];

const tokenGroups = [
  { label: "Primary", value: "var(--ds-primary-500)" },
  { label: "Secondary", value: "var(--ds-secondary-500)" },
  { label: "Success", value: "var(--ds-success-500)" },
  { label: "Warning", value: "var(--ds-warning-500)" },
  { label: "Danger", value: "var(--ds-danger-500)" },
  { label: "Surface", value: "var(--ds-surface-elevated)" },
];

export default function DesignSystemPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [tableFilter, setTableFilter] = useState("");
  const filteredRows = useMemo(
    () =>
      demoRows.filter((row) =>
        `${row.component} ${row.status} ${row.owner}`.toLowerCase().includes(tableFilter.toLowerCase()),
      ),
    [tableFilter],
  );

  return (
    <div className="space-y-6">
      <Card variant="spotlight" className="rounded-[calc(var(--radius-xl)+0.35rem)]">
        <CardHeader
          title="Production component library"
          description="Typed primitives, tokenized surfaces, validation-aware form controls, and reference docs wired directly into the crawler UI."
        >
          <p className="text-xs uppercase tracking-[0.32em] text-[var(--ds-text-muted)]">Design System</p>
        </CardHeader>
        <CardFooter>
          <Button onClick={() => setDialogOpen(true)} leadingIcon={<Blocks className="h-4 w-4" />}>
            Open modal demo
          </Button>
          <Popover
            trigger={
              <span>
                <Button variant="secondary" leadingIcon={<Info className="h-4 w-4" />}>
                  Popover demo
                </Button>
              </span>
            }
            content={
              <div className="space-y-2">
                <p className="text-sm font-semibold">Reusable overlays</p>
                <p className="text-sm text-[var(--ds-text-secondary)]">
                  Dialog, tooltip, and popover primitives share the same surface, border, and shadow tokens.
                </p>
              </div>
            }
          />
          <Tooltip label="Ghost buttons are for low-emphasis actions">
            <span>
              <Button variant="ghost">Hover tooltip</Button>
            </span>
          </Tooltip>
        </CardFooter>
      </Card>

      <section className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <Card variant="outline">
          <CardHeader title="Buttons and cards" description="Core actions and layout surfaces." />
          <CardContent className="space-y-6">
            <div className="flex flex-wrap gap-3">
              <Button>Primary</Button>
              <Button variant="secondary">Secondary</Button>
              <Button variant="ghost">Ghost</Button>
              <Button variant="danger">Danger</Button>
            </div>
            <div className="grid gap-4 lg:grid-cols-3">
              <Card variant="default" className="p-4">
                <p className="text-sm font-semibold">Default</p>
                <p className="mt-2 text-sm text-[var(--ds-text-secondary)]">Base operational panel.</p>
              </Card>
              <Card variant="elevated" className="p-4">
                <p className="text-sm font-semibold">Elevated</p>
                <p className="mt-2 text-sm text-[var(--ds-text-secondary)]">Priority callout with stronger depth.</p>
              </Card>
              <Card variant="outline" className="p-4">
                <p className="text-sm font-semibold">Outline</p>
                <p className="mt-2 text-sm text-[var(--ds-text-secondary)]">Low-emphasis grouped content.</p>
              </Card>
            </div>
          </CardContent>
        </Card>

        <Card variant="outline">
          <CardHeader title="Design tokens" description="CSS variables define color, radius, space, and surface behavior." />
          <CardContent className="grid gap-3 sm:grid-cols-2">
            {tokenGroups.map((token) => (
              <div key={token.label} className="rounded-[var(--radius-md)] border border-[var(--ds-border-soft)] p-3">
                <div className="h-12 rounded-[var(--radius-sm)]" style={{ background: token.value }} />
                <p className="mt-3 text-sm font-medium">{token.label}</p>
                <p className="mt-1 text-xs text-[var(--ds-text-muted)]">{token.value}</p>
              </div>
            ))}
          </CardContent>
        </Card>
      </section>

      <section className="grid gap-6 xl:grid-cols-[1fr_1fr]">
        <Card variant="outline">
          <CardHeader title="Form primitives" description="Validation-aware input, textarea, select, and checkbox fields." />
          <CardContent>
            <form className="space-y-5">
              <FormSection title="Validation states">
                <FormGrid>
                  <Input label="Project name" defaultValue="Crawler UI refresh" successMessage="Looks good" />
                  <Input label="Owner email" defaultValue="invalid-email" error="Enter a valid owner email" />
                </FormGrid>
                <Textarea
                  label="Summary"
                  defaultValue="A cohesive component foundation for internal tooling."
                  description="Textarea uses the same shell and messaging pattern."
                />
                <div className="grid gap-4 md:grid-cols-2">
                  <Select label="Release stage" defaultValue="beta">
                    <option value="alpha">Alpha</option>
                    <option value="beta">Beta</option>
                    <option value="ga">General availability</option>
                  </Select>
                  <CheckboxField
                    label="Require accessibility review"
                    description="Used before promoting components to ready status."
                    defaultChecked
                  />
                </div>
              </FormSection>
              <FormActions>
                <Button>Save draft</Button>
                <Button variant="secondary">Publish docs</Button>
              </FormActions>
            </form>
          </CardContent>
        </Card>

        <Card variant="outline">
          <CardHeader title="Data table" description="Typed columns with sorting, search, and filter affordances." />
          <CardContent>
            <DataTable
              columns={columns}
              rows={filteredRows}
              page={1}
              totalPages={1}
              onPageChange={() => undefined}
              searchValue={tableFilter}
              onSearchChange={setTableFilter}
              filtersSlot={
                <span className="inline-flex items-center gap-2">
                  <Filter className="h-4 w-4" />
                  Demo status registry
                </span>
              }
            />
          </CardContent>
        </Card>
      </section>

      <Card variant="outline">
        <CardHeader title="Component props documentation" description="Reference data for variants, states, and supported props." />
        <CardContent className="space-y-4">
          {componentDocs.map((doc) => (
            <div key={doc.name} className="rounded-[var(--radius-lg)] border border-[var(--ds-border-soft)] p-4">
              <div className="flex items-center gap-3">
                <LayoutTemplate className="h-5 w-5 text-[var(--ds-primary-500)]" />
                <div>
                  <h3 className="text-lg font-semibold">{doc.name}</h3>
                  <p className="text-sm text-[var(--ds-text-secondary)]">{doc.summary}</p>
                </div>
              </div>
              <div className="mt-4 overflow-x-auto">
                <table className="min-w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-[var(--ds-border-soft)] text-[var(--ds-text-muted)]">
                      <th className="px-3 py-2 font-medium uppercase tracking-[0.18em]">Prop</th>
                      <th className="px-3 py-2 font-medium uppercase tracking-[0.18em]">Type</th>
                      <th className="px-3 py-2 font-medium uppercase tracking-[0.18em]">Default</th>
                      <th className="px-3 py-2 font-medium uppercase tracking-[0.18em]">Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    {doc.props.map((prop) => (
                      <tr key={prop.name} className="border-b border-[var(--ds-border-soft)] last:border-b-0">
                        <td className="px-3 py-3 font-mono text-[var(--ds-primary-500)]">{prop.name}</td>
                        <td className="px-3 py-3 text-[var(--ds-text-secondary)]">{prop.type}</td>
                        <td className="px-3 py-3 text-[var(--ds-text-secondary)]">{prop.defaultValue ?? "-"}</td>
                        <td className="px-3 py-3">{prop.description}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <Dialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        title="Modal system demo"
        description="The dialog primitive handles overlay rendering, escape-to-close behavior, and footer actions."
        footer={
          <>
            <Button variant="ghost" onClick={() => setDialogOpen(false)}>
              Cancel
            </Button>
            <Button leadingIcon={<ShieldCheck className="h-4 w-4" />} onClick={() => setDialogOpen(false)}>
              Confirm review
            </Button>
          </>
        }
      >
        <div className="space-y-4 text-sm text-[var(--ds-text-secondary)]">
          <p>Use this surface for approvals, destructive confirmations, and richer contextual forms.</p>
          <p>It shares the same radius, border, and shadow tokens as cards and popovers.</p>
        </div>
      </Dialog>
    </div>
  );
}
