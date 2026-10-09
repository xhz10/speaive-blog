/** 记住正在编辑的字段，防止点击正文工具时误用离开正文前的旧选区。
 * 工具按钮获得键盘焦点不改变目标，仍支持从正文 Tab 到工具栏操作。
 */
export function initializeBodyToolTarget(form: HTMLFormElement, ownsBody: (target: Node) => boolean) {
  let bodyTarget = true;
  form.addEventListener("focusin", event => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    if (ownsBody(target)) bodyTarget = true;
    else if (target.matches("input, textarea, select, [contenteditable]")) bodyTarget = false;
    form.dataset.bodyToolsActive = String(bodyTarget);
  });
  return () => bodyTarget;
}
