import { defaultKeymap, history, historyKeymap, indentWithTab } from "@codemirror/commands";
import {
  defaultHighlightStyle,
  foldGutter,
  foldKeymap,
  indentOnInput,
  syntaxHighlighting
} from "@codemirror/language";
import { highlightSelectionMatches, searchKeymap } from "@codemirror/search";
import { EditorSelection, EditorState } from "@codemirror/state";
import {
  drawSelection,
  dropCursor,
  EditorView,
  highlightActiveLine,
  highlightActiveLineGutter,
  highlightSpecialChars,
  keymap,
  lineNumbers,
  placeholder as editorPlaceholder
} from "@codemirror/view";

export async function createMarkdownEditor(
  host: HTMLElement,
  source: HTMLTextAreaElement,
  onChange: (value: string) => void,
  placeholder = "从这里开始写……"
): Promise<EditorView> {
  const { markdown } = await import("@codemirror/lang-markdown");
  const writerTheme = EditorView.theme({
    "&": {
      backgroundColor: "#ffffff",
      color: "#252723",
      fontSize: "0.9rem"
    },
    "&.cm-focused": { outline: "none" },
    ".cm-scroller": {
      fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
      lineHeight: "1.8"
    },
    ".cm-content": { padding: "22px 0", caretColor: "#c83e2c" },
    ".cm-line": { padding: "0 22px" },
    ".cm-gutters": {
      border: "0",
      borderRight: "1px solid #ecebe6",
      backgroundColor: "#fbfbf8",
      color: "#aaa9a2",
      paddingTop: "22px"
    },
    ".cm-activeLine, .cm-activeLineGutter": { backgroundColor: "#faf7f3" },
    ".cm-selectionBackground, &.cm-focused .cm-selectionBackground": {
      backgroundColor: "#f3d8d2"
    },
    ".cm-cursor": { borderLeftColor: "#c83e2c" }
  });

  return new EditorView({
    parent: host,
    doc: source.value,
    extensions: [
      lineNumbers(),
      highlightActiveLineGutter(),
      highlightSpecialChars(),
      history(),
      foldGutter(),
      drawSelection(),
      dropCursor(),
      EditorState.allowMultipleSelections.of(true),
      indentOnInput(),
      syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
      highlightActiveLine(),
      highlightSelectionMatches(),
      keymap.of([
        { key: "Mod-b", run: (view) => toggleInlineMarkup(view, "**", "**", "加粗文字") },
        { key: "Mod-i", run: (view) => toggleInlineMarkup(view, "*", "*", "斜体文字") },
        indentWithTab,
        ...defaultKeymap,
        ...searchKeymap,
        ...historyKeymap,
        ...foldKeymap
      ]),
      markdown(),
      EditorView.lineWrapping,
      editorPlaceholder(placeholder),
      EditorView.contentAttributes.of({
        "aria-label": "Markdown 正文",
        autocapitalize: "sentences",
        spellcheck: "true"
      }),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) onChange(update.state.doc.toString());
      }),
      writerTheme
    ]
  });
}

export function applyMarkdownAction(view: EditorView, action: string): void {
  if (action === "bold") toggleInlineMarkup(view, "**", "**", "加粗文字");
  else if (action === "italic") toggleInlineMarkup(view, "*", "*", "斜体文字");
  else if (action === "link") insertMarkdownLink(view);
  else if (action === "heading") toggleLinePrefix(view, "## ");
  else if (action === "list") toggleLinePrefix(view, "- ");
  else if (action === "quote") toggleLinePrefix(view, "> ");
  else if (action === "code") toggleInlineMarkup(view, "```\n", "\n```", "代码");
}

export function toContentSlug(value: string): string {
  return value.normalize("NFKC").trim().toLocaleLowerCase("zh-CN")
    .replace(/[^\p{Letter}\p{Number}]+/gu, "-")
    .replace(/^-+|-+$/g, "");
}

function toggleInlineMarkup(
  view: EditorView,
  before: string,
  after: string,
  placeholder: string
): boolean {
  const selection = view.state.selection.main;
  const selected = view.state.doc.sliceString(selection.from, selection.to);
  const hasMarkup = selection.from >= before.length
    && view.state.doc.sliceString(selection.from - before.length, selection.from) === before
    && view.state.doc.sliceString(selection.to, selection.to + after.length) === after;

  if (hasMarkup) {
    view.dispatch({
      changes: [
        { from: selection.from - before.length, to: selection.from, insert: "" },
        { from: selection.to, to: selection.to + after.length, insert: "" }
      ],
      selection: EditorSelection.range(selection.from - before.length, selection.to - before.length),
      scrollIntoView: true
    });
    view.focus();
    return true;
  }

  const content = selected || placeholder;
  view.dispatch({
    changes: { from: selection.from, to: selection.to, insert: `${before}${content}${after}` },
    selection: EditorSelection.range(
      selection.from + before.length,
      selection.from + before.length + content.length
    ),
    scrollIntoView: true
  });
  view.focus();
  return true;
}

function toggleLinePrefix(view: EditorView, prefix: string): boolean {
  const selection = view.state.selection.main;
  const first = view.state.doc.lineAt(selection.from);
  const selectionEnd = selection.to > selection.from && selection.to > 0
    && view.state.doc.sliceString(selection.to - 1, selection.to) === "\n"
    ? selection.to - 1
    : selection.to;
  const last = view.state.doc.lineAt(selectionEnd);
  const lines = [];
  for (let position = first.from; position <= last.to;) {
    const line = view.state.doc.lineAt(position);
    lines.push(line);
    if (line.to >= last.to) break;
    position = line.to + 1;
  }
  const allPrefixed = lines.every((line) => line.text.startsWith(prefix));
  const headingPattern = prefix === "## " ? /^#{1,6}\s+/ : null;
  view.dispatch({
    changes: lines.map((line) => {
      const existing = headingPattern?.exec(line.text)?.[0]
        ?? (line.text.startsWith(prefix) ? prefix : "");
      return {
        from: line.from,
        to: line.from + existing.length,
        insert: allPrefixed ? "" : prefix
      };
    }),
    scrollIntoView: true
  });
  view.focus();
  return true;
}

function insertMarkdownLink(view: EditorView): boolean {
  const selection = view.state.selection.main;
  const label = view.state.doc.sliceString(selection.from, selection.to) || "链接文字";
  const url = "https://";
  const insertion = `[${label}](${url})`;
  const urlStart = selection.from + label.length + 3;
  view.dispatch({
    changes: { from: selection.from, to: selection.to, insert: insertion },
    selection: EditorSelection.range(urlStart, urlStart + url.length),
    scrollIntoView: true
  });
  view.focus();
  return true;
}
