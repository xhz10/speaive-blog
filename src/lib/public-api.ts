import type { PostAuthor } from "./post-author";

const DEFAULT_BACKEND_URL = "http://127.0.0.1:8080";

export interface PublishedPostSummary {
  slug: string;
  title: string;
  description: string;
  publishedAt: Date;
  updatedAt: Date;
  tags: string[];
  cover?: string;
  author: PostAuthor;
}

export interface PublishedPost extends PublishedPostSummary {
  body: string;
  html: string;
}

export interface PublishedComment {
  id: string;
  parentCommentId: string | null;
  author: PostAuthor;
  body: string;
  createdAt: Date;
}

export interface PublishedNovelFragmentSummary {
  slug: string;
  title: string;
  excerpt: string;
  publishedAt: Date;
  updatedAt: Date;
  author: PostAuthor;
}

export interface PublishedNovelFragment extends PublishedNovelFragmentSummary {
  body: string;
  html: string;
}

export interface PublishedWorkItem {
  contentType: "POST" | "NOVEL";
  contentSlug: string;
  position: number;
  title: string;
  summary: string;
  href: string;
  publishedAt: Date | null;
}

export interface PublishedWork {
  slug: string;
  title: string;
  description: string;
  cover?: string;
  updatedAt: Date;
  items: PublishedWorkItem[];
}

export interface PublishedWorkNavigation {
  workSlug: string;
  workTitle: string;
  previous: { contentType: "POST" | "NOVEL"; contentSlug: string; title: string; href: string } | null;
  next: { contentType: "POST" | "NOVEL"; contentSlug: string; title: string; href: string } | null;
}

export interface PublishedDiscussionDigest {
  body: string | null;
  state: "MISSING" | "STALE" | "CURRENT";
  commentCount: number;
  updatedAt: Date | null;
}

export interface SharedContent {
  contentType: "POST" | "NOVEL";
  slug: string;
  title: string;
  summary: string;
  body: string;
  html: string;
  publishedAt: Date | null;
  updatedAt: Date;
  authorDisplayName: string;
  expiresAt: Date;
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
  author: PostAuthor;
}

interface PostDetailResponse extends PostSummaryResponse {
  body: string;
  html: string;
}

interface CommentListResponse {
  items: CommentResponse[];
}

interface NovelFragmentListResponse {
  items: NovelFragmentResponse[];
}

interface NovelFragmentResponse {
  slug: string;
  title: string;
  excerpt: string;
  publishedAt: string;
  updatedAt: string;
  author: PostAuthor;
  body?: string;
  html?: string;
}

interface CommentResponse {
  id: string;
  parentCommentId: string | null;
  author: PostAuthor;
  body: string;
  createdAt: string;
}

interface WorkResponse {
  slug: string;
  title: string;
  description: string;
  cover: string | null;
  updatedAt: string;
  items: Array<{
    contentType: "POST" | "NOVEL";
    contentSlug: string;
    position: number;
    title: string;
    summary: string;
    href: string;
    publishedAt: string | null;
  }>;
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

export async function listPublishedComments(slug: string): Promise<PublishedComment[]> {
  const response = await request(`/api/v1/public/posts/${encodeURIComponent(slug)}/comments`, true);
  if (response.status === 404) return [];
  const payload = await response.json() as CommentListResponse;
  return payload.items.map((comment) => ({
    id: comment.id,
    parentCommentId: comment.parentCommentId,
    author: { ...comment.author },
    body: comment.body,
    createdAt: parseDate(comment.createdAt)
  }));
}

export async function listPublishedNovelFragments(): Promise<PublishedNovelFragmentSummary[]> {
  const response = await request("/api/v1/public/novels");
  const payload = await response.json() as NovelFragmentListResponse;
  return payload.items.map(toNovelFragmentSummary);
}

export async function getPublishedNovelFragment(slug: string): Promise<PublishedNovelFragment | null> {
  const response = await request(`/api/v1/public/novels/${encodeURIComponent(slug)}`, true);
  if (response.status === 404) return null;
  const fragment = await response.json() as NovelFragmentResponse;
  return {
    ...toNovelFragmentSummary(fragment),
    body: fragment.body ?? "",
    html: fragment.html ?? ""
  };
}

export async function listPublishedWorks(): Promise<PublishedWork[]> {
  const response = await request("/api/v1/public/works");
  const payload = await response.json() as { items: WorkResponse[] };
  return payload.items.map(toWork);
}

export async function getPublishedWork(slug: string): Promise<PublishedWork | null> {
  const response = await request(`/api/v1/public/works/${encodeURIComponent(slug)}`, true);
  if (response.status === 404) return null;
  return toWork(await response.json() as WorkResponse);
}

export async function getPublishedWorkNavigation(
  contentType: "POST" | "NOVEL",
  contentSlug: string,
  workSlug?: string
): Promise<PublishedWorkNavigation | null> {
  const query = new URLSearchParams({ contentType, contentSlug });
  if (workSlug) query.set("workSlug", workSlug);
  const response = await request(
    `/api/v1/public/works/navigation?${query.toString()}`
  );
  if (response.status === 204) return null;
  return await response.json() as PublishedWorkNavigation;
}

export async function getPublishedDiscussionDigest(slug: string): Promise<PublishedDiscussionDigest | null> {
  const response = await request(`/api/v1/public/posts/${encodeURIComponent(slug)}/discussion-digest`, true);
  if (response.status === 404) return null;
  const value = await response.json() as { body: string | null; state: PublishedDiscussionDigest["state"]; commentCount: number; updatedAt: string | null };
  return { ...value, updatedAt: value.updatedAt ? parseDate(value.updatedAt) : null };
}

export async function getSharedContent(token: string): Promise<SharedContent | null> {
  const response = await request(`/api/v1/public/shares/${encodeURIComponent(token)}`, true);
  if (response.status === 404) return null;
  const value = await response.json() as {
    contentType: "POST" | "NOVEL"; slug: string; title: string; summary: string; body: string; html: string;
    publishedAt: string | null; updatedAt: string; authorDisplayName: string; expiresAt: string;
  };
  return {
    ...value,
    publishedAt: value.publishedAt ? parseDate(value.publishedAt) : null,
    updatedAt: parseDate(value.updatedAt),
    expiresAt: parseDate(value.expiresAt)
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
    cover: post.cover ?? undefined,
    author: { ...post.author }
  };
}

function toNovelFragmentSummary(fragment: NovelFragmentResponse): PublishedNovelFragmentSummary {
  return {
    slug: fragment.slug,
    title: fragment.title,
    excerpt: fragment.excerpt,
    publishedAt: parseDate(fragment.publishedAt),
    updatedAt: parseDate(fragment.updatedAt),
    author: { ...fragment.author }
  };
}

function toWork(work: WorkResponse): PublishedWork {
  return {
    slug: work.slug,
    title: work.title,
    description: work.description,
    cover: work.cover ?? undefined,
    updatedAt: parseDate(work.updatedAt),
    items: work.items.map((item) => ({
      ...item,
      publishedAt: item.publishedAt ? parseDate(item.publishedAt) : null
    }))
  };
}

function parseDate(value: string): Date {
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) {
    throw new Error("博客服务返回了无效日期");
  }
  return date;
}
