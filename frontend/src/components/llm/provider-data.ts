/**
 * Comprehensive LLM provider registry for Reddit Crawler.
 * Based on Vercel AI SDK supported providers + community ones.
 */

export interface LLMProvider {
  id: string;
  name: string;
  description: string;
  apiKeyUrl: string;      // Where to get the API key
  defaultBaseUrl: string;  // Default base URL for this provider
  baseUrlPlaceholder?: string;
  supportsModels: boolean; // Can we fetch models automatically? (via list endpoint)
}

export const ALL_PROVIDERS: LLMProvider[] = [
  // ── OpenAI ecosystem ──
  {
    id: "openai",
    name: "OpenAI",
    description: "GPT-4o, GPT-3.5 Turbo, o1-series models",
    apiKeyUrl: "https://platform.openai.com/api-keys",
    defaultBaseUrl: "https://api.openai.com/v1",
    supportsModels: true,
  },

  // ── Google Gemini ecosystem ──
  {
    id: "google-gemini",
    name: "Google Gemini",
    description: "Gemini Pro, Gemini Ultra, Flash models (Google AI Studio)",
    apiKeyUrl: "https://aistudio.google.com/app/apikey",
    defaultBaseUrl: "https://generativelanguage.googleapis.com/v1beta",
    supportsModels: true,
  },

  // ── Anthropic ──
  {
    id: "anthropic",
    name: "Anthropic Claude",
    description: "Claude Haiku, Sonnet, Opus models (Anthropic)",
    apiKeyUrl: "https://console.anthropic.com/settings/keys",
    defaultBaseUrl: "https://api.anthropic.com/v1",
    supportsModels: true,
  },

  // ── Meta/Llama ecosystem ──
  {
    id: "groq",
    name: "Groq (Llama)",
    description: "Ultra-fast inference for Llama 3.1 & other models via Groq Cloud",
    apiKeyUrl: "https://console.groq.com/keys",
    defaultBaseUrl: "https://api.groq.com/openai/v1",
    supportsModels: true,
  },

  // ── Perplexity AI ──
  {
    id: "perplexity",
    name: "Perplexity AI",
    description: "Sonar models optimized for search & research queries",
    apiKeyUrl: "https://docs.perplexity.ai/docs/getting-started",
    defaultBaseUrl: "https://api.perplexity.ai",
    supportsModels: true,
  },

  // ── Mistral AI ──
  {
    id: "mistral",
    name: "Mistral AI",
    description: "Mistral Large, Small, Open models via Mistral API",
    apiKeyUrl: "https://console.mistral.ai/api-keys/",
    defaultBaseUrl: "https://api.mistral.ai/v1",
    supportsModels: true,
  },

  // ── Together AI ──
  {
    id: "together",
    name: "Together AI",
    description: "Hundreds of open-weight models with unified API (CodeLlama, Mixtral, etc.)",
    apiKeyUrl: "https://api.together.xyz/settings/api-keys",
    defaultBaseUrl: "https://api.together.xyz/v1",
    supportsModels: true,
  },

  // ── OpenRouter (aggregator) ──
  {
    id: "openrouter",
    name: "OpenRouter",
    description: "Aggregator: access 200+ models from OpenAI, Google, Anthropic, Meta & more in one API key",
    apiKeyUrl: "https://openrouter.ai/keys",
    defaultBaseUrl: "https://openrouter.ai/api/v1",
    supportsModels: true,
  },

  // ── Microsoft Azure (Azure OpenAI Service) ──
  {
    id: "azure-openai",
    name: "Microsoft Azure",
    description: "Enterprise Azure OpenAI deployment. Requires base URL + API key + model deployment name.",
    apiKeyUrl: "https://portal.azure.com/#blade/Microsoft_Azure_Productivity/ResourceExplorerBlade/",
    defaultBaseUrl: "",
    baseUrlPlaceholder: "https://{resource}.openai.azure.com/openai/deployments/{deployment}/chat/completions?api-version=2024-02-01",
    supportsModels: false, // Requires manual deployment name
  },

  // ── Groq with custom endpoint ──
  {
    id: "groq-custom",
    name: "Groq (Custom)",
    description: "Use your own Groq-compatible endpoint (on-prem or proxy)",
    apiKeyUrl: "https://console.groq.com/keys",
    defaultBaseUrl: "",
    baseUrlPlaceholder: "https://api.groq.com/openai/v1",
    supportsModels: false,
  },

  // ── Local Ollama (free & self-hosted) ──
  {
    id: "ollama",
    name: "Ollama (Local)",
    description: "Free local inference via Ollama. Install ollama.com and pull models locally.",
    apiKeyUrl: "", // No API key needed for Ollama
    defaultBaseUrl: "http://localhost:11434/v1",
    supportsModels: true,
  },

  // ── Any OpenAI-compatible provider (custom base URL) ──
  {
    id: "openai-compat",
    name: "OpenAI-Compatible (Custom)",
    description: "Any provider with an OpenAI-compatible API (vLLM, LM Studio, text-gen-webui, etc.)",
    apiKeyUrl: "",
    defaultBaseUrl: "",
    baseUrlPlaceholder: "https://your-api-endpoint.com/v1/chat/completions",
    supportsModels: false, // User needs to specify model manually
  },

  // ── Cohere for AI ──
  {
    id: "cohere",
    name: "Cohere for AI",
    description: "Command R+, Command Light models optimized for enterprise RAG & search",
    apiKeyUrl: "https://dashboard.cohere.com/api-keys",
    defaultBaseUrl: "https://api.cohere.ai/v1",
    supportsModels: true,
  },

  // ── DeepInfra (open models) ──
  {
    id: "deepinfra",
    name: "DeepInfra",
    description: "Hundred+ open models (Llama, Mixtral, Qwen, etc.) with pay-per-token pricing",
    apiKeyUrl: "https://deepinfra.com/dashboard/settings",
    defaultBaseUrl: "https://api.deepinfra.com/v1/openai",
    supportsModels: true,
  },

  // ── Novita AI (budget inference) ──
  {
    id: "novita",
    name: "Novita AI",
    description: "Cost-effective inference for Llama, Mixtral, Qwen and other open models",
    apiKeyUrl: "https://novita.ai/settings/token",
    defaultBaseUrl: "https://api.novita.ai/v3/openai",
    supportsModels: true,
  },

  // ── SiliconFlow (Chinese LLM providers) ──
  {
    id: "siliconflow",
    name: "SiliconFlow",
    description: "Qwen, Yi, Llama through a unified API. Great for Chinese models & multilingual support.",
    apiKeyUrl: "https://cloud.siliconflow.cn/account/ak",
    defaultBaseUrl: "https://api.siliconflow.cn/v1",
    supportsModels: true,
  },
];

/** Helper: check if a provider requires an API key on the server side */
export function providerHasApiKeyUrl(provider: LLMProvider): boolean {
  return !!provider.apiKeyUrl;
}

