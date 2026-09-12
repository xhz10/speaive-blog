import { describe, expect, it } from "vitest";
import { formatMarkdown, markdownLink, type MarkdownEdit } from "../src/lib/writing/markdown";

const apply = (value: string, edit: MarkdownEdit) => value.slice(0, edit.from) + edit.text + value.slice(edit.to);

describe("手机正文排版", () => {
  it("对中文和 emoji 选区加粗，再次点击去掉标记，保留两边文字", () => {
    const value = "今天的🌙很好看";
    const start = value.indexOf("🌙"), end = start + "🌙很好看".length;
    const edit = formatMarkdown(value, start, end, "bold");
    const bold = apply(value, edit);
    expect(bold).toBe("今天的**🌙很好看**");
    expect(bold.slice(edit.selectionStart, edit.selectionEnd)).toBe("🌙很好看");
    expect(apply(bold, formatMarkdown(bold, edit.selectionStart, edit.selectionEnd, "bold"))).toBe(value);
  });
  it("没有选区时选中占位文字，直接打字即可替换", () => {
    const edit = formatMarkdown("", 0, 0, "bold");
    expect(edit.text.slice(edit.selectionStart, edit.selectionEnd)).toBe("加粗文字");
  });
  it("跨段列表不包含选区结尾的下一段，重复操作可取消", () => {
    const value = "散步\n晚饭\n不选这一段";
    const edit = formatMarkdown(value, 0, 6, "list");
    const listed = apply(value, edit);
    expect(listed).toBe("- 散步\n- 晚饭\n不选这一段");
    expect(apply(listed, formatMarkdown(listed, edit.selectionStart, edit.selectionEnd, "list"))).toBe(value);
  });
  it("混合已有和未有列表的选区，不重复添加标记", () => {
    const value = "- 散步\n晚饭";
    expect(apply(value, formatMarkdown(value, 0, value.length, "list"))).toBe("- 散步\n- 晚饭");
  });
  it("替换既有标题级别，并保持段落中间的光标位置", () => {
    const value = "# 周末安排";
    const edit = formatMarkdown(value, 4, 4, "heading");
    expect(apply(value, edit)).toBe("## 周末安排");
    expect(edit.selectionStart).toBe(5);
    expect(edit.selectionEnd).toBe(5);
  });
  it("空段落插入引用后光标落在标记后", () => {
    const edit = formatMarkdown("前文\n", 3, 3, "quote");
    expect(apply("前文\n", edit)).toBe("前文\n> ");
    expect(edit.selectionStart).toBe(5);
  });
  it("全文第一行为空时也能插入小标题", () => {
    const edit = formatMarkdown("\n下一段", 0, 0, "heading");
    expect(apply("\n下一段", edit)).toBe("## \n下一段");
  });
});

describe("插入网页链接", () => {
  it("转义链接文字和网址括号，避免切断 Markdown 结构", () => {
    expect(markdownLink("例子 [一]", "https://example.com/a(b)")).toBe("[例子 \\[一\\]](https://example.com/a%28b%29)");
  });
  it("没有名称时使用网站名", () => {
    expect(markdownLink("", " https://example.com ")).toBe("[example.com](https://example.com/)");
  });
  it.each(["javascript:alert(1)", "data:text/html,hello", "file:///etc/passwd", "//example.com", "example.com", "https://user:pass@example.com"])("拒绝无效或不合适的网址 %s", url => {
    expect(() => markdownLink("链接", url)).toThrow();
  });
});
