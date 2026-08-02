export type PostStatus = "DRAFT" | "PUBLISHED";

export interface StudioPostSummary {
  slug: string;
  title: string;
  description: string;
  publishedAt: string;
  updatedAt: string;
  tags: string[];
  cover: string | null;
  status: PostStatus;
  version: string;
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
  body: string;
}

export interface CreatePostPayload extends PostWritePayload {
  slug: string;
}

export interface UpdatePostPayload extends PostWritePayload {
  version: string;
}
