/** 使用原生 dialog 保留焦点约束、Esc 取消和关闭后返回触发按钮的能力。 */
export function confirmWritingAction(title: string, description: string, action: string, danger = false): Promise<boolean> {
  const dialog = document.querySelector<HTMLDialogElement>("[data-writing-confirmation]");
  if (!dialog || dialog.open) return Promise.resolve(false);
  const trigger = document.activeElement;
  dialog.querySelector("#writing-confirm-title")!.textContent = title;
  dialog.querySelector("#writing-confirm-description")!.textContent = description;
  dialog.querySelector("[data-confirm-action]")!.textContent = action;
  dialog.dataset.danger = String(danger);
  dialog.returnValue = "cancel";
  return new Promise((resolve) => {
    dialog.addEventListener("close", () => {
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus();
      resolve(dialog.returnValue === "confirm");
    }, { once: true });
    dialog.showModal();
  });
}
