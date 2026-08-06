interface DraftIdCrypto {
  randomUUID?: () => string;
  getRandomValues?: (values: Uint32Array<ArrayBuffer>) => unknown;
}

export function createDraftTabId(cryptoSource: DraftIdCrypto | undefined = globalThis.crypto): string {
  try {
    if (typeof cryptoSource?.randomUUID === "function") {
      return cryptoSource.randomUUID();
    }

    if (typeof cryptoSource?.getRandomValues === "function") {
      const values = new Uint32Array(4);
      cryptoSource.getRandomValues(values);
      return Array.from(values, (value) => value.toString(16).padStart(8, "0")).join("");
    }
  } catch {
    // This ID only separates local drafts between tabs; it is not a security token.
  }

  const time = Date.now().toString(36);
  const random = Math.random().toString(36).slice(2, 12).padEnd(10, "0");
  return `${time}-${random}`;
}
