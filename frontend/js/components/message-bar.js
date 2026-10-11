import { h, setChildren, linkButton } from "../dom.js";

/** kind: "info" (yellow) or "error" (pink). Errors use role=alert so screen readers announce them. */
export function messageBar(kind, text, ...extra) {
  return h("div", { class: `msg msg--${kind}`, role: kind === "error" ? "alert" : "status" }, text, ...extra);
}

export function showMessage(container, kind, text, ...extra) {
  setChildren(container, messageBar(kind, text, ...extra));
}

export function clearMessage(container) {
  container.replaceChildren();
}

/** Shows an ApiError (or anything thrown) in a message bar, optionally with a "Try again" button. */
export function showError(container, error, { onRetry } = {}) {
  const text = error && error.message ? error.message : "Something went wrong. Please try again.";
  showMessage(container, "error", text, onRetry ? linkButton("Try again", onRetry) : null);
}
