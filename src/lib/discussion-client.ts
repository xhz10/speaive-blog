export function setupDiscussion(root: HTMLElement) {
  if (root.dataset.bound) return;
  root.dataset.bound = "true";
  const announcement = root.querySelector<HTMLElement>("[data-comment-announcement]")!;
  const threads = root.querySelector<HTMLOListElement>("[data-comment-threads]");
  const announce = (message: string) => { announcement.textContent = message; };

  function measureComments() {
    root.querySelectorAll<HTMLElement>("[data-discussion-comment]").forEach((comment) => {
      const body = comment.querySelector<HTMLElement>("[data-comment-body]")!;
      const button = comment.querySelector<HTMLButtonElement>("[data-comment-expand]")!;
      if (!body.getClientRects().length || button.getAttribute("aria-expanded") === "true") return;
      body.classList.add("is-clamped");
      const overflows = body.scrollHeight > body.clientHeight + 1;
      button.hidden = !overflows;
      body.classList.toggle("is-clamped", overflows);
    });
  }

  function expandComment(comment: HTMLElement) {
    comment.querySelector<HTMLElement>("[data-comment-body]")?.classList.remove("is-clamped");
    const button = comment.querySelector<HTMLButtonElement>("[data-comment-expand]");
    if (!button || button.hidden) return;
    button.setAttribute("aria-expanded", "true");
    button.querySelector<HTMLElement>("[data-expand-label]")!.textContent = "收起全文";
  }

  function revealHash(focus = false) {
    let id: string;
    try { id = decodeURIComponent(location.hash.slice(1)); } catch { return; }
    const target = document.getElementById(id);
    if (!target || !root.contains(target)) return;
    const disclosure = target.closest<HTMLDetailsElement>("[data-more-replies]");
    if (disclosure) disclosure.open = true;
    measureComments();
    if (target.matches("[data-discussion-comment]")) expandComment(target);
    requestAnimationFrame(() => {
      if (focus) target.focus({ preventScroll: true });
      target.scrollIntoView({ block: "start" });
    });
  }

  function sortThreads(order: string) {
    if (!threads) return;
    const items = Array.from(threads.children) as HTMLElement[];
    items.sort((a, b) => {
      const difference = Number(a.dataset.threadTime) - Number(b.dataset.threadTime)
        || (a.dataset.threadId ?? "").localeCompare(b.dataset.threadId ?? "");
      return order === "oldest" ? difference : -difference;
    });
    threads.append(...items);
    root.querySelectorAll<HTMLAnchorElement>("[data-comment-sort]").forEach((link) => {
      if (link.dataset.commentSort === order) link.setAttribute("aria-current", "true");
      else link.removeAttribute("aria-current");
    });
  }

  root.addEventListener("click", async (event) => {
    if (!(event.target instanceof Element)) return;
    const button = event.target.closest<HTMLButtonElement>("[data-comment-expand]");
    if (button) {
      const comment = button.closest<HTMLElement>("[data-discussion-comment]")!;
      if (button.getAttribute("aria-expanded") === "true") {
        button.setAttribute("aria-expanded", "false");
        button.querySelector<HTMLElement>("[data-expand-label]")!.textContent = "展开全文";
        measureComments();
      } else expandComment(comment);
      return;
    }
    const link = event.target.closest<HTMLAnchorElement>("a");
    if (!link || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    if (link.dataset.commentSort) {
      event.preventDefault();
      sortThreads(link.dataset.commentSort);
      history.replaceState(null, "", link.href);
      announce(link.dataset.commentSort === "oldest" ? "已按最早评论排序，回复按时间顺序显示。" : "已按最新评论排序，回复按时间顺序显示。");
    } else if (link.hasAttribute("data-copy-comment")) {
      event.preventDefault();
      const url = new URL(location.href);
      url.hash = link.hash;
      try {
        await navigator.clipboard.writeText(url.href);
        announce("评论链接已复制，可以直接分享这条讨论。");
        const label = link.querySelector("span");
        if (label) {
          label.textContent = "已复制";
          window.setTimeout(() => { label.textContent = "链接"; }, 2000);
        }
      } catch {
        history.replaceState(null, "", url);
        announce("已定位到这条评论，可以复制浏览器地址分享。");
        revealHash(true);
      }
    } else if (link.hasAttribute("data-comment-context") || link.hasAttribute("data-view-replies")) {
      event.preventDefault();
      history.pushState(null, "", link.href);
      revealHash(true);
    }
  });

  root.querySelectorAll<HTMLDetailsElement>("[data-more-replies]").forEach((details) => {
    details.addEventListener("toggle", measureComments);
  });
  const onHashChange = () => revealHash();
  const onPopState = () => {
    sortThreads(new URL(location.href).searchParams.get("comments") === "oldest" ? "oldest" : "newest");
    revealHash();
  };
  window.addEventListener("hashchange", onHashChange);
  window.addEventListener("popstate", onPopState);
  const observer = new ResizeObserver(measureComments);
  observer.observe(root);
  document.addEventListener("astro:before-swap", () => {
    observer.disconnect();
    window.removeEventListener("hashchange", onHashChange);
    window.removeEventListener("popstate", onPopState);
  }, { once: true });
  measureComments();
  void document.fonts.ready.then(measureComments);
  if (location.hash) revealHash();
}
