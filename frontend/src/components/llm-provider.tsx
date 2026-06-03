"use client";

import { useState } from "react";
import { CheckCircle, XCircle, Loader2 } from "lucide-react";

type Provider = "openrouter" | "openai" | "azure" | "custom";

interface LLMConfigState {
  provider: Provider;
  apiKey: string;
  baseUrl: string;
  modelId: string;
}

export function LLMProviderSelection({ selected, onChange }: { selected: Provider; onChange: (p: Provider) => void }) {
  const providers: { id: Provider; name: string; desc: string; icon: string }[] = [
    { id: "openrouter", name: "OpenRouter", desc: "Multi-model access via single API", icon: "🔀" },
    { id: "openai", name: "OpenAI", desc: "GPT-4, GPT-3.5 and more", icon: "🤖" },
    { id: "azure", name: "Azure OpenAI", desc: "Enterprise Azure deployment", icon: "☁️" },
  ];

  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
      {providers.map((p) => (
        <button
          key={p.id}
          type="button"
          onClick={() => onChange(p.id)}
          className={`flex items-center gap-2 p-3 border transition-colors text-left rounded-none ${
            selected === p.id
              ? "border-[var(--color-accent)] bg-[var(--color-accent)/5] text-[var(--color-fg-primary)]"
              : "border-[var(--color-border)] bg-[var(--color-surface-high)] text-[var(--color-fg-muted)] hover:border-[var(--color-border-muted)]"
          }`}
        >
          <span className="text-lg">{p.icon}</span>
          <div>
            <div className="text-xs font-semibold">{p.name}</div>
            <div className="text-[10px] opacity-70">{p.desc}</div>
          </div>
        </button>
      ))}
    </div>
  );
}
