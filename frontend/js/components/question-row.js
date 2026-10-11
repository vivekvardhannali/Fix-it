import { h } from "../dom.js";
import { toId } from "../util.js";
import { tagList } from "./tag-chip.js";
import { timeEl } from "./time-el.js";

/** The page address of a question. Only a validated number ever goes into the URL. */
export function questionHref(id, hash = "") {
  const safe = toId(id);
  return safe ? `/question.html?id=${safe}${hash}` : null;
}

/**
 * One row of a question list (homepage, search results, My Questions): `+` box, status box, title, a few body lines,
 * tags - exactly what the design asks for. Works for both list items and search results (which have no author/time).
 * `debug` adds the three scores under the row (search tuning view).
 */
export function questionRow(q, { debug = false } = {}) {
  const href = questionHref(q.questionId);
  const title = href ? h("a", { href }, q.title) : q.title;
  const meta = [];
  if (q.createdAt) meta.push("asked ", timeEl(q.createdAt));
  if (q.createdAt && q.authorName) meta.push(" by ", q.authorName);
  else if (q.authorName) meta.push("by ", q.authorName);

  return h("article", { class: "q-row" },
    h("div", { class: "q-stats" },
      h("div", { class: "stat stat--plus", title: "People who also want this answered" },
        h("span", { class: "num" }, String(q.interestCount ?? 0)), h("span", { class: "label" }, "plus")),
      q.isResolved
        ? h("div", { class: "stat stat--resolved", title: "Resolved: the author selected a correct answer" },
          h("span", { class: "num", "aria-hidden": "true" }, "✓"), h("span", { class: "label" }, "resolved"))
        : h("div", { class: "stat stat--open", title: "Not resolved yet" },
          h("span", { class: "num", "aria-hidden": "true" }, "?"), h("span", { class: "label" }, "open"))),
    h("div", { class: "q-summary" },
      h("h2", { class: "q-title" }, title),
      h("p", { class: "q-excerpt" }, q.bodyPreview ?? ""),
      h("div", { class: "q-foot" }, tagList(q.tags || []), meta.length ? h("span", { class: "q-meta" }, meta) : null),
      debug ? h("div", { class: "q-debug" }, debugText(q)) : null));
}

function debugText(q) {
  const f = (n) => (typeof n === "number" ? n.toFixed(3) : "-");
  return `relevance ${f(q.relevance)} · semantic ${f(q.semanticScore)} · lexical ${f(q.lexicalScore)}`;
}
