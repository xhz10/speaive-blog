/** 一次正文编辑：只替换选区附近的文字，同时明确编辑后光标的位置。 */
export interface MarkdownEdit {
  from: number;
  to: number;
  text: string;
  selectionStart: number;
  selectionEnd: number;
}

/** 手机排版工具支持的动作；不参与文章保存或发布。 */
export type MarkdownAction = "heading" | "bold" | "quote" | "list";

export function formatMarkdown(value: string, start: number, end: number, action: MarkdownAction): MarkdownEdit {
  if (action === "bold") {
    const selected = value.slice(start, end);
    if (start >= 2 && value.slice(start - 2, start) === "**" && value.slice(end, end + 2) === "**") {
      return { from: start - 2, to: end + 2, text: selected, selectionStart: start - 2, selectionEnd: end - 2 };
    }
    const text = selected || "加粗文字";
    return { from: start, to: end, text: `**${text}**`, selectionStart: start + 2, selectionEnd: start + 2 + text.length };
  }
  const from = start === 0 ? 0 : value.lastIndexOf("\n", start - 1) + 1;
  // 选区恰好结束在下一段的开头时，不修改下一段。
  const last = end > start && value[end - 1] === "\n" ? end - 1 : end;
  const newline = value.indexOf("\n", last);
  const to = newline < 0 ? value.length : newline;
  const lines = value.slice(from, to).split("\n");
  const prefix = action === "heading" ? "## " : action === "quote" ? "> " : "- ";
  const pattern = action === "heading" ? /^#{1,6} / : action === "quote" ? /^> / : /^- /;
  const remove = lines.every(line => line.startsWith(prefix));
  const text = lines.map(line => {
    const old = line.match(pattern)?.[0] ?? "";
    return (remove ? "" : prefix) + line.slice(old.length);
  }).join("\n");
  // 单个光标保持在当前段落的位置；多行选区继续覆盖原来选中的各段。
  const firstDelta = (remove ? 0 : prefix.length) - (lines[0]!.match(pattern)?.[0].length ?? 0);
  const caret = Math.max(from, Math.min(from + text.length, start + firstDelta));
  return { from, to, text, selectionStart: start === end ? caret : from, selectionEnd: start === end ? caret : from + text.length };
}

/** 只接受网页链接，避免把脚本协议和 Markdown 控制符带进正文。 */
export function markdownLink(label: string, address: string): string {
  let url: URL;
  try { url = new URL(address.trim()); } catch { throw new Error("请输入完整的网址，例如 https://example.com。"); }
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password) throw new Error("请使用不含账号密码的 http 或 https 网页链接。");
  const text = (label.trim() || url.hostname).replace(/[\r\n]+/g, " ").replace(/[\\[\]]/g, "\\$&");
  const href = url.href.replace(/\(/g, "%28").replace(/\)/g, "%29");
  return `[${text}](${href})`;
}
