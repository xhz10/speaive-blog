const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";

export interface PublishedPostSummary {
  slug: string;
  title: string;
  description: string;
  publishedAt: Date;
  updatedAt: Date;
  tags: string[];
  cover?: string;
}

export interface PublishedPost extends PublishedPostSummary {
  body: string;
  html: string;
}

interface PostListResponse {
  items: PostSummaryResponse[];
}

interface PostSummaryResponse {
  slug: string;
  title: string;
  description: string;
  publishedAt: string;
  updatedAt: string;
  tags: string[];
  cover: string | null;
}

interface PostDetailResponse extends PostSummaryResponse {
  body: string;
  html: string;
}

export async function listPublishedPosts(): Promise<PublishedPostSummary[]> {
  const response = await request("/api/v1/public/posts");
  const payload = await response.json() as PostListResponse;
  return payload.items.map(toSummary);
}

export async function getPublishedPost(slug: string): Promise<PublishedPost | null> {
  const response = await request(`/api/v1/public/posts/${encodeURIComponent(slug)}`, true);
  if (response.status === 404) return null;

  const post = await response.json() as PostDetailResponse;
  return {
    ...toSummary(post),
    body: post.body,
    html: post.html
  };
}

export function backendUrl(path: string): URL {
  const base = process.env.SPEAIVE_BACKEND_URL ?? DEFAULT_BACKEND_URL;
  return new URL(path, base.endsWith("/") ? base : `${base}/`);
}

async function request(path: string, allowNotFound = false): Promise<Response> {
  const response = await fetch(backendUrl(path), {
    headers: { Accept: "application/json" },
    cache: "no-store"
  });
  if (!response.ok && !(allowNotFound && response.status === 404)) {
    throw new Error(`博客服务请求失败：${response.status}`);
  }
  return response;
}

function toSummary(post: PostSummaryResponse): PublishedPostSummary {
  return {
    slug: post.slug,
    title: post.title,
    description: post.description,
    publishedAt: parseDate(post.publishedAt),
    updatedAt: parseDate(post.updatedAt),
    tags: [...post.tags],
    cover: post.cover ?? undefined
  };
}

function parseDate(value: string): Date {
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) {
    throw new Error("博客服务返回了无效日期");
  }
  return date;
}
