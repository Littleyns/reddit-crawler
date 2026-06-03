"use client";

import { useState } from "react";
import { Search } from "lucide-react";

const modelsByProvider: Record<string, string[]> = {
  openrouter: [
    "anthropic/claude-sonnet-4",
    "anthropic/claude-opus-4",
    "google/gemini-2.5-pro",
    "openai/gpt-4.1",
    "meta/llama-3.3-70b",
  ],
  openai: ["gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini"],
  azure: ["gpt-4-turbo", "gpt-4", "gpt-35-turbo"],
  custom: [],
};

interface ModelSelectorProps {
  value?: string;
  provider?: string;
  onChange: (m: string) => void;
}

export function ModelSelector({ value, provider = "openrouter", onChange }: ModelSelectorProps) {
  const models = modelsByProvider[provider] || [];
  
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Model</label>
      <div className="relative">
        <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-[var(--color-fg-muted)] pointer-events-none" />
        <input
          type="text"
          placeholder="Search models..."
          className="w-full pl-7 pr-4 py-2 border border-[var(--color-border)] bg-[var(--color-surface-high)] text-[11px] text-[var(--color-fg-primary)] rounded-none placeholder:text-[var(--color-fg-quiet)] focus:border-[var(--color-accent)] outline-none"
        />
      </div>
      <select
        value={value || ""}
        onChange={(e) => onChange(e.target.value)}
        className="w-full px-3 py-2 border border-[var(--color-border)] bg-[var(--color-surface-high)] text-[11px] text-[var(--color-fg-primary)] rounded-none focus:border-[var(--color-accent)] outline-none"
      >
        <option value="">Select a model...</option>
        {models.map((m) => (
          <option key={m} value={m}>{m}</option>
        ))}
        <option value="__custom">+ Custom model ID</option>
      </select>
    </div>
  );
}
