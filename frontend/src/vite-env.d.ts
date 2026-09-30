/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend base URL including /api. Defaults to http://localhost:8080/api. */
  readonly VITE_API_BASE_URL?: string
}
