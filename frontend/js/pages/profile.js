// Profile -> My Questions: the logged-in user's own questions, newest first.

import { h, setChildren } from "../dom.js";
import { api } from "../api.js";
import { mountHeader, mountFooter } from "../header.js";
import { parseQuery, buildQuery, pageFrom, plural } from "../util.js";
import { questionRow } from "../components/question-row.js";
import { pager } from "../components/pager.js";
import { showError, clearMessage } from "../components/message-bar.js";

await mountHeader("profile");
mountFooter();
document.title = "My Questions - Fix It";

const messages = document.getElementById("messages");
const content = document.getElementById("content");
const page = pageFrom(parseQuery(location.search).get("page"));

async function load() {
  clearMessage(messages);
  setChildren(content,
    h("div", { class: "page-head" }, h("h1", {}, "My Questions"), h("a", { class: "btn btn-primary", href: "/ask.html" }, "Ask Question")),
    h("p", { class: "loading" }, "Loading…"));
  try {
    const result = await api.myQuestions(page);
    const head = h("div", { class: "page-head" }, h("h1", {}, "My Questions"), h("a", { class: "btn btn-primary", href: "/ask.html" }, "Ask Question"));
    if (result.items.length === 0) {
      setChildren(content, head, h("div", { class: "empty" }, "You have not asked any questions yet.", h("br"), h("a", { class: "btn btn-primary", href: "/ask.html" }, "Ask Question")));
      return;
    }
    setChildren(content, head,
      h("p", { class: "subtle", id: "result-count" }, plural(result.totalItems, "question"), ", newest first."),
      h("div", { class: "q-list" }, result.items.map((q) => questionRow(q))),
      pager({ page: result.page, totalPages: result.totalPages, hrefFor: (p) => "/profile.html" + buildQuery({ page: p || undefined }) }));
  } catch (e) {
    setChildren(content);
    showError(messages, e, { onRetry: load });
  }
}

await load();
