import { h } from "../dom.js";
import { toId } from "../util.js";
import { tagList } from "./tag-chip.js";
import { timeEl } from "./time-el.js";
import { avatar } from "./avatar.js";

/** The page address of a question. Only a validated number ever goes into the URL. */
export function questionHref(id, hash = "") {
  const safe = toId(id);
  return safe ? `/question.html?id=${safe}${hash}` : null;
}

/** "Resolved" / "Open" pill. The text stays lowercase in the DOM; CSS capitalises it. */
export function statusPill(isResolved) {
  return isResolved
    ? h("span", { class: "status-pill status-pill--resolved", title: "Resolved: the author selected a correct answer" }, "resolved")
    : h("span", { class: "status-pill status-pill--open", title: "Not resolved yet" }, "open");
}

/**
 * One question card for a list (homepage, search results, My Questions): status + tags, title, a few body lines,
 * "+" count and author/time - exactly what the design asks for. Works for both list items and search results (which
 * have no author/time). `debug` adds the three scores under the card (search tuning view).
 */
export function questionRow(q, { debug = false } = {}) {
  const href = questionHref(q.questionId);
  const title = href ? h("a", { href }, q.title) : q.title;
  const meta = [];
  if (q.authorName) meta.push(avatar(q.authorName, { size: "sm" }), h("span", { class: "name" }, q.authorName));
  if (q.createdAt) meta.push(h("span", {}, "asked ", timeEl(q.createdAt)));
  const plus = q.interestCount ?? 0;

  return h("article", { class: "q-row" + (q.isResolved ? " q-row--resolved" : "") },
    h("div", { class: "q-top" }, statusPill(q.isResolved), tagList(q.tags || [])),
    h("h2", { class: "q-title" }, title),
    h("p", { class: "q-excerpt" }, q.bodyPreview ?? ""),
    h("div", { class: "q-foot" },
      h("span", { class: "plus-count", title: "People who also want this answered" },
        h("span", { class: "num" }, "+" + plus), plus === 1 ? "person wants this answered" : "people want this answered"),
      meta.length ? h("span", { class: "q-meta" }, meta) : null),
    debug ? h("div", { class: "q-debug" }, debugText(q)) : null);
}

function debugText(q) {
  const f = (n) => (typeof n === "number" ? n.toFixed(3) : "-");
  return `relevance ${f(q.relevance)} · semantic ${f(q.semanticScore)} · lexical ${f(q.lexicalScore)}`;
}
