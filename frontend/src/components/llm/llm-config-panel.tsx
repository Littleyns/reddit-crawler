"use client";

import { useState, useEffect } from "react";
import { ProviderSelector } from "./provider-selector";
import { ModelSelector } from "./model-selector";
import { ApiKeyInput } from "./api-key-input";
import { LLMConnectionTest } from "./llm-connection-test";

type Provider = "openrouter" | "openai" | "azure" | "custom";

const STORAGE_KEY = "reddit-crawler-llm-config";

interface LLMConfig {
  provider: Provider;
  apiKey: string;
  baseUrl: string;
  modelId: string;
}

const DEFAULT_CONFIG: LLMConfig = {
  provider: "openrouter",
  apiKey: "",
  baseUrl: "",
  modelId: "",
};

const PROVIDER_BASE_URLS: Record<Provider, string> = {
  openrouter: "https://openrouter.ai/api/v1/chat/completions",
  openai: "https://api.openai.com/v1/chat/completions",
  azure: "",
  custom: "",
};

export function LLMConfigPanel() {
  const [config, setConfig] = useState<LLMConfig>({ ...DEFAULT_CONFIG });
  const [saved, setSaved] = useState(false);

  // Load from localStorage on mount
  useEffect(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        setConfig(JSON.parse(saved));
      }
    } catch (e) {
      console.warn("Failed to load LLM config from localStorage:", e);
    }
  }, []);

  // Load from API on mount  
  useEffect(() => {
    const loadFromServer = async () => {
      try {
        const response = await fetch("/api/llm/config");
        if (response.ok) {
          const data = await response.json();
          setConfig({
            provider: data.provider || "openrouter",
            apiKey: "", // Never show full key on load
            baseUrl: data.baseUrl || PROVIDER_BASE_URLS[data.provider as Provider] || "",
            modelId: data.modelId || "",
          });
        }
      } catch (e) {
        // Silently fail — will use localStorage or defaults
      }
    };
    
    loadFromServer();
  }, []);

  const updateField = (field: keyof LLMConfig, value: string | Provider) => {
    setConfig((prev) => {
      const updated = { ...prev, [field]: value };
      
      // Auto-set baseUrl when provider changes
      if (field === "provider") {
        (updated as any).baseUrl = PROVIDER_BASE_URLS[value as Provider] || "";
      }
      
      localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
      return updated;
    });
  };

  const handleSave = async () => {
    try {
      const response = await fetch("/api/llm/config", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
      });
      
      if (response.ok) {
        setSaved(true);
        setTimeout(() => setSaved(false), 2000);
      }
    } catch (e) {
      console.error("Failed to save LLM config:", e);
    }
  };

  return (
    <div className="space-y-6">
      <div className="space-y-3">
        <ProviderSelector 
          selected={config.provider} 
          onChange={(p) => updateField("provider", p)} 
        />
        
        {config.provider === "custom" && (
          <InputWithLabel
            label="Custom Base URL"
            value={config.baseUrl}
            onChange={(v: string) => updateField("baseUrl", v)}
            placeholder="https://your-api-endpoint.com/v1/chat/completions"
          />
        )}
        
        <ModelSelector 
          value={config.modelId}
          provider={config.provider as string}
          onChange={(m) => updateField("modelId", m)}
        />
        
        <ApiKeyInput
          value={config.apiKey}
          onChange={(v: string) => updateField("apiKey", v)}
          provider={config.provider}
        />
      </div>

      {/* Connection Test */}
      <LLMConnectionTest 
        baseUrl={config.baseUrl || PROVIDER_BASE_URLS[config.provider] || ""}
        apiKey={config.apiKey}
        modelId={config.modelId}
      />

      {/* Save button */}
      <div className="flex items-center gap-3 pt-2">
        <button 
          onClick={handleSave}
          className="btn-sq btn-sq-primary rounded-none px-4 py-[5px] text-sm font-medium"
        >
          {saved ? "Saved!" : "Save Configuration"}
        </button>
      </div>
    </div>
  );
}

// Helper component for simple labelled inputs
function InputWithLabel({ label, value, onChange, placeholder }: any) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[9px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">{label}</label>
      <input 
        type="text" 
        value={value || ""} 
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full px-3 py-2.5 border border-[var(--color-border)] bg-[var(--color-surface-high)] text-[11px] text-[var(--color-fg-primary)] rounded-none focus:border-[var(--color-accent)] outline-none" 
      />
    </div>
  );
}
