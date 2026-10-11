// Homepage: questions that have ANY of the selected tags, most "+" first (no tags selected = all questions).

import { h, setChildren, ensureHeading } from "../dom.js";
import { api } from "../api.js";
import { mountHeader, mountFooter } from "../header.js";
import { parseQuery, buildQuery, pageFrom, plural } from "../util.js";
import { questionRow } from "../components/question-row.js";
import { pager } from "../components/pager.js";
import { tagFilter } from "../components/tag-picker.js";
import { showError, showMessage, clearMessage } from "../components/message-bar.js";

await mountHeader("home");
mountFooter();
document.title = "Questions - Fix It";

const messages = document.getElementById("messages");
const content = document.getElementById("content");
const params = parseQuery(location.search);
const page = pageFrom(params.get("page"));

const hrefFor = (tags, p = 0) => "/" + buildQuery({ tags, page: p || undefined });

async function load() {
  clearMessage(messages);
  let allTags = [];
  try {
    allTags = await api.tags();
  } catch (e) {
    ensureHeading("Questions");
    showError(messages, e, { onRetry: load });
    return;
  }
  // only tags that exist are used; anything else in the address is ignored (and mentioned)
  const known = new Map(allTags.map((t) => [t.name.toLowerCase(), t.name]));
  const asked = params.getAll("tags");
  const selected = [...new Set(asked.map((t) => known.get(t.trim().toLowerCase())).filter(Boolean))];
  const ignored = asked.filter((t) => !known.has(t.trim().toLowerCase()));
  if (ignored.length) showMessage(messages, "info", `Ignored unknown tag${ignored.length > 1 ? "s" : ""}: ${ignored.join(", ")}.`);

  const toggle = (name) => {
    const next = selected.includes(name) ? selected.filter((t) => t !== name) : [...selected, name];
    location.assign(hrefFor(next));
  };
  const head = h("div", { class: "page-head" }, h("h1", {}, "Questions"), h("a", { class: "btn btn-primary", href: "/ask.html" }, "Ask Question"));
  const intro = h("p", { class: "subtle", id: "home-intro" },
    selected.length ? "Questions with any of the selected tags, most + first." : "All questions, most + first. Pick tags to narrow them down.");
  const list = h("div", { id: "question-list" }, h("p", { class: "loading" }, "Loading…"));
  setChildren(content, head, intro, tagFilter({ tags: allTags, selected, onToggle: toggle }), list);

  try {
    const result = await api.listQuestions({ tags: selected, page });
    if (result.items.length === 0) {
      setChildren(list, h("div", { class: "empty" },
        selected.length ? "No questions yet for these tags." : "No questions yet.",
        h("br"), h("a", { class: "btn btn-primary", href: "/ask.html" }, "Ask the first one")));
      return;
    }
    setChildren(list,
      h("p", { class: "subtle", id: "result-count" }, plural(result.totalItems, "question")),
      h("div", { class: "q-list" }, result.items.map((q) => questionRow(q))),
      pager({ page: result.page, totalPages: result.totalPages, hrefFor: (p) => hrefFor(selected, p) }));
  } catch (e) {
    setChildren(list);
    showError(messages, e, { onRetry: load });
  }
}

await load();
