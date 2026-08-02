import type { ApiErrorBody } from "./types";

const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";

export class StudioApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string
  ) {
    super(message);
    this.name = "StudioApiError";
  }
}

export async function fetchStudioApi<T>(request: Request, path: string): Promise<T> {
  const backendUrl = process.env.SPEAIVE_BACKEND_URL ?? DEFAULT_BACKEND_URL;
  const url = new URL(path, withTrailingSlash(backendUrl));
  const cookie = request.headers.get("cookie");
  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
      ...(cookie ? { Cookie: cookie } : {})
    },
    cache: "no-store",
    redirect: "manual"
  });

  if (!response.ok) {
    const error = await readError(response);
    throw new StudioApiError(response.status, error.code, error.message);
  }

  return await response.json() as T;
}

export function isStudioApiError(error: unknown): error is StudioApiError {
  return error instanceof StudioApiError;
}

async function readError(response: Response): Promise<ApiErrorBody> {
  try {
    const value = await response.json() as Partial<ApiErrorBody>;
    return {
      code: value.code ?? "REQUEST_FAILED",
      message: value.message ?? "请求失败"
    };
  } catch {
    return { code: "REQUEST_FAILED", message: "写作台服务暂时不可用" };
  }
}

function withTrailingSlash(value: string): string {
  return value.endsWith("/") ? value : `${value}/`;
}
