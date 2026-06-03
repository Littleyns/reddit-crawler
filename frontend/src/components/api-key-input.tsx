"use client";

import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";

export function ApiKeyInput({
  value,
  onChange,
}: {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  value: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  onChange: (value: string) => void;
}) {
  const [visible, setVisible] = useState(false);

  return (
    <div className="flex items-center border border-[var(--color-border)] bg-[var(--color-surface-high)] px-2.5 py-[4px] rounded-none">
      <input
        type={visible ? "text" : "password"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="min-w-0 flex-1 bg-transparent text-[11px] text-[var(--color-fg-primary)] outline-none placeholder:text-[var(--color-fg-muted)] rounded-none"
        placeholder="Paste your Reddit API key"
      />
      <button
        type="button"
        onClick={() => setVisible(!visible)}
        className="ml-2 shrink-0 text-[var(--color-fg-muted)] hover:text-[var(--color-fg-secondary)] transition-colors rounded-none"
        aria-label={visible ? "Hide API key" : "Show API key"}
      >
        {visible ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
      </button>
    </div>
  );
}
