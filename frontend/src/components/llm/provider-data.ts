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
  {id: "openai", name: "OpenAI", description: "GPT-4o, GPT-3.5 Turbo, o1-series models", apiKeyUrl: "https://platform.openai.com/api-keys", defaultBaseUrl: "https://api.openai.com/v1", supportsModels: true},
  {id: "google-gemini", name: "Google Gemini", description: "Gemini Pro, Gemini Ultra, Flash models (Google AI Studio)", apiKeyUrl: "https://aistudio.google.com/app/apikey", defaultBaseUrl: "https://generativelanguage.googleapis.com/v1beta", supportsModels: true},
  {id: "anthropic", name: "Anthropic Claude", description: "Claude Haiku, Sonnet, Opus models (Anthropic)", apiKeyUrl: "https://console.anthropic.com/settings/keys", defaultBaseUrl: "https://api.anthropic.com/v1", supportsModels: true},
  {id: "groq", name: "Groq (Llama)", description: "Ultra-fast inference for Llama 3.1 & other models via Groq Cloud", apiKeyUrl: "https://console.groq.com/keys", defaultBaseUrl: "https://api.groq.com/openai/v1", supportsModels: true},
  {id: "perplexity", name: "Perplexity AI", description: "Sonar models optimized for search & research queries", apiKeyUrl: "https://docs.perplexity.ai/docs/getting-started", defaultBaseUrl: "https://api.perplexity.ai", supportsModels: true},
  {id: "mistral", name: "Mistral AI", description: "Mistral Large, Small, Open models via Mistral API", apiKeyUrl: "https://console.mistral.ai/api-keys/", defaultBaseUrl: "https://api.mistral.ai/v1", supportsModels: true},
  {id: "together", name: "Together AI", description: "Hundreds of open-weight models with unified API (CodeLlama, Mixtral, etc.)", apiKeyUrl: "https://api.together.xyz/settings/api-keys", defaultBaseUrl: "https://api.together.xyz/v1", supportsModels: true},
  {id: "openrouter", name: "OpenRouter", description: "Aggregator: access 200+ models from OpenAI, Google, Anthropic, Meta & more in one API key", apiKeyUrl: "https://openrouter.ai/keys", defaultBaseUrl: "https://openrouter.ai/api/v1", supportsModels: true},
  {id: "azure-openai", name: "Microsoft Azure", description: "Enterprise Azure OpenAI deployment. Requires base URL + API key + model deployment name.", apiKeyUrl: "https://portal.azure.com/#blade/Microsoft_Azure_Productivity/ResourceExplorerBlade/", defaultBaseUrl: "", baseUrlPlaceholder: "https://{resource}.openai.azure.com/openai/deployments/{deployment}/chat/completions?api-version=2024-02-01", supportsModels: false},
  {id: "groq-custom", name: "Groq (Custom)", description: "Use your own Groq-compatible endpoint (on-prem or proxy)", apiKeyUrl: "https://console.groq.com/keys", defaultBaseUrl: "", baseUrlPlaceholder: "https://api.groq.com/openai/v1", supportsModels: false},
  {id: "ollama", name: "Ollama (Local)", description: "Free local inference via Ollama. Install ollama.com and pull models locally.", apiKeyUrl: "", defaultBaseUrl: "http://localhost:11434/v1", supportsModels: true},
  {id: "openai-compat", name: "OpenAI-Compatible (Custom)", description: "Any provider with an OpenAI-compatible API (vLLM, LM Studio, text-gen-webui, etc.)", apiKeyUrl: "", defaultBaseUrl: "", baseUrlPlaceholder: "https://your-api-endpoint.com/v1/chat/completions", supportsModels: false},
  {id: "cohere", name: "Cohere for AI", description: "Command R+, Command Light models optimized for enterprise RAG & search", apiKeyUrl: "https://dashboard.cohere.com/api-keys", defaultBaseUrl: "https://api.cohere.ai/v1", supportsModels: true},
  {id: "deepinfra", name: "DeepInfra", description: "Hundred+ open models (Llama, Mixtral, Qwen, etc.) with pay-per-token pricing", apiKeyUrl: "https://deepinfra.com/dashboard/settings", defaultBaseUrl: "https://api.deepinfra.com/v1/openai", supportsModels: true},
  {id: "novita", name: "Novita AI", description: "Cost-effective inference for Llama, Mixtral, Qwen and other open models", apiKeyUrl: "https://novita.ai/settings/token", defaultBaseUrl: "https://api.novita.ai/v3/openai", supportsModels: true},
  {id: "siliconflow", name: "SiliconFlow", description: "Qwen, Yi, Llama through a unified API. Great for Chinese models & multilingual support.", apiKeyUrl: "https://cloud.siliconflow.cn/account/ak", defaultBaseUrl: "https://api.siliconflow.cn/v1", supportsModels: true},
];

export function providerHasApiKeyUrl(provider: LLMProvider): boolean {
  return !!provider.apiKeyUrl;
}

/** Helper: all supported model IDs per provider (for autocomplete/fallback) */
function makeModelMap(): Record<string, string[]> {
  return {
    openai: ["gpt-4o", "gpt-4o-mini", "chatgpt-4o-latest", "gpt-4-turbo", "o1-preview", "o1-mini"],
    "google-gemini": ["gemini-pro", "gemini-pro-vision", "gemini-flash", "gemini-2.0-flash"],
    anthropic: ["claude-3-opus-latest", "claude-3-sonnet-20240229", "claude-3-haiku-20240307", "claude-3-5-sonnet-20241022"],
    groq: ["llama-3.1-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768", "gemma2-9b-it"],
    perplexity: ["sonar-pro", "sonar-reasoning-pro", "sonar-reasoning"],
    mistral: ["mistral-large-latest", "mistral-small-latest", "open-mistral-nemo", "open-mixtral-8x7b"],
    together: ["meta-llama/Llama-3.1-70B-Instruct-Turbo", "meta-llama/Llama-3.1-8B-Instruct-Turbo", "mistralai/Mixtral-8x7B-Instruct-v0.1", "Qwen/Qwen2.5-72B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3"],
    openrouter: ["anthropic/claude-3.5-sonnet", "openai/gpt-4o", "google/gemini-pro-1.5", "meta-llama/llama-3.1-70b-instruct", "mistralai/mixtral-8x7b"],
    ollama: ["qwen3:32b", "qwen2.5:14b", "llama3.1:8b", "llama3.1:70b"],
    cohere: ["command-r-plus", "command-r", "command-lite"],
    deepinfra: ["meta-llama/Llama-3.3-70B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3", "mistralai/Mixtral-8x7B-Instruct-v0.1"],
  };
}

export const PROVIDER_DEFAULT_MODELS: Record<string, string[]> = makeModelMap();
