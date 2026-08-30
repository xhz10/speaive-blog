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

export type NovelFragmentStatus = "DRAFT" | "PUBLISHED";
export type NovelFragmentVisibility = "PUBLIC" | "ADMIN_ONLY";

export interface StudioNovelFragmentSummary {
  slug: string;
  title: string;
  excerpt: string;
  publishedAt: string | null;
  updatedAt: string;
  author: PostAuthor;
  status: NovelFragmentStatus;
  visibility: NovelFragmentVisibility;
  version: string;
}

export interface StudioNovelFragmentDetail extends StudioNovelFragmentSummary {
  body: string;
  html: string;
}

export interface StudioNovelFragmentList {
  items: StudioNovelFragmentSummary[];
}

export interface NovelFragmentWritePayload {
  title: string;
  excerpt: string;
  visibility: NovelFragmentVisibility;
  body: string;
}

export interface CreateNovelFragmentPayload extends NovelFragmentWritePayload {
  slug: string;
}

export interface UpdateNovelFragmentPayload extends NovelFragmentWritePayload {
  version: string;
}

export interface StudioAgent {
  id: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  ownerAccountId: string | null;
  systemPrompt: string;
  model: string | null;
  temperature: number;
  canProcessPrivate: boolean;
  enabled: boolean;
  enabledRequested: boolean;
  reviewStatus: "PENDING" | "APPROVED" | "REJECTED";
  reviewNote: string | null;
  reviewedAt: string | null;
  autoCommentEnabled: boolean;
  autoCommentAllPosts: boolean;
  autoCommentTags: string[];
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

export interface MemberSession {
  id: string;
  username: string;
  displayName: string;
  enabled: boolean;
  createdAt: string;
}

export interface CreateOwnedAgentPayload {
  username: string;
  displayName: string;
  avatarUrl: string | null;
  systemPrompt: string;
  temperature: number;
  autoCommentEnabled: boolean;
  autoCommentAllPosts: boolean;
  autoCommentTags: string[];
}

export interface UpdateOwnedAgentPayload {
  displayName: string;
  avatarUrl: string | null;
  systemPrompt: string;
  temperature: number;
  version: number;
}

export interface ConfigureOwnedAgentPayload {
  enabled: boolean;
  autoCommentEnabled: boolean;
  autoCommentAllPosts: boolean;
  autoCommentTags: string[];
  version: number;
}

export interface Invitation {
  id: string;
  code: string | null;
  maxUses: number;
  usedCount: number;
  expiresAt: string;
  createdAt: string;
}

export interface InvitationList {
  items: Invitation[];
}

export interface PostCommunityPolicy {
  postSlug: string;
  enabled: boolean;
  version: number;
  queuedAgents: number;
}

export type CommentStatus = "PENDING" | "PUBLISHED" | "HIDDEN";

export interface StudioComment {
  id: string;
  parentCommentId: string | null;
  author: PostAuthor;
  body: string;
  status: CommentStatus;
  createdAt: string;
  updatedAt: string;
}

export interface StudioCommentList {
  items: StudioComment[];
}

export type AiSummaryState = "MISSING" | "STALE" | "CURRENT";

export interface StudioPostAiSummary {
  postSlug: string;
  postRevision: number;
  body: string | null;
  model: string | null;
  state: AiSummaryState;
  generatedAt: string | null;
}

export interface AiSummaryCoverage {
  total: number;
  current: number;
  missing: number;
  stale: number;
  generatedSlug: string | null;
}
