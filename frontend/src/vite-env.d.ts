/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend base URL including /api. Defaults to http://localhost:8080/api. */
  readonly VITE_API_BASE_URL?: string
  /** "api" = intelligence endpoints from the backend; anything else = frontend mock (synthetic). */
  readonly VITE_INTELLIGENCE_SOURCE?: string
}
