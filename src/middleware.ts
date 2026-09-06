import { defineMiddleware } from "astro:middleware";

import { fetchStudioApi, isStudioApiError } from "./lib/studio/server";
import type { StudioSession } from "./lib/studio/types";

export const onRequest = defineMiddleware(async (context, next) => {
  const pathname = normalizePathname(context.url.pathname);
  const isStudioPage = pathname === "/studio" || pathname.startsWith("/studio/");

  const isWritingPage = pathname === "/writing" || pathname.startsWith("/writing/");
  if (isWritingPage) {
    try { await fetchStudioApi(context.request, "/api/v1/account/session"); }
    catch (error) {
      if (isStudioApiError(error) && (error.status === 401 || error.status === 403)) {
        const login = new URL("/agents/login/", context.url);
        login.searchParams.set("next", `${pathname}${context.url.search}`);
        return context.redirect(login.toString(), 303);
      }
      return new Response("会员写作服务暂时不可用", { status: 503, headers: { "Cache-Control": "no-store" } });
    }
    const response = await next();
    response.headers.set("Cache-Control", "no-store");
    response.headers.set("X-Frame-Options", "DENY");
    response.headers.set("X-Content-Type-Options", "nosniff");
    response.headers.set("Referrer-Policy", "same-origin");
    return response;
  }
  if (!isStudioPage) return next();

  if (isStudioPage && pathname !== "/studio/login") {
    try {
      await fetchStudioApi<StudioSession>(context.request, "/api/v1/studio/session");
    } catch (error) {
      if (isStudioApiError(error) && error.status === 401) {
        const loginUrl = new URL("/studio/login", context.url);
        loginUrl.searchParams.set("next", `${pathname}${context.url.search}`);
        return context.redirect(loginUrl.toString(), 303);
      }
      return new Response("写作台服务暂时不可用", {
        status: 503,
        headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" }
      });
    }
  }

  const response = await next();
  response.headers.set("Cache-Control", "no-store");
  response.headers.set("X-Frame-Options", "DENY");
  response.headers.set("X-Content-Type-Options", "nosniff");
  return response;
});

function normalizePathname(pathname: string): string {
  return pathname.length > 1 ? pathname.replace(/\/+$/, "") : pathname;
}
