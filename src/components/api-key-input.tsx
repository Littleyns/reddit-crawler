"use client";

import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

export function ApiKeyInput({
  value,
  onChange,
  error,
}: {
  value: string;
  onChange: (nextValue: string) => void;
  error?: string;
}) {
  const [visible, setVisible] = useState(false);

  return (
    <Input
      type={visible ? "text" : "password"}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder="Paste a Reddit API key"
      error={error}
      trailingAdornment={
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => setVisible((current) => !current)}
          leadingIcon={visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          aria-label={visible ? "Hide API key" : "Show API key"}
        />
      }
    />
  );
}
