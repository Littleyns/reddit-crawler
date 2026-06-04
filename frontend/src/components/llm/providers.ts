/**
 * Comprehensive LLM provider registry for Reddit Crawler.
 * Based on Vercel AI SDK supported providers + community aggregator APIs.
 */

export interface LlmProvider {
  id: string
  name: string
  description: string
  apiKeyUrl: string | null // Where to get the API key
  defaultBaseUrl: string
  baseUrlPlaceholder?: string
  requiresModelManual: boolean
}

const PROVIDERS: ReadonlyArray<LlmProvider> = [
  {
    id: 'openai',
    name: 'OpenAI',
    description: 'GPT-4o, GPT-3.5 Turbo, o1-series models',
    apiKeyUrl: 'https://platform.openai.com/api-keys',
    defaultBaseUrl: 'https://api.openai.com/v1',
    requiresModelManual: false,
  },
  {
    id: 'openrouter',
    name: 'OpenRouter',
    description: 'Aggregator: access 200+ models from OpenAI, Google, Anthropic, Meta & more in one API key',
    apiKeyUrl: 'https://openrouter.ai/keys',
    defaultBaseUrl: 'https://openrouter.ai/api/v1',
    requiresModelManual: false,
  },
  {
    id: 'google-gemini',
    name: 'Google Gemini (via Google AI Studio)',
    description: 'Gemini Pro, Gemma Flash, Gemini Ultra — Google official model family',
    apiKeyUrl: 'https://aistudio.google.com/app/apikey',
    defaultBaseUrl: 'https://generativelanguage.googleapis.com/v1beta',
    requiresModelManual: false,
  },
  {
    id: 'anthropic',
    name: 'Anthropic Claude',
    description: 'Claude Haiku, Sonnet, Opus models (Anthropic official)',
    apiKeyUrl: 'https://console.anthropic.com/settings/keys',
    defaultBaseUrl: 'https://api.anthropic.com/v1/',
    requiresModelManual: false,
  },
  {
    id: 'groq',
    name: 'Groq (Llama on fast GPU)',
    description: 'Ultra-fast inference for Llama 3.1 & other open models via Groq Cloud',
    apiKeyUrl: 'https://console.groq.com/keys',
    defaultBaseUrl: 'https://api.groq.com/openai/v1',
    requiresModelManual: false,
  },
  {
    id: 'mistral',
    name: 'Mistral AI',
    description: 'Mistral Large, Small, Pixtral & Open-weight models via Mistral API',
    apiKeyUrl: 'https://console.mistral.ai/api-keys/',
    defaultBaseUrl: 'https://api.mistral.ai/v1',
    requiresModelManual: false,
  },
  {
    id: 'together',
    name: 'Together AI',
    description: 'Hundreds of open-weight models with unified API (CodeLlama, Mixtral, Qwen …)',
    apiKeyUrl: 'https://api.together.xyz/settings/api-keys',
    defaultBaseUrl: 'https://api.together.xyz/v1',
    requiresModelManual: false,
  },
  {
    id: 'deepinfra',
    name: 'DeepInfra (Open Models)',
    description: 'Hundred+ open models — Llama, Mixtral, Qwen — with pay-per-token pricing.',
    apiKeyUrl: 'https://deepinfra.com/dashboard/settings',
    defaultBaseUrl: 'https://api.deepinfra.com/v1/openai',
    requiresModelManual: false,
  },
  {
    id: 'siliconflow',
    name: 'SiliconFlow (Qwen / Yi / Llama)',
    description: 'Multilingual & Chinese models through a unified API. Qwen, Yi, Llama …',
    apiKeyUrl: 'https://cloud.siliconflow.cn/account/ak',
    defaultBaseUrl: 'https://api.siliconflow.cn/v1',
    requiresModelManual: false,
  },
  {
    id: 'novita',
    name: 'Novita AI',
    description: 'Cost-effective inference for Llama, Mixtral, Qwen and other open models.',
    apiKeyUrl: 'https://novita.ai/settings/token',
    defaultBaseUrl: 'https://api.novita.ai/v3/openai',
    requiresModelManual: false,
  },
  {
    id: 'ollama',
    name: 'Ollama (Free Local Inference)',
    description: 'Run models locally on your machine. Install ollama.com and pull models.',
    apiKeyUrl: null,
    defaultBaseUrl: 'http://localhost:11434/v1/',
    requiresModelManual: false,
  },
  {
    id: 'lmstudio',
    name: 'LM Studio (Local)',
    description: 'Run any open model locally through LM Studio server.',
    apiKeyUrl: null,
    defaultBaseUrl: 'http://localhost:1234/v1/',
    requiresModelManual: false,
  },
] as const

/** Ordered list of every available provider ID — used by the selector component. */
export const AVAILABLE_PROVIDER_IDS = PROVIDERS.map(
  (p): LlmProvider['id'] => p.id
)

/** All providers, exported for use in components. */
export {
  /** All supported provider definitions. */
  PROVIDERS as ALL_PROVIDERS,
}

/** Helper: check if a provider requires an API key on the server side. */
export function providerNeedsApiKeyUrl(provider: LlmProvider): boolean {
  return provider.apiKeyUrl != null
}
