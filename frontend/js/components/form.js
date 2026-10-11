import { h } from "../dom.js";

/** A labelled single-line field with a hint and an inline error that screen readers announce. */
export function textField({ id, label, type = "text", autocomplete, hint, maxlength, value = "", required = false }) {
  const input = h("input", { id, name: id, type, autocomplete, maxlength, value: value || undefined, required: required || undefined });
  return field({ id, label, input, hint });
}

export function textAreaField({ id, label, hint, maxlength, value = "", rows = 8, counter = false }) {
  const input = h("textarea", { id, name: id, maxlength, rows }, value);
  const result = field({ id, label, input, hint });
  if (counter) {
    const count = h("div", { class: "counter", "aria-hidden": "true" });
    const update = () => { count.textContent = `${input.value.length} / ${maxlength}`; };
    input.addEventListener("input", update);
    update();
    result.element.insertBefore(count, result.element.querySelector(".field-error"));
  }
  return result;
}

function field({ id, label, input, hint }) {
  const error = h("div", { class: "field-error", id: `${id}-error`, role: "alert", hidden: true });
  const element = h("div", { class: "field" },
    h("label", { for: id }, label), input, hint ? h("div", { class: "hint", id: `${id}-hint` }, hint) : null, error);
  input.setAttribute("aria-describedby", [hint ? `${id}-hint` : null, `${id}-error`].filter(Boolean).join(" "));
  return {
    element, input,
    setError(message) {
      error.hidden = !message;
      error.textContent = message || "";
      element.classList.toggle("has-error", Boolean(message));
      input.setAttribute("aria-invalid", message ? "true" : "false");
    },
  };
}

/** Shows each message under its field and focuses the first field that has one. Returns true when there were none. */
export function applyErrors(fields, errors) {
  let first = null;
  for (const [name, f] of Object.entries(fields)) {
    f.setError(errors[name] || "");
    if (errors[name] && !first) first = f;
  }
  first?.input?.focus?.();
  return first === null;
}

/** "5 seconds", "3 minutes", "2 hours" for a Retry-After value. */
export function waitText(seconds) {
  if (!seconds || seconds < 1) return "a little while";
  if (seconds < 90) return `${Math.ceil(seconds)} seconds`;
  const minutes = Math.ceil(seconds / 60);
  if (minutes < 90) return `${minutes} minutes`;
  return `${Math.ceil(minutes / 60)} hours`;
}
