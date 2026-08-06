import { afterEach, describe, expect, it, vi } from "vitest";

import { createDraftTabId } from "../src/lib/studio/draft-id";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("draft tab ID", () => {
  it("uses randomUUID when the browser provides it", () => {
    const randomUUID = vi.fn(() => "browser-uuid");
    const getRandomValues = vi.fn((values: Uint32Array) => values);

    expect(createDraftTabId({ randomUUID, getRandomValues })).toBe("browser-uuid");
    expect(getRandomValues).not.toHaveBeenCalled();
  });

  it("works on HTTP origins where randomUUID is unavailable", () => {
    const getRandomValues = vi.fn((values: Uint32Array) => {
      values.set([1, 2, 0xabcdef01, 0xffffffff]);
      return values;
    });

    expect(createDraftTabId({ getRandomValues }))
      .toBe("0000000100000002abcdef01ffffffff");
  });

  it("still creates an ID when Web Crypto fails", () => {
    vi.spyOn(Date, "now").mockReturnValue(1_000);
    vi.spyOn(Math, "random").mockReturnValue(0.5);

    expect(createDraftTabId({ randomUUID: () => { throw new Error("unavailable"); } }))
      .toBe("rs-i000000000");
  });
});
