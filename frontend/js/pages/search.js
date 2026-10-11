// Search results: the query within the selected tags, already thresholded and ranked by the backend.
// Add &debug=1 for the tuning view (mode switch + semantic / lexical / final scores).

import { h, setChildren, ensureHeading } from "../dom.js";
import { api } from "../api.js";
import { mountHeader, mountFooter } from "../header.js";
import { parseQuery, buildQuery, plural } from "../util.js";
import { validateSearchQuery } from "../validate.js";
import { questionRow } from "../components/question-row.js";
import { tagFilter } from "../components/tag-picker.js";
import { showError, showMessage, clearMessage } from "../components/message-bar.js";

await mountHeader("search");
mountFooter();

const messages = document.getElementById("messages");
const content = document.getElementById("content");
const params = parseQuery(location.search);
const q = (params.get("q") || "").trim();
const debug = params.get("debug") === "1";
const modeParam = ["hybrid", "semantic", "lexical"].includes(params.get("mode")) ? params.get("mode") : "hybrid";
document.title = (q ? `${q} - Search` : "Search") + " - Fix It";

const searchHref = (overrides = {}) => "/search.html" + buildQuery({ q, tags: overrides.tags ?? [], debug: debug ? 1 : undefined, mode: debug ? overrides.mode ?? modeParam : undefined });

async function load() {
  clearMessage(messages);
  let allTags = [];
  try { allTags = await api.tags(); } catch (e) { ensureHeading("Search"); return showError(messages, e, { onRetry: load }); }
  const known = new Map(allTags.map((t) => [t.name.toLowerCase(), t.name]));
  const selected = [...new Set(params.getAll("tags").map((t) => known.get(t.trim().toLowerCase())).filter(Boolean))];

  const toggle = (name) => location.assign(searchHref({ tags: selected.includes(name) ? selected.filter((t) => t !== name) : [...selected, name] }));
  const askHref = "/ask.html" + buildQuery({ title: q, tags: selected });
  const askBox = h("div", { class: "cta-box", id: "ask-box" }, h("span", {}, "Not what you were looking for?"), h("a", { class: "btn btn-primary", href: askHref }, "Ask a new question"));
  const modeSwitch = debug ? h("p", { class: "subtle", id: "mode-switch" }, "Tuning view — ranking: ",
    ["hybrid", "semantic", "lexical"].map((m) => [m === modeParam ? h("strong", { "aria-current": "true" }, m) : h("a", { href: searchHref({ tags: selected, mode: m }) }, m), " "])) : null;

  const problem = validateSearchQuery(q);
  setChildren(content, h("div", { class: "page-head" }, h("h1", {}, q ? ["Search results for “", q, "”"] : "Search"), h("a", { class: "btn btn-secondary", href: "/ask.html" + buildQuery({ title: q, tags: selected }) }, "Ask Question")),
    modeSwitch, tagFilter({ tags: allTags, selected, onToggle: toggle }), h("div", { id: "results" }));
  const results = document.getElementById("results");

  if (problem) {                                       // empty/too long: say so before sending anything
    showMessage(messages, "info", problem);
    return;
  }
  setChildren(results, h("p", { class: "loading" }, "Searching…"));
  try {
    const found = await api.search({ q, tags: selected, mode: debug ? modeParam : undefined });
    if (found.length === 0) {
      setChildren(results, h("div", { class: "empty", id: "no-results" }, "No similar questions found."), askBox);
      return;
    }
    setChildren(results, h("p", { class: "subtle", id: "result-count" }, plural(found.length, "result"), ", most relevant first."),
      h("div", { class: "q-list" }, found.map((r) => questionRow(r, { debug }))), askBox);
  } catch (e) {
    setChildren(results);
    if (e.status === 503) {
      showMessage(messages, "info", "Search is temporarily unavailable. You can still ", h("a", { href: "/" }, "browse questions"), " or ", h("a", { href: askHref }, "ask a new one"), ".");
    } else {
      showError(messages, e, { onRetry: load });
    }
  }
}

await load();
