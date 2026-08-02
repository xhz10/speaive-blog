import type { APIRoute } from "astro";

import { backendUrl } from "../../lib/public-api";

export const GET: APIRoute = async () => {
  try {
    const response = await fetch(backendUrl("/actuator/health"), {
      headers: { Accept: "application/json" },
      cache: "no-store"
    });
    if (!response.ok) throw new Error("backend unavailable");
    return Response.json({ status: "ok" }, {
      headers: { "Cache-Control": "no-store" }
    });
  } catch {
    return Response.json({ status: "error" }, {
      status: 503,
      headers: { "Cache-Control": "no-store" }
    });
  }
};
