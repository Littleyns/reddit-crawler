"use client";

import { forwardRef, useId } from "react";
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { AlertCircle, CheckCircle2 } from "lucide-react";
import { cn } from "@/lib/utils";

export type ValidationState = "default" | "success" | "error";

interface BaseFieldShellProps {
  label?: ReactNode;
  description?: ReactNode;
  error?: ReactNode;
  successMessage?: ReactNode;
  validationState?: ValidationState;
  containerClassName?: string;
  required?: boolean;
}

function getValidationState({
  validationState,
  error,
  successMessage,
}: Pick<BaseFieldShellProps, "validationState" | "error" | "successMessage">) {
  if (validationState) {
    return validationState;
  }
  if (error) {
    return "error";
  }
  if (successMessage) {
    return "success";
  }
  return "default";
}

const shellToneClasses: Record<ValidationState, string> = {
  default:
    "border-[var(--ds-border-soft)] bg-[rgba(8,17,31,0.46)] focus-within:border-[var(--ds-border-strong)] focus-within:shadow-[var(--ds-shadow-focus)]",
  success:
    "border-[rgba(82,211,166,0.38)] bg-[rgba(14,30,31,0.6)] focus-within:border-[rgba(82,211,166,0.62)]",
  error:
    "border-[rgba(255,107,127,0.48)] bg-[rgba(36,13,21,0.58)] focus-within:border-[rgba(255,107,127,0.72)] focus-within:shadow-[0_0_0_4px_rgba(240,84,103,0.12)]",
};

function FieldMessage({
  state,
  error,
  successMessage,
  description,
}: {
  state: ValidationState;
  error?: ReactNode;
  successMessage?: ReactNode;
  description?: ReactNode;
}) {
  if (state === "error" && error) {
    return (
      <p className="mt-2 flex items-start gap-2 text-sm text-[var(--ds-danger-500)]">
        <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
        <span>{error}</span>
      </p>
    );
  }

  if (state === "success" && successMessage) {
    return (
      <p className="mt-2 flex items-start gap-2 text-sm text-[var(--ds-success-500)]">
        <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
        <span>{successMessage}</span>
      </p>
    );
  }

  if (!description) {
    return null;
  }

  return <p className="mt-2 text-sm text-[var(--ds-text-muted)]">{description}</p>;
}

function FieldShell({
  id,
  label,
  description,
  error,
  successMessage,
  validationState,
  containerClassName,
  required,
  children,
}: BaseFieldShellProps & { id: string; children: ReactNode }) {
  const state = getValidationState({ validationState, error, successMessage });

  return (
    <label className={cn("block", containerClassName)} htmlFor={id}>
      {label ? (
        <span className="mb-2.5 block text-sm font-medium text-[var(--ds-text-primary)]">
          {label}
          {required ? <span className="ml-1 text-[var(--ds-danger-500)]">*</span> : null}
        </span>
      ) : null}
      <div
        data-state={state}
        className={cn(
          "group flex items-center gap-3 rounded-[var(--radius-md)] border px-4",
          shellToneClasses[state],
        )}
      >
        {children}
      </div>
      <FieldMessage
        state={state}
        error={error}
        successMessage={successMessage}
        description={description}
      />
    </label>
  );
}

export interface InputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, "size">,
    BaseFieldShellProps {
  leadingIcon?: ReactNode;
  trailingAdornment?: ReactNode;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  {
    id,
    label,
    description,
    error,
    successMessage,
    validationState,
    containerClassName,
    leadingIcon,
    trailingAdornment,
    className,
    required,
    ...props
  },
  ref,
) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;

  return (
    <FieldShell
      id={fieldId}
      label={label}
      description={description}
      error={error}
      successMessage={successMessage}
      validationState={validationState}
      containerClassName={containerClassName}
      required={required}
    >
      {leadingIcon ? <span className="text-[var(--ds-text-muted)]">{leadingIcon}</span> : null}
      <input
        ref={ref}
        id={fieldId}
        aria-label={typeof label === "string" ? label : undefined}
        className={cn(
          "min-h-12 w-full bg-transparent py-3 text-sm text-[var(--ds-text-primary)] outline-none placeholder:text-[var(--ds-text-muted)]",
          className,
        )}
        required={required}
        {...props}
      />
      {trailingAdornment}
    </FieldShell>
  );
});

export interface TextareaProps
  extends TextareaHTMLAttributes<HTMLTextAreaElement>,
    BaseFieldShellProps {}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  {
    id,
    label,
    description,
    error,
    successMessage,
    validationState,
    containerClassName,
    className,
    required,
    ...props
  },
  ref,
) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;

  return (
    <FieldShell
      id={fieldId}
      label={label}
      description={description}
      error={error}
      successMessage={successMessage}
      validationState={validationState}
      containerClassName={containerClassName}
      required={required}
    >
      <textarea
        ref={ref}
        id={fieldId}
        aria-label={typeof label === "string" ? label : undefined}
        className={cn(
          "min-h-28 w-full resize-y bg-transparent py-3 text-sm text-[var(--ds-text-primary)] outline-none placeholder:text-[var(--ds-text-muted)]",
          className,
        )}
        required={required}
        {...props}
      />
    </FieldShell>
  );
});

export interface SelectProps
  extends SelectHTMLAttributes<HTMLSelectElement>,
    BaseFieldShellProps {}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  {
    id,
    label,
    description,
    error,
    successMessage,
    validationState,
    containerClassName,
    className,
    required,
    children,
    ...props
  },
  ref,
) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;

  return (
    <FieldShell
      id={fieldId}
      label={label}
      description={description}
      error={error}
      successMessage={successMessage}
      validationState={validationState}
      containerClassName={containerClassName}
      required={required}
    >
      <select
        ref={ref}
        id={fieldId}
        aria-label={typeof label === "string" ? label : undefined}
        className={cn(
          "min-h-12 w-full appearance-none bg-transparent py-3 pr-3 text-sm text-[var(--ds-text-primary)] outline-none",
          className,
        )}
        required={required}
        {...props}
      >
        {children}
      </select>
    </FieldShell>
  );
});

export interface CheckboxFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: ReactNode;
  description?: ReactNode;
}

export const CheckboxField = forwardRef<HTMLInputElement, CheckboxFieldProps>(function CheckboxField(
  { className, label, description, id, ...props },
  ref,
) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;

  return (
    <label
      htmlFor={fieldId}
      className={cn(
        "flex items-start gap-3 rounded-[var(--radius-md)] border border-[var(--ds-border-soft)] bg-[rgba(8,17,31,0.46)] px-4 py-3.5 text-sm",
        className,
      )}
    >
      <input
        ref={ref}
        id={fieldId}
        type="checkbox"
        className="mt-1 h-4 w-4 rounded border-[var(--ds-border-strong)] bg-transparent accent-[var(--ds-primary-500)]"
        {...props}
      />
      <span className="min-w-0">
        <span className="block font-medium text-[var(--ds-text-primary)]">{label}</span>
        {description ? (
          <span className="mt-1 block text-[var(--ds-text-muted)]">{description}</span>
        ) : null}
      </span>
    </label>
  );
});
