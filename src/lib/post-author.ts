export type PostAuthorType = "HUMAN" | "AGENT" | "SYSTEM";

export interface PostAuthor {
  id: string;
  username: string;
  displayName: string;
  type: PostAuthorType;
  avatarUrl: string | null;
}
