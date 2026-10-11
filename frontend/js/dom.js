// Safe element builder. User text is ONLY ever added as text nodes (never parsed as HTML), so a question titled
// "<script>alert(1)</script>" is shown literally. innerHTML/outerHTML/insertAdjacentHTML/document.write/eval are banned
// in frontend/js (a unit test greps for them).

/**
 * h("a", { class: "btn", href: "/x", onClick: handler }, "text", otherElement, [moreChildren])
 *  - `class`/`className` set the class; `on<Event>` adds a listener; `dataset` sets data-*; `text` sets textContent;
 *  - boolean true -> empty attribute, false/null/undefined -> attribute omitted;
 *  - children: strings/numbers become text nodes; arrays are flattened; null/undefined/false are skipped.
 */
export function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props || {})) {
    if (value === undefined || value === null || value === false) continue;
    if (key === "class" || key === "className") el.className = value;
    else if (key === "text") el.textContent = String(value);
    else if (key === "dataset") Object.assign(el.dataset, value);
    else if (key.startsWith("on") && typeof value === "function") el.addEventListener(key.slice(2).toLowerCase(), value);
    else if (value === true) el.setAttribute(key, "");
    else el.setAttribute(key, String(value));
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const child of children.flat(Infinity)) {
    if (child === undefined || child === null || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

/** Replaces everything inside `el` with the given children. */
export function setChildren(el, ...children) {
  el.replaceChildren();
  append(el, children);
  return el;
}

export const qs = (selector, root = document) => root.querySelector(selector);

/** Guarantees the page has exactly one <h1>, even in an error state where the normal content never rendered. */
export function ensureHeading(text) {
  if (document.querySelector("h1")) return;
  const host = document.getElementById("content") || document.getElementById("main");
  host.prepend(h("h1", { class: "page-title" }, text));
}

/** Makes a text button that looks like a link (for actions that are not navigation). */
export function linkButton(label, onClick, props = {}) {
  return h("button", { type: "button", class: "btn-link", onClick, ...props }, label);
}

/** Disables a button while an async action runs, always re-enabling it afterwards. */
export async function whileBusy(button, action) {
  button.disabled = true;
  button.setAttribute("aria-busy", "true");
  try {
    return await action();
  } finally {
    button.disabled = false;
    button.removeAttribute("aria-busy");
  }
}
