"use client";

import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";

interface ApiKeyInputProps {
  value: string;
  onChange: (v: string) => void;
  provider: string;
}

export function ApiKeyInput({ value, onChange, provider }: ApiKeyInputProps) {
  const [show, setShow] = useState(false);
  
  const patterns: Record<string, RegExp> = {
    openrouter: /^sk-or-v2-[a-zA-Z0-9]{32,}$/,
    openai: /^sk-(?:pro|-)[a-zA-Z0-9]{24,}$/,
    azure: /^[a-zA-Z0-9-]+$/,
  };
  
  const isValid = patterns[provider] ? patterns[provider].test(value) : value.length > 8;
  
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">
        API Key ({provider})
      </label>
      <div className="relative">
        <input
          type={show ? "text" : "password"}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={"sk-" + provider.substring(0, 2) + "-..."}
          className={
            "w-full pr-16 pl-3 py-2.5 border font-mono text-[11px] bg-[var(--color-surface-high)] rounded-none focus:border-[var(--color-accent)] outline-none transition-colors" +
            (isValid || !value ? "" : " border-[var(--color-danger)]") +
            (!isValid && value ? " border-[var(--color-danger)]" : " border-[var(--color-border)]")
          }
        />
        <button 
          type="button" 
          onClick={() => setShow(!show)} 
          className="absolute right-3 top-2.5 p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-fg-primary)]"
        >
          {show ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
        </button>
      </div>
      {value && !isValid && (
        <span className="text-[9px] text-[var(--color-danger)]">Valid {provider} key required</span>
      )}
      {value && isValid && !show && (
        <span className="text-[9px] text-[var(--color-success-text)]">Key accepted ({value.length} chars)</span>
      )}
    </div>
  );
}
