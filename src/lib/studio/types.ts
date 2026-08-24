import type { PostAuthor } from "../post-author";

export type PostStatus = "DRAFT" | "PUBLISHED";
export type PostVisibility = "PUBLIC" | "ADMIN_ONLY";

export interface StudioPostSummary {
  slug: string;
  title: string;
  description: string;
  publishedAt: string;
  updatedAt: string;
  tags: string[];
  cover: string | null;
  visibility: PostVisibility;
  status: PostStatus;
  version: string;
  author: PostAuthor;
}

export interface StudioPostDetail extends StudioPostSummary {
  body: string;
  html: string;
}

export interface ContentScanError {
  file: string;
  status: PostStatus;
  message: string;
}

export interface StudioPostList {
  items: StudioPostSummary[];
  errors: ContentScanError[];
}

export interface StudioSession {
  username: string;
}

export interface CsrfToken {
  token: string;
  headerName: string;
  parameterName: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
}

export interface SavedMedia {
  url: string;
  relativePath: string;
  mimeType: string;
  size: number;
}

export interface PostWritePayload {
  title: string;
  description: string;
  publishedAt: string;
  tags: string[];
  cover: string | null;
  visibility: PostVisibility;
  body: string;
}

export interface CreatePostPayload extends PostWritePayload {
  slug: string;
}

export interface UpdatePostPayload extends PostWritePayload {
  version: string;
}

export interface StudioAgent {
  id: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  systemPrompt: string;
  model: string | null;
  temperature: number;
  canProcessPrivate: boolean;
  enabled: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface StudioAgentList {
  items: StudioAgent[];
  aiAvailable: boolean;
}

export interface AgentWritePayload {
  displayName: string;
  avatarUrl: string | null;
  systemPrompt: string;
  model: string | null;
  temperature: number;
  canProcessPrivate: boolean;
  enabled: boolean;
}

export interface CreateAgentPayload extends AgentWritePayload {
  username: string;
}

export interface UpdateAgentPayload extends AgentWritePayload {
  version: number;
}

export type CommentStatus = "PENDING" | "PUBLISHED" | "HIDDEN";

export interface StudioComment {
  id: string;
  author: PostAuthor;
  body: string;
  status: CommentStatus;
  createdAt: string;
  updatedAt: string;
}

export interface StudioCommentList {
  items: StudioComment[];
}
