// The header shown on every page except login: logo, tabs, search box, notification bell, profile menu.

import { h, setChildren } from "./dom.js";
import { api } from "./api.js";
import { currentUser, displayName, logout } from "./session.js";
import { parseQuery, toId } from "./util.js";
import { bellIcon, userIcon } from "./components/icons.js";
import { timeEl } from "./components/time-el.js";
import { questionHref } from "./components/question-row.js";

const POLL_MS = 60_000;

/** A button that opens/closes a dropdown panel. Closes on Escape and on a click anywhere else. */
function makeMenu(button, panel, onOpen) {
  const close = ({ refocus = false } = {}) => {
    panel.hidden = true;
    button.setAttribute("aria-expanded", "false");
    if (refocus) button.focus();
  };
  const open = () => {
    document.dispatchEvent(new CustomEvent("fixit:close-menus"));
    panel.hidden = false;
    button.setAttribute("aria-expanded", "true");
    onOpen?.();
  };
  button.addEventListener("click", (event) => {
    event.stopPropagation();
    panel.hidden ? open() : close();
  });
  panel.addEventListener("click", (event) => event.stopPropagation());
  document.addEventListener("click", () => close());
  document.addEventListener("fixit:close-menus", () => close());
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !panel.hidden) close({ refocus: true });
  });
  return { close };
}

/** "bob commented on your question 'WiFi…'" - built from the notification's own fields. */
function noticeText(n) {
  const who = h("strong", {}, n.actorName || "Someone");
  const title = h("em", {}, n.questionTitle || "a question");
  if (n.type === "COMMENT_ON_QUESTION") return [who, " commented on your question ", title];
  if (n.type === "REPLY_TO_COMMENT") return [who, " replied to your comment on ", title];
  return ["New activity on ", title];
}

/** live=false (style guide): no network calls; shows `unread` and the `sample` notifications. */
function buildBell({ live = true, unread = 0, sample = [] } = {}) {
  const badge = h("span", { class: "badge", hidden: true });
  const button = h("button", { type: "button", class: "icon-btn", id: "bell-button", "aria-haspopup": "true", "aria-expanded": "false", "aria-controls": "notice-panel", "aria-label": "Notifications" }, bellIcon(), badge);
  const panel = h("div", { class: "dropdown", id: "notice-panel", hidden: true });

  const setCount = (count) => {
    badge.hidden = !(count > 0);
    badge.textContent = count > 99 ? "99+" : String(count);
    button.setAttribute("aria-label", count > 0 ? `Notifications, ${count} unread` : "Notifications");
  };
  const refreshCount = async () => {
    if (!live) return;
    try {
      setCount((await api.unreadCount()).unreadCount);
    } catch { /* a failed badge refresh is not worth an error message */ }
  };

  const openNotice = async (n) => {
    if (!live) return;
    try { await api.markRead(n.notificationId); } catch { /* still take them to the question */ }
    const commentId = toId(n.commentId);
    const href = questionHref(n.questionId, commentId ? `#comment-${commentId}` : "");
    if (href) location.assign(href);
  };

  const loadList = async () => {
    setChildren(panel, h("div", { class: "dropdown-head" }, "Notifications"), h("div", { class: "dropdown-note" }, "Loading…"));
    try {
      const list = live ? await api.notifications({ limit: 10 }) : sample;
      setChildren(panel, h("div", { class: "dropdown-head" }, "Notifications"),
        list.length === 0
          ? h("div", { class: "dropdown-note" }, "No notifications")
          : list.map((n) => h("button", { type: "button", class: "dropdown-item notice" + (n.isRead ? "" : " unread"), onClick: () => openNotice(n) },
            h("span", { class: "notice-text" }, noticeText(n)), h("span", { class: "notice-time" }, timeEl(n.createdAt)))));
      refreshCount();
    } catch {
      setChildren(panel, h("div", { class: "dropdown-head" }, "Notifications"), h("div", { class: "dropdown-note", role: "alert" }, "Could not load notifications"));
    }
  };

  makeMenu(button, panel, loadList);
  setCount(unread);
  if (live) {
    refreshCount();
    setInterval(() => { if (document.visibilityState === "visible") refreshCount(); }, POLL_MS);   // no sockets: poll while the tab is visible
    document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") refreshCount(); });
  }
  return h("div", { class: "header-menu" }, button, panel);
}

function buildProfile(user, { live = true } = {}) {
  const button = h("button", { type: "button", class: "icon-btn", id: "profile-button", "aria-haspopup": "true", "aria-expanded": "false", "aria-controls": "profile-panel" },
    userIcon(), h("span", {}, displayName(user)));
  const panel = h("div", { class: "dropdown", id: "profile-panel", hidden: true },
    h("div", { class: "dropdown-meta" }, h("div", {}, "User ID: ", h("strong", { id: "profile-user-id" }, String(user.userId))), h("div", {}, user.smail)),
    h("a", { class: "dropdown-item", href: "/profile.html" }, "My Questions"),
    h("button", { type: "button", class: "dropdown-item", id: "logout-button", onClick: () => (live ? logout() : undefined) }, "Log out"));
  makeMenu(button, panel);
  return h("div", { class: "header-menu header-menu--right" }, button, panel);
}

/**
 * Draws the header into `host`. Used by mountHeader (live) and by the style guide (live: false, sample data).
 * `page` is one of home | question | search | ask | profile.
 */
export function renderHeader(host, { user, page, live = true, unread = 0, notices = [] }) {
  const params = parseQuery(location.search);
  const onSearchPage = page === "search";
  const tab = (href, label, current) => h("a", { href, "aria-current": current ? "page" : undefined }, label);

  host.className = "site-header";
  setChildren(host,
    h("div", { class: "container" },
      h("a", { class: "logo", href: "/" }, "Fix It"),
      h("nav", { class: "tabs", "aria-label": "Main" },
        tab("/", "Questions", page === "home" || page === "question" || page === "search"),
        tab("/ask.html", "Ask Question", page === "ask")),
      h("form", { class: "header-search", action: "/search.html", method: "get", role: "search" },
        h("label", { class: "visually-hidden", for: "header-q" }, "Search questions"),
        h("input", { id: "header-q", type: "search", name: "q", placeholder: "Search questions…", maxlength: 500, autocomplete: "off", value: onSearchPage ? params.get("q") || "" : undefined }),
        // keep the tags that are currently selected when searching from the homepage/search page
        (page === "home" || onSearchPage) ? params.getAll("tags").map((t) => h("input", { type: "hidden", name: "tags", value: t })) : null),
      buildBell({ live, unread, sample: notices }),
      buildProfile(user, { live })));
}

/**
 * Builds the header into <header id="site-header"> for the logged-in user. The first call also redirects to the login
 * page when there is no session. Returns the user.
 */
export async function mountHeader(page) {
  const user = await currentUser();
  renderHeader(document.getElementById("site-header"), { user, page });
  return user;
}

export function mountFooter() {
  const host = document.getElementById("site-footer");
  if (!host) return;
  host.className = "site-footer";
  setChildren(host, h("div", { class: "container" }, "Fix It — IIT Madras · ", h("a", { href: "/" }, "Questions"), " · ", h("a", { href: "/ask.html" }, "Ask a question")));
}
