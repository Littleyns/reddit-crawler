"use client";

import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";

export function ApiKeyInput({
  value,
  onChange,
}: {
  value: string;
  onChange: (nextValue: string) => void;
}) {
  const [visible, setVisible] = useState(false);

  return (
    <div className="flex items-center rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3">
      <input
        type={visible ? "text" : "password"}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="w-full bg-transparent outline-none"
        placeholder="Paste a Reddit API key"
      />
      <button
        type="button"
        onClick={() => setVisible((current) => !current)}
        className="text-[var(--color-muted)] hover:text-[var(--color-foreground)]"
        aria-label={visible ? "Hide API key" : "Show API key"}
      >
        {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </button>
    </div>
  );
}
