// Brief flash on an item (used when arriving from a notification and after adding something).

export function highlight(el) {
  if (!el) return;
  el.scrollIntoView({ block: "center", behavior: "auto" });
  el.classList.remove("highlight");
  void el.offsetWidth;                 // restart the animation if it is already running
  el.classList.add("highlight");
  setTimeout(() => el.classList.remove("highlight"), 2500);
}

/** If the address ends in #comment-12 or #answer-5, scroll to and flash that item. */
export function highlightFromHash() {
  const match = /^#((?:comment|answer)-[1-9][0-9]*)$/.exec(location.hash);
  if (match) highlight(document.getElementById(match[1]));
}
