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

export type CreativeContentType = "POST" | "NOVEL";
export type InspirationKind = "IDEA" | "SCENE" | "DIALOGUE" | "CHARACTER" | "QUESTION";
export type InspirationStatus = "INBOX" | "DEVELOPING" | "CONVERTED" | "ARCHIVED";

export interface StudioInspiration {
  id: string;
  title: string;
  body: string;
  kind: InspirationKind;
  status: InspirationStatus;
  pinned: boolean;
  targetType: CreativeContentType | null;
  targetSlug: string | null;
  revision: number;
  createdAt: string;
  updatedAt: string;
}

export interface StudioInspirationList {
  items: StudioInspiration[];
}

export interface ContentRevision {
  contentType: CreativeContentType;
  revision: number;
  eventType: "CREATE" | "IMPORT" | "UPDATE" | "PUBLISH" | "UNPUBLISH" | "ARCHIVE" | "RESTORE";
  slug: string;
  title: string;
  summary: string;
  body: string;
  publishedAt: string | null;
  tags: string[];
  cover: string | null;
  status: PostStatus | NovelFragmentStatus;
  visibility: PostVisibility | NovelFragmentVisibility;
  updatedAt: string;
  recordedAt: string;
}

export interface ContentRevisionList {
  items: ContentRevision[];
}

export interface WorkItem {
  contentType: CreativeContentType;
  contentSlug: string;
  position: number;
  title: string;
  summary: string;
  href: string;
  publishedAt: string | null;
}

export interface WorkCollection {
  slug: string;
  title: string;
  description: string;
  cover: string | null;
  visibility: "PUBLIC" | "ADMIN_ONLY";
  revision: number;
  updatedAt: string;
  items: WorkItem[];
}

export interface WorkCollectionList {
  items: WorkCollection[];
}

export interface WorkWritePayload {
  slug: string;
  title: string;
  description: string;
  cover: string | null;
  visibility: "PUBLIC" | "ADMIN_ONLY";
  items: Array<Pick<WorkItem, "contentType" | "contentSlug">>;
  revision?: number;
}

export interface ShareGrant {
  id: string;
  contentType: CreativeContentType;
  contentSlug: string;
  token: string | null;
  expiresAt: string;
  revokedAt: string | null;
  lastAccessedAt: string | null;
  createdAt: string;
  active: boolean;
}

export interface ShareGrantList {
  items: ShareGrant[];
}

export interface EditorialReview {
  id: string;
  postRevision: number;
  agentId: string;
  agentDisplayName: string;
  quoteText: string | null;
  quotePrefix: string | null;
  quoteSuffix: string | null;
  body: string;
  model: string | null;
  createdAt: string;
  stale: boolean;
}

export interface EditorialReviewList {
  items: EditorialReview[];
}

export interface DiscussionDigest {
  postSlug: string;
  body: string | null;
  model: string | null;
  state: "MISSING" | "STALE" | "CURRENT";
  commentCount: number;
  updatedAt: string | null;
}

export interface ResurfacingItem {
  kind: "THEME" | "UNFINISHED" | "OLD_POST";
  title: string;
  description: string;
  href: string;
  tags: string[];
  relatedSlugs: string[];
}

export interface ResurfacingResult {
  items: ResurfacingItem[];
}
