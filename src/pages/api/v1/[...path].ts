import { isIP } from "node:net";
import type { APIRoute } from "astro";

const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";
const BODYLESS_METHODS = new Set(["GET", "HEAD"]);
const MAX_REQUEST_BYTES = 9 * 1024 * 1024;
const HOP_BY_HOP_HEADERS = [
  "connection",
  "content-length",
  "host",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade"
];

export const ALL: APIRoute = async ({ request, params, url, clientAddress }) => {
  const path = params.path;
  if (!path) return apiError("NOT_FOUND", "接口不存在", 404);

  const proxyPath = normalizeProxyPath(path);
  if (!proxyPath) return apiError("NOT_FOUND", "接口不存在", 404);

  try {
    const contentLength = Number(request.headers.get("content-length") ?? "0");
    if (Number.isFinite(contentLength) && contentLength > MAX_REQUEST_BYTES) {
      return apiError("TOO_LARGE", "上传文件超过大小限制", 413);
    }

    const backendUrl = new URL(process.env.SPEAIVE_BACKEND_URL ?? DEFAULT_BACKEND_URL);
    const target = new URL(`/api/v1/${proxyPath}`, backendUrl);
    target.search = url.search;

    const forwardedClientAddress = resolveClientAddress(request, clientAddress);
    const headers = new Headers(request.headers);
    stripHopByHopHeaders(headers);
    stripForwardingHeaders(headers);
    if (forwardedClientAddress) headers.set("x-forwarded-for", forwardedClientAddress);
    headers.set("accept-encoding", "identity");

    const init: RequestInit & { duplex?: "half" } = {
      method: request.method,
      headers,
      body: BODYLESS_METHODS.has(request.method)
        ? undefined
        : limitedBody(request.body),
      redirect: "manual",
      duplex: "half"
    };
    const response = await fetch(target, init);

    const responseHeaders = copyResponseHeaders(response.headers);
    responseHeaders.delete("content-length");
    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders
    });
  } catch (error) {
    if (isPayloadTooLarge(error)) {
      return apiError("TOO_LARGE", "上传文件超过大小限制", 413);
    }
    return apiError("BACKEND_UNAVAILABLE", "写作台服务暂时不可用", 502);
  }
};

function normalizeProxyPath(path: string): string | null {
  const segments = path.split("/");
  if (segments.some((segment) => segment.length === 0)) return null;

  const normalized: string[] = [];
  for (const rawSegment of segments) {
    let segment: string;
    try {
      segment = decodeURIComponent(rawSegment);
    } catch {
      return null;
    }
    if (
      segment === "."
      || segment === ".."
      || segment.includes("/")
      || segment.includes("\\")
      || /[\u0000-\u001f\u007f]/u.test(segment)
    ) {
      return null;
    }
    normalized.push(encodeURIComponent(segment));
  }
  return normalized.join("/");
}

class PayloadTooLargeError extends Error {}

function limitedBody(body: ReadableStream<Uint8Array> | null): ReadableStream<Uint8Array> | undefined {
  if (!body) return undefined;

  let received = 0;
  return body.pipeThrough(new TransformStream<Uint8Array, Uint8Array>({
    transform(chunk, controller) {
      received += chunk.byteLength;
      if (received > MAX_REQUEST_BYTES) {
        controller.error(new PayloadTooLargeError());
        return;
      }
      controller.enqueue(chunk);
    }
  }));
}

function isPayloadTooLarge(error: unknown): boolean {
  let current = error;
  while (current instanceof Error) {
    if (current instanceof PayloadTooLargeError) return true;
    current = current.cause;
  }
  return false;
}

function resolveClientAddress(request: Request, directAddress: string | undefined): string | null {
  if (process.env.SPEAIVE_TRUST_PROXY_HEADERS !== "true") return null;
  const candidates: Array<string | null | undefined> = [
    request.headers.get("x-real-ip"),
    request.headers.get("x-forwarded-for")?.split(",", 1)[0],
    directAddress
  ];

  for (const candidate of candidates) {
    const normalized = normalizeIp(candidate);
    if (normalized) return normalized;
  }
  return null;
}

function normalizeIp(input: string | null | undefined): string | null {
  if (!input) return null;
  let value = input.trim();
  if (value.startsWith("::ffff:")) value = value.slice(7);
  return isIP(value) ? value : null;
}

function copyResponseHeaders(source: Headers): Headers {
  const getSetCookie = (source as Headers & { getSetCookie?: () => string[] }).getSetCookie;
  const cookies = getSetCookie?.call(source) ?? [];
  const headers = new Headers(source);
  stripHopByHopHeaders(headers);
  if (cookies.length > 0) {
    headers.delete("set-cookie");
    for (const cookie of cookies) headers.append("set-cookie", cookie);
  }
  return headers;
}

function stripHopByHopHeaders(headers: Headers): void {
  const connectionTokens = (headers.get("connection") ?? "")
    .split(",")
    .map((value) => value.trim().toLowerCase())
    .filter(Boolean);
  for (const name of [...HOP_BY_HOP_HEADERS, ...connectionTokens]) headers.delete(name);
}

function stripForwardingHeaders(headers: Headers): void {
  for (const name of [...headers.keys()]) {
    if (name === "forwarded" || name === "x-real-ip" || name.startsWith("x-forwarded-")) {
      headers.delete(name);
    }
  }
}

function apiError(code: string, message: string, status: number): Response {
  return Response.json(
    { code, message },
    { status, headers: { "Cache-Control": "no-store" } }
  );
}
