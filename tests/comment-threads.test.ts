import { describe, expect, it } from "vitest";
import { buildCommentThreads } from "../src/lib/comment-threads";

const comment = (id: string, hour: number, parentCommentId: string | null = null) => ({
  id, parentCommentId, createdAt: new Date(Date.UTC(2026, 8, 5, hour)), body: id
});

describe("comment conversations", () => {
  it("sorts whole conversations while keeping interleaved replies chronological and their real targets intact", () => {
    const root = comment("root", 1);
    const firstReply = comment("first-reply", 2, "root");
    const sibling = comment("sibling", 3, "root");
    const nested = comment("nested", 4, "first-reply");
    const laterRoot = comment("later-root", 5);
    const input = [nested, laterRoot, sibling, firstReply, root];
    const threads = buildCommentThreads(input);
    expect(threads.map((thread) => thread.root.id)).toEqual(["later-root", "root"]);
    expect(threads[1].replies.map((entry) => entry.comment.id)).toEqual(["first-reply", "sibling", "nested"]);
    expect(threads[1].replies[2].parent).toBe(firstReply);
    expect(buildCommentThreads(input, "oldest").map((thread) => thread.root.id)).toEqual(["root", "later-root"]);
    expect(input[0]).toBe(nested);
  });

  it("preserves orphaned replies when a parent is missing, together with their descendants", () => {
    const orphan = comment("orphan", 1, "hidden-parent");
    const threads = buildCommentThreads([comment("child", 2, "orphan"), orphan]);
    expect(threads).toHaveLength(1);
    expect(threads[0].root.id).toBe("orphan");
    expect(threads[0].replies[0].parent).toBe(orphan);
  });

  it("does not duplicate or lose comments in cyclic or self-referential data", () => {
    const a = comment("a", 1, "b");
    const threads = buildCommentThreads([a, comment("b", 2, "a"), comment("self", 3, "self"), a]);
    const ids = threads.flatMap((thread) => [thread.root.id, ...thread.replies.map((entry) => entry.comment.id)]);
    expect(ids.sort()).toEqual(["a", "b", "self"]);
  });

  it("supports studio timestamps and an empty conversation without mutating the input", () => {
    expect(buildCommentThreads([])).toEqual([]);
    const input = [comment("a", 1), comment("b", 2)].map((item) => ({ ...item, createdAt: item.createdAt.toISOString() }));
    expect(buildCommentThreads(input).map((thread) => thread.root.id)).toEqual(["b", "a"]);
    expect(input.map((item) => item.id)).toEqual(["a", "b"]);
  });
});
