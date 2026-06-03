import type { ButtonSize, ButtonVariant } from "@/components/ui/button";
import type { CardVariant } from "@/components/ui/card";
import type { ValidationState } from "@/components/ui/input";

export interface ComponentPropDoc {
  name: string;
  type: string;
  defaultValue?: string;
  description: string;
}

export interface ComponentDoc {
  name: string;
  summary: string;
  props: ComponentPropDoc[];
}

const buttonVariants: ButtonVariant[] = ["primary", "secondary", "ghost", "danger"];
const buttonSizes: ButtonSize[] = ["sm", "md", "lg"];
const cardVariants: CardVariant[] = ["default", "elevated", "outline", "spotlight"];
const inputStates: ValidationState[] = ["default", "success", "error"];

export const componentDocs: ComponentDoc[] = [
  {
    name: "Button",
    summary: `Action control with ${buttonVariants.join(", ")} variants and ${buttonSizes.join(", ")} sizes.`,
    props: [
      { name: "variant", type: buttonVariants.map((item) => `"${item}"`).join(" | "), defaultValue: '"primary"', description: "Visual emphasis level for the action." },
      { name: "size", type: buttonSizes.map((item) => `"${item}"`).join(" | "), defaultValue: '"md"', description: "Controls button height and spacing." },
      { name: "leadingIcon", type: "ReactNode", description: "Optional icon rendered before the label." },
      { name: "trailingIcon", type: "ReactNode", description: "Optional icon rendered after the label." },
      { name: "loading", type: "boolean", defaultValue: "false", description: "Disables the button while a request is pending." },
    ],
  },
  {
    name: "Card",
    summary: `Surface container supporting ${cardVariants.join(", ")} layouts.`,
    props: [
      { name: "variant", type: cardVariants.map((item) => `"${item}"`).join(" | "), defaultValue: '"default"', description: "Chooses the surface treatment." },
      { name: "as", type: '"section" | "article" | "div"', defaultValue: '"section"', description: "Semantic wrapper element." },
      { name: "className", type: "string", description: "Extends layout or spacing on the surface." },
    ],
  },
  {
    name: "Input",
    summary: `Field primitive with ${inputStates.join(", ")} validation states.`,
    props: [
      { name: "label", type: "ReactNode", description: "Visible field label." },
      { name: "description", type: "ReactNode", description: "Assistive helper text when there is no validation message." },
      { name: "error", type: "ReactNode", description: "Inline error text; automatically sets the field to error state." },
      { name: "successMessage", type: "ReactNode", description: "Inline success text; automatically sets the field to success state." },
      { name: "leadingIcon", type: "ReactNode", description: "Optional icon placed before the input text." },
      { name: "trailingAdornment", type: "ReactNode", description: "Optional custom content placed after the input text." },
    ],
  },
  {
    name: "Dialog",
    summary: "Accessible modal shell with close handling, overlay, and configurable width.",
    props: [
      { name: "open", type: "boolean", description: "Controls whether the dialog is visible." },
      { name: "onOpenChange", type: "(open: boolean) => void", description: "Notifies the parent when the dialog should open or close." },
      { name: "title", type: "ReactNode", description: "Dialog title content." },
      { name: "description", type: "ReactNode", description: "Optional supporting text under the title." },
      { name: "footer", type: "ReactNode", description: "Optional footer action area." },
      { name: "size", type: '"sm" | "md" | "lg"', defaultValue: '"md"', description: "Controls modal width." },
    ],
  },
  {
    name: "Popover / Tooltip",
    summary: "Lightweight floating content primitives for actions and contextual help.",
    props: [
      { name: "trigger", type: "ReactNode", description: "Interactive element that toggles the popover." },
      { name: "content", type: "ReactNode", description: "Popover body content." },
      { name: "align", type: '"start" | "center" | "end"', defaultValue: '"start"', description: "Horizontal alignment for the popover surface." },
      { name: "label", type: "ReactNode", description: "Tooltip text." },
      { name: "side", type: '"top" | "bottom"', defaultValue: '"top"', description: "Tooltip position relative to the trigger." },
    ],
  },
  {
    name: "DataTable",
    summary: "Typed table with client-side sorting, search input slotting, and pagination controls.",
    props: [
      { name: "columns", type: "readonly DataTableColumn<T>[]", description: "Column schema including renderers and sortability." },
      { name: "rows", type: "T[]", description: "Visible row data." },
      { name: "searchValue", type: "string", description: "Optional search field value shown above the table." },
      { name: "onSearchChange", type: "(value: string) => void", description: "Invoked when the built-in search field changes." },
      { name: "filtersSlot", type: "ReactNode", description: "Optional compact filter UI shown beside the search field." },
    ],
  },
];
