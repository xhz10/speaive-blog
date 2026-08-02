import type { APIRoute } from "astro";

import { backendUrl } from "../../lib/public-api";

const VALID_MEDIA_PATH = /^[A-Za-z0-9/_-]+\.(?:avif|gif|jpe?g|png|webp)$/i;
const FORWARDED_HEADERS = [
  "cache-control",
  "content-length",
  "content-type",
  "etag",
  "last-modified"
];

export const GET: APIRoute = async ({ params }) => {
  const path = params.path;
  if (!path || !VALID_MEDIA_PATH.test(path)) {
    return new Response("Not found", { status: 404 });
  }

  try {
    const response = await fetch(backendUrl(`/media/${path}`), { cache: "no-store" });
    if (response.status === 404) return new Response("Not found", { status: 404 });
    if (!response.ok) return unavailable();

    const headers = new Headers({ "X-Content-Type-Options": "nosniff" });
    for (const name of FORWARDED_HEADERS) {
      const value = response.headers.get(name);
      if (value) headers.set(name, value);
    }
    return new Response(response.body, {
      status: response.status,
      headers
    });
  } catch {
    return unavailable();
  }
};

function unavailable(): Response {
  return new Response("Media service unavailable", {
    status: 502,
    headers: { "Cache-Control": "no-store" }
  });
}
