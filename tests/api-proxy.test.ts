import { afterEach, describe, expect, it, vi } from "vitest";

import { ALL } from "../src/pages/api/v1/[...path]";

afterEach(() => {
  vi.unstubAllGlobals();
  delete process.env.SPEAIVE_TRUST_PROXY_HEADERS;
});

describe("Astro API boundary", () => {
  it("drops all forwarding headers unless the host proxy is explicitly trusted", async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ items: [], errors: [] }));
    vi.stubGlobal("fetch", fetchMock);
    const request = new Request("http://blog.test/api/v1/public/posts", {
      headers: {
        "Connection": "X-Remove-Me",
        "X-Forwarded-For": "198.51.100.200",
        "X-Forwarded-Prefix": "/forged-prefix",
        "X-Real-IP": "198.51.100.201",
        "X-Remove-Me": "must-not-reach-backend"
      }
    });

    const response = await invoke(request, "public/posts", "203.0.113.10");

    expect(response.status).toBe(200);
    const headers = fetchMock.mock.calls[0][1].headers as Headers;
    expect(headers.has("x-forwarded-for")).toBe(false);
    expect(headers.has("x-forwarded-prefix")).toBe(false);
    expect(headers.has("x-real-ip")).toBe(false);
    expect(headers.has("x-remove-me")).toBe(false);
  });

  it("accepts one validated client address from the trusted host proxy", async () => {
    process.env.SPEAIVE_TRUST_PROXY_HEADERS = "true";
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ username: "admin" }));
    vi.stubGlobal("fetch", fetchMock);
    const request = new Request("http://blog.test/api/v1/studio/session", {
      headers: {
        "X-Real-IP": "198.51.100.20",
        "X-Forwarded-For": "198.51.100.21, 10.0.0.1"
      }
    });

    await invoke(request, "studio/session", "172.18.0.1");

    const headers = fetchMock.mock.calls[0][1].headers as Headers;
    expect(headers.get("x-forwarded-for")).toBe("198.51.100.20");
  });

  it.each([
    "../actuator/health",
    "studio/./session",
    "studio//session",
    "studio\\..\\actuator/health",
    "%2e%2e/%2e%2e/actuator/health",
    "studio%2fsession"
  ])("rejects a path that could escape the API prefix: %s", async (path) => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const response = await invoke(
      new Request("http://blog.test/api/v1/public/posts"),
      path,
      "203.0.113.10"
    );

    expect(response.status).toBe(404);
    await expect(response.json()).resolves.toMatchObject({ code: "NOT_FOUND" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("keeps a unicode slug inside the API prefix", async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ slug: "中文-随笔" }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await invoke(
      new Request("http://blog.test/api/v1/public/posts/%E4%B8%AD%E6%96%87-%E9%9A%8F%E7%AC%94"),
      "public/posts/中文-随笔",
      "203.0.113.10"
    );

    expect(response.status).toBe(200);
    const target = fetchMock.mock.calls[0][0] as URL;
    expect(target.pathname).toBe("/api/v1/public/posts/%E4%B8%AD%E6%96%87-%E9%9A%8F%E7%AC%94");
  });

  it("rejects a declared body larger than nine MiB before forwarding", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const request = new Request("http://blog.test/api/v1/studio/import", {
      method: "POST",
      headers: { "Content-Length": String(9 * 1024 * 1024 + 1) },
      body: "x"
    });

    const response = await invoke(request, "studio/import", "203.0.113.10");

    expect(response.status).toBe(413);
    await expect(response.json()).resolves.toMatchObject({ code: "TOO_LARGE" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("stops a chunked body after nine MiB even without Content-Length", async () => {
    const fetchMock = vi.fn(async (_url: URL, init: RequestInit) => {
      const reader = (init.body as ReadableStream<Uint8Array>).getReader();
      while (!(await reader.read()).done) {
        // Consume the stream just like the backend connection would.
      }
      return Response.json({});
    });
    vi.stubGlobal("fetch", fetchMock);
    const request = new Request("http://blog.test/api/v1/studio/import", {
      method: "POST",
      body: chunkedBody(10, 1024 * 1024),
      duplex: "half"
    } as RequestInit & { duplex: "half" });

    const response = await invoke(request, "studio/import", "203.0.113.10");

    expect(response.status).toBe(413);
    await expect(response.json()).resolves.toMatchObject({ code: "TOO_LARGE" });
  });

  it("preserves multiple Set-Cookie headers from Spring Boot", async () => {
    const backendHeaders = new Headers();
    backendHeaders.append("Set-Cookie", "SPEAIVE_SESSION=session; Path=/; HttpOnly");
    backendHeaders.append("Set-Cookie", "XSRF-TOKEN=csrf; Path=/; SameSite=Lax");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", {
      status: 200,
      headers: backendHeaders
    })));

    const response = await invoke(
      new Request("http://blog.test/api/v1/studio/login"),
      "studio/login",
      "203.0.113.10"
    );

    expect(response.headers.getSetCookie()).toEqual([
      "SPEAIVE_SESSION=session; Path=/; HttpOnly",
      "XSRF-TOKEN=csrf; Path=/; SameSite=Lax"
    ]);
  });
});

async function invoke(request: Request, path: string, clientAddress: string): Promise<Response> {
  return await ALL({
    request,
    params: { path },
    url: new URL(request.url),
    clientAddress
  } as never) as Response;
}

function chunkedBody(chunks: number, chunkSize: number): ReadableStream<Uint8Array> {
  let remaining = chunks;
  return new ReadableStream({
    pull(controller) {
      if (remaining-- > 0) {
        controller.enqueue(new Uint8Array(chunkSize));
      } else {
        controller.close();
      }
    }
  });
}
