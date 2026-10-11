import { h } from "../dom.js";

/**
 * Tag filter for the homepage / search: one toggle button per tag (the fixed four), pressed = selected.
 * Clicking calls `onToggle(name)`; the page decides what that means (it reloads with the new tag list).
 */
export function tagFilter({ tags, selected = [], onToggle }) {
  const on = new Set(selected.map((t) => t.toLowerCase()));
  return h("div", { class: "tag-filter", role: "group", "aria-label": "Filter by tag" },
    tags.map((t) => h("button", {
      type: "button", class: "tag tag-toggle", "aria-pressed": on.has(t.name.toLowerCase()) ? "true" : "false",
      onClick: () => onToggle(t.name),
    }, t.name)));
}

/**
 * Tag choice for the ask form: checkbox chips (nothing can be typed or created). Returns { element, getSelected }.
 */
export function tagPicker({ tags, selected = [], max = 4, name = "tags" }) {
  const on = new Set(selected.map((t) => t.toLowerCase()));
  const boxes = tags.map((t) => h("input", { type: "checkbox", name, value: t.name, checked: on.has(t.name.toLowerCase()) || undefined }));
  const error = h("div", { class: "field-error", id: "tags-error", role: "alert", hidden: true });
  const field = h("fieldset", { class: "field", "aria-describedby": "tags-hint tags-error" },
    h("legend", {}, "Tags"),
    h("div", { class: "tag-options" }, tags.map((t, i) => h("label", { class: "tag-option" }, boxes[i], h("span", {}, t.name)))),
    h("div", { class: "hint", id: "tags-hint" }, `Choose 1 to ${Math.min(max, tags.length)}. These are the only tags there are.`),
    error);
  return {
    element: field,
    boxes,
    getSelected: () => boxes.filter((b) => b.checked).map((b) => b.value),
    setError(message) {
      error.hidden = !message;
      error.textContent = message || "";
      field.classList.toggle("has-error", Boolean(message));
    },
    focus: () => boxes[0]?.focus(),
  };
}
