export interface TaggedItem {
  tags: readonly string[];
}

export interface TagOption {
  name: string;
  count: number;
}

export interface TagArchive<T> {
  items: T[];
  selectedTag: string;
  options: TagOption[];
}

export function buildTagArchive<T extends TaggedItem>(items: readonly T[], requestedTag = ""): TagArchive<T> {
  const optionsByKey = new Map<string, TagOption>();

  for (const item of items) {
    const itemKeys = new Set<string>();
    for (const rawTag of item.tags) {
      const name = rawTag.trim();
      const key = normalizeTagKey(name);
      if (!key || itemKeys.has(key)) continue;
      itemKeys.add(key);

      const existing = optionsByKey.get(key);
      if (existing) existing.count += 1;
      else optionsByKey.set(key, { name, count: 1 });
    }
  }

  const requested = requestedTag.trim();
  const selectedTag = optionsByKey.get(normalizeTagKey(requested))?.name ?? requested;
  const selectedKey = normalizeTagKey(selectedTag);
  const filteredItems = selectedKey
    ? items.filter((item) => item.tags.some((tag) => normalizeTagKey(tag) === selectedKey))
    : items;

  return {
    items: [...filteredItems],
    selectedTag,
    options: [...optionsByKey.values()].sort((left, right) => left.name.localeCompare(right.name, "zh-CN"))
  };
}

function normalizeTagKey(value: string): string {
  return value.trim().toLocaleLowerCase("zh-CN");
}

