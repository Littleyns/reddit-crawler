"use client";

import { useState } from "react";
import { CheckCircle, XCircle, Loader2 } from "lucide-react";

type TestStatus = "idle" | "testing" | "success" | "error";

interface LLMConnectionResult {
  success: boolean;
  latencyMs: number;
  message: string;
}

interface ConnectionTestProps {
  baseUrl?: string;
  apiKey?: string;
  modelId?: string;
}

export function LLMConnectionTest({ baseUrl, apiKey, modelId }: ConnectionTestProps) {
  const [status, setStatus] = useState<TestStatus>("idle");
  const [result, setResult] = useState<LLMConnectionResult | null>(null);

  const handleTest = async () => {
    if (!baseUrl || !apiKey) return;
    
    setStatus("testing");
    
    try {
      // Simulate connection test (replace with real API call in production)
      const response = await fetch(baseUrl + "/api/llm/test", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Bearer " + apiKey,
        },
        body: JSON.stringify({ modelId }),
      });
      
      const data = await response.json();
      
      setStatus(data.success ? "success" : "error");
      setResult(data);
    } catch (err) {
      setStatus("error");
      setResult({ success: false, latencyMs: 0, message: "Connection failed" });
    }
  };

  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Connection Test</label>
      
      <button 
        type="button"
        onClick={handleTest}
        disabled={status === "testing"}
        className={
          "btn-sq flex items-center gap-1.5 justify-center px-3 py-[5px] rounded-none text-[11px]" +
          (status === "testing" ? " btn-sq-muted opacity-70" : " btn-sq-primary")
        }
      >
        {status === "testing" ? (
          <>
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            Testing...
          </>
        ) : status === "success" ? (
          <>
            <CheckCircle className="h-3.5 w-3.5 text-[var(--color-success)]" />
            {result ? result.latencyMs + "ms" : "Connected"}
          </>
        ) : status === "error" ? (
          <>
            <XCircle className="h-3.5 w-3.5 text-[var(--color-danger)]" />
            Failed
          </>
        ) : (
          "Test Connection"
        )}
      </button>
      
      {result && status !== "idle" && (
        <span className={"text-[9px] font-medium" + (status === "success" ? " text-[var(--color-success-text)]" : " text-[var(--color-danger-text)]")}>
          {result.message}
        </span>
      )}
    </div>
  );
}
