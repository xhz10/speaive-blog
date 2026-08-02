import { afterEach, describe, expect, it, vi } from "vitest";

import { GET } from "../src/pages/media/[...path]";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("media proxy", () => {
  it("keeps a real missing image as 404", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 404 })));

    const response = await invoke("2026/08/missing.png");

    expect(response.status).toBe(404);
  });

  it("reports backend failures without disguising them as missing media", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 503 })));

    const response = await invoke("2026/08/unavailable.png");

    expect(response.status).toBe(502);
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("streams successful media and preserves representation headers", async () => {
    const bytes = new Uint8Array([1, 2, 3]);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(bytes, {
      headers: {
        "Content-Type": "image/png",
        "Content-Length": "3",
        "ETag": "\"media-version\""
      }
    })));

    const response = await invoke("2026/08/image.png");

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("image/png");
    expect(response.headers.get("etag")).toBe("\"media-version\"");
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(bytes);
  });
});

async function invoke(path: string): Promise<Response> {
  return await GET({ params: { path } } as never) as Response;
}
