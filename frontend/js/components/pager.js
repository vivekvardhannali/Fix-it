import { h } from "../dom.js";
import { pageWindow } from "../util.js";

/**
 * Prev 1 2 [3] 4 5 Next  - `page` is 0-based; `hrefFor(page)` returns the link for a page.
 * Returns null when there is only one page.
 */
export function pager({ page, totalPages, hrefFor }) {
  if (!(totalPages > 1)) return null;
  const items = [];
  items.push(page > 0 ? h("a", { href: hrefFor(page - 1), rel: "prev" }, "Prev") : h("span", { class: "disabled", "aria-disabled": "true" }, "Prev"));
  for (const p of pageWindow(page, totalPages)) {
    if (p === "…") items.push(h("span", { class: "gap", "aria-hidden": "true" }, "…"));
    else if (p === page) items.push(h("span", { class: "current", "aria-current": "page" }, String(p + 1)));
    else items.push(h("a", { href: hrefFor(p), "aria-label": `Page ${p + 1}` }, String(p + 1)));
  }
  items.push(page < totalPages - 1 ? h("a", { href: hrefFor(page + 1), rel: "next" }, "Next") : h("span", { class: "disabled", "aria-disabled": "true" }, "Next"));
  return h("nav", { class: "pager", "aria-label": "Pagination" }, items);
}
