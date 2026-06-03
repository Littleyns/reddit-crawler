"use client";

import { Check } from "lucide-react";

type Provider = "openrouter" | "openai" | "azure" | "custom";

interface ProviderInfo {
  id: Provider;
  name: string;
  desc: string;
  icon: string;
}

const availableProviders: ProviderInfo[] = [
  { id: "openrouter", name: "OpenRouter", desc: "Multi-model access via single API", icon: "🔀" },
  { id: "openai", name: "OpenAI", desc: "GPT-4, GPT-3.5 and more", icon: "🤖" },
  { id: "azure", name: "Azure OpenAI", desc: "Enterprise Azure deployment", icon: "" },
];

interface ProviderSelectorProps {
  selected?: string;
  onChange: (p: Provider) => void;
}

export function ProviderSelector({ selected, onChange }: ProviderSelectorProps) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
      {availableProviders.map((p) => (
        <button
          key={p.id}
          type="button"
          onClick={() => onChange(p.id)}
          className={
            "flex items-center gap-2 p-3 border transition-colors text-left rounded-none text-[11px] h-full " +
              (selected === p.id
                ? "border-[var(--color-accent)] bg-[var(--color-accent)/5] text-[var(--color-fg-primary)] font-medium"
                : "border-[var(--color-border)] bg-[var(--color-surface-high)] text-[var(--color-fg-muted)] hover:border-[var(--color-border-muted)]")
          }
        >
          <span className="text-lg">{p.icon}</span>
          <div className="flex flex-col gap-0.5">
            <span>{p.name}</span>
            <span className="opacity-60 text-[9px]">{p.desc}</span>
          </div>
        </button>
      ))}
    </div>
  );
}
