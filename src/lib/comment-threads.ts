interface ThreadComment {
  id: string;
  parentCommentId: string | null;
  createdAt: Date | string;
}

export type CommentOrder = "newest" | "oldest";

export interface CommentThread<T> {
  root: T;
  replies: Array<{ comment: T; parent?: T }>;
}

/** Two visual levels, with the actual reply target retained at every depth. */
export function buildCommentThreads<T extends ThreadComment>(
  comments: T[],
  order: CommentOrder = "newest"
): CommentThread<T>[] {
  const byId = new Map(comments.map((comment) => [comment.id, comment]));
  const chronological = [...byId.values()].sort((a, b) =>
    new Date(a.createdAt).valueOf() - new Date(b.createdAt).valueOf()
      || a.id.localeCompare(b.id));
  const children = new Map<string, T[]>();
  for (const comment of chronological) {
    if (!comment.parentCommentId || !byId.has(comment.parentCommentId)) continue;
    const siblings = children.get(comment.parentCommentId) ?? [];
    siblings.push(comment);
    children.set(comment.parentCommentId, siblings);
  }

  const visited = new Set<string>();
  const threads: CommentThread<T>[] = [];
  const collect = (root: T) => {
    if (visited.has(root.id)) return;
    visited.add(root.id);
    const descendants: T[] = [];
    const stack = [...(children.get(root.id) ?? [])];
    while (stack.length) {
      const comment = stack.pop()!;
      if (visited.has(comment.id)) continue;
      visited.add(comment.id);
      descendants.push(comment);
      stack.push(...(children.get(comment.id) ?? []));
    }
    descendants.sort((a, b) => new Date(a.createdAt).valueOf() - new Date(b.createdAt).valueOf()
      || a.id.localeCompare(b.id));
    threads.push({
      root,
      replies: descendants.map((comment) => ({
        comment,
        parent: comment.parentCommentId ? byId.get(comment.parentCommentId) : undefined
      }))
    });
  };

  // A missing/hidden parent must not make an otherwise visible comment disappear.
  chronological.filter((comment) => !comment.parentCommentId || !byId.has(comment.parentCommentId))
    .forEach(collect);
  // Also keep malformed cyclic threads finite and visible.
  chronological.forEach(collect);
  return threads.sort((a, b) => {
    const difference = new Date(a.root.createdAt).valueOf() - new Date(b.root.createdAt).valueOf()
      || a.root.id.localeCompare(b.root.id);
    return order === "oldest" ? difference : -difference;
  });
}

export function commentAvatarTone(id: string): number {
  return Array.from(id).reduce((hash, letter) => (hash * 31 + letter.codePointAt(0)!) >>> 0, 0) % 5;
}
