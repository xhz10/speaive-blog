import { describe, expect, it } from "vitest";

import { buildTagArchive } from "../src/lib/tag-archive";

const posts = [
  { slug: "one", tags: ["随笔", "Astro"] },
  { slug: "two", tags: ["astro", "写作"] },
  { slug: "three", tags: ["随笔", "随笔"] }
];

describe("tag archive", () => {
  it("builds case-insensitive tag counts without double-counting one post", () => {
    const archive = buildTagArchive(posts);

    expect(archive.options).toEqual([
      { name: "随笔", count: 2 },
      { name: "写作", count: 1 },
      { name: "Astro", count: 2 }
    ]);
    expect(archive.items).toHaveLength(3);
  });

  it("resolves and filters a requested tag without caring about letter case", () => {
    const archive = buildTagArchive(posts, "ASTRO");

    expect(archive.selectedTag).toBe("Astro");
    expect(archive.items.map((post) => post.slug)).toEqual(["one", "two"]);
  });

  it("returns an empty result for an unknown tag while keeping the tag name", () => {
    const archive = buildTagArchive(posts, "不存在");

    expect(archive.selectedTag).toBe("不存在");
    expect(archive.items).toEqual([]);
  });
});
