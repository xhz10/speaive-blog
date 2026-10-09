import type { ApiErrorBody, CsrfToken } from "./types";

let csrfPromise: Promise<CsrfToken> | undefined;

export class StudioClientError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string
  ) {
    super(message);
    this.name = "StudioClientError";
  }
}

export async function studioRequest<T = void>(
  path: string,
  init: RequestInit = {},
  options: { redirectOnUnauthorized?: boolean; retryCsrf?: boolean } = {}
): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  const needsCsrf = !["GET", "HEAD", "OPTIONS"].includes(method);

  if (needsCsrf) {
    const csrf = await getCsrfToken();
    headers.set(csrf.headerName, csrf.token);
  }

  const response = await fetchResponse(`/api/v1${path}`, {
    ...init,
    method,
    headers,
    credentials: "same-origin",
    cache: "no-store"
  });

  if (response.status === 403 && needsCsrf && options.retryCsrf !== false) {
    csrfPromise = undefined;
    return studioRequest<T>(path, init, { ...options, retryCsrf: false });
  }

  if (!response.ok) {
    const error = await readError(response);
    if (response.status === 401 && options.redirectOnUnauthorized !== false) {
      const next = `${window.location.pathname}${window.location.search}`;
      window.location.assign(`/studio/login?next=${encodeURIComponent(next)}`);
    }
    throw new StudioClientError(response.status, error.code, error.message);
  }

  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

export function resetCsrfToken(): void {
  csrfPromise = undefined;
}

function getCsrfToken(): Promise<CsrfToken> {
  csrfPromise ??= fetchResponse("/api/v1/studio/csrf", {
    credentials: "same-origin",
    cache: "no-store",
    headers: { Accept: "application/json" }
  })
    .then(async (response) => {
      if (!response.ok) {
        const error = await readError(response);
        throw new StudioClientError(response.status, error.code, error.message);
      }
      return await response.json() as CsrfToken;
    })
    .catch((error) => {
      csrfPromise = undefined;
      throw error;
    });
  return csrfPromise;
}

/** 浏览器的断网错误没有 HTTP 状态码，转换为可展示的中文提示。
 * 保留其他异常；主动取消请求不按断网处理。
 */
async function fetchResponse(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(path, init);
  } catch (error) {
    if (error instanceof TypeError) {
      throw new StudioClientError(0, "NETWORK_ERROR", "暂时无法连接服务器，请检查网络后重试。");
    }
    throw error;
  }
}

async function readError(response: Response): Promise<ApiErrorBody> {
  try {
    const body = await response.json() as Partial<ApiErrorBody>;
    return {
      code: body.code ?? "REQUEST_FAILED",
      message: body.message ?? "请求失败"
    };
  } catch {
    return { code: "REQUEST_FAILED", message: "请求失败，请稍后再试" };
  }
}
