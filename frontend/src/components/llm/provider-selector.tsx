"use client"

import type { LlmProvider } from "./providers"
import { ALL_PROVIDERS, AVAILABLE_PROVIDER_IDS } from "./providers"
import { Check } from "lucide-react"

const ICON_MAP: Record<LlmProvider["id"], string> = {
  openai: "🔵",
  openrouter: "🔀",
  "google-gemini": "🟡",
  anthropic: "⚪",
  groq: "⚡",
  mistral: "🐘",
  together: "🤝",
  deepinfra: "🌐",
  siliconflow: "🔴",
  novita: "💎",
  ollama: "🏠",
  lmstudio: "🖥️",
}

export function ProviderSelector({ selected, onChange }: { selected?: string; onChange: (id: LlmProvider["id"]) => void }) {
  return (
    <section className="flex flex-col gap-3">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
        {ALL_PROVIDERS.map((p) => {
          const active = selected === p.id
          return (
            <button
              key={p.id}
              type="button"
              onClick={() => onChange(p.id)}
              className={
                "group flex items-start gap-3 p-4 border transition-all text-left rounded-none " +
                (active
                  ? "border-[var(--color-accent)] bg-[var(--color-accent)/10] shadow-sm"
                  : "border-[var(--color-border)] bg-[var(--color-surface-high)] hover:border-[var(--color-accent)]/50")
              }
            >
              <span className="flex h-9 w-9 shrink-0 items-center justify-center text-md border border-[var(--color-border-muted)] rounded-none bg-[var(--color-surface-low)]">
                {ICON_MAP[p.id] ?? "🌐"}
              </span>
              <div className="flex flex-col gap-1 min-w-0">
                <span className={"text-[11px] truncate " + (active ? "font-semibold text-[var(--color-accent)]" : "text-[var(--color-fg-primary])")}>
                  {p.name}
                </span>
                <span className={"text-[9px] leading-relaxed line-clamp-2 " + (active ? "text-[var(--color-accent)]/80" : "text-[var(--color-fg-muted)")}>
                  {p.description}
                </span>
              </div>
              <Check className={"h-4 w-4 ml-auto shrink-0 opacity-0 group-hover:opacity-60 transition-opacity " + (active ? "text-[var(--color-accent)] opacity-100" : "")} />
            </button>
          )
        })}
      </div>

      {selected && (
        <footer className="flex gap-3 pt-1 text-left">
          {AVAILABLE_PROVIDER_IDS.map((id) => {
            const p = ALL_PROVIDERS.find((x) => x.id === id)!
            return (
              <span key={p.id} className="flex gap-2 text-[9px] text-[var(--color-fg-muted)]">
                <span className="opacity-60">{ICON_MAP[p.id] ?? "🌐"}</span>
                <span>{p.name}</span>
              </span>
            )
          })}
        </footer>
      )}
    </section>
  )
}
