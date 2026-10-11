interface ConfirmationOptions {
  title: string;
  message: string;
  confirmLabel: string;
}

/** 操作确认框：等待明确选择，取消、Escape 和离开页面均不执行操作。 */
export function initializeStudioConfirmation(dialog: HTMLDialogElement) {
  const title = dialog.querySelector<HTMLElement>("[data-confirm-title]")!;
  const message = dialog.querySelector<HTMLElement>("[data-confirm-message]")!;
  const confirm = dialog.querySelector<HTMLButtonElement>("[data-confirm-accept]")!;
  const cancel = dialog.querySelector<HTMLButtonElement>("[data-confirm-cancel]")!;
  let resolveDecision: ((confirmed: boolean) => void) | undefined;
  let previousFocus: HTMLElement | null = null;

  function finish(confirmed: boolean) {
    if (!resolveDecision) return;
    const resolve = resolveDecision;
    const focus = previousFocus;
    resolveDecision = undefined;
    if (dialog.open) dialog.close();
    resolve(confirmed);
    requestAnimationFrame(() => {
      if (focus?.isConnected && document.activeElement === document.body) focus.focus({ preventScroll: true });
    });
  }
  confirm.addEventListener("click", () => finish(true));
  cancel.addEventListener("click", () => finish(false));
  dialog.addEventListener("cancel", event => { event.preventDefault(); finish(false); });
  dialog.addEventListener("close", () => { if (!dialog.open) finish(false); });
  window.addEventListener("pagehide", () => finish(false));

  return (options: ConfirmationOptions): Promise<boolean> => {
    // 同一时刻只展示一个决定，不替换正在等待的确认内容。
    if (resolveDecision) return Promise.resolve(false);
    title.textContent = options.title;
    message.textContent = options.message;
    confirm.textContent = options.confirmLabel;
    previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    return new Promise((resolve, reject) => {
      resolveDecision = resolve;
      try { dialog.showModal(); cancel.focus({ preventScroll: true }); }
      catch (error) { resolveDecision = undefined; reject(error); }
    });
  };
}
