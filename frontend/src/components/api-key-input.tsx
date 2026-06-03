"use client";

import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";

interface ApiKeyInputProps {
  value: string;
  onChange: (v: string) => void;
  provider: string;
}

export function ApiKeyInput({ value, onChange, provider }: ApiKeyInputProps) {
  const [show, setShow] = useState(false);
  
  let isValid = value.length > 8;
  if (provider === "openrouter") {
    isValid = /^sk-or-v2-[a-zA-Z0-9]+$/i.test(value);
  } else if (provider === "openai") {
    isValid = /^sk-(pro|sk)-[a-zA-Z0-9]+$/i.test(value);
  } else if (provider === "azure") {
    isValid = /^[a-zA-Z0-9-]+$/.test(value) && value.length > 10;
  }
  
  const placeholderText = "sk-" + provider.substring(0, 2).toLowerCase() + "-...";
  const borderColor = !isValid && value.length > 0 ? "border-[var(--color-danger)]" : "border-[var(--color-border)]";
  const statusText = isValid && value.length > 0 ? "text-[var(--color-success-text)]" : "text-[var(--color-fg-muted)]";

  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">API Key ({provider})</label>
      <div className="relative">
        <input
          type={show ? "text" : "password"}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholderText}
          className={"w-full pr-16 pl-3 py-2.5 border font-mono text-[11px] rounded-none outline-none focus:border-[var(--color-accent)] transition-colors " + borderColor}
        />
        <button
          type="button"
          onClick={() => setShow(!show)}
          className="absolute right-3 top-2.5 p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-fg-primary)]"
        >
          {show ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
        </button>
      </div>
      {value.length > 0 && !isValid && (
        <span className="text-[9px] text-[var(--color-danger)]">Valid {provider} key required</span>
      )}
      {value.length > 0 && isValid && !show && (
        <span className={"text-[9px] " + statusText}>Key accepted ({value.length} chars)</span>
      )}
    </div>
  );
}

export default ApiKeyInput;
