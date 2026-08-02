/// <reference path="../.astro/types.d.ts" />
/// <reference types="astro/client" />

declare global {
  interface ImportMetaEnv {
    readonly SPEAIVE_SITE_URL?: string;
    readonly SPEAIVE_BACKEND_URL?: string;
    readonly SPEAIVE_TRUST_PROXY_HEADERS?: string;
  }

  interface ImportMeta {
    readonly env: ImportMetaEnv;
  }

}

export {};
