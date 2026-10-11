// Small pure helpers (no DOM, no network) so they can be unit-tested with `node --test`.

/** "?tags=a&tags=b&q=x" from an object. Arrays repeat the key; null/undefined/""/[] are skipped. */
export function buildQuery(params = {}) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    const values = Array.isArray(value) ? value : [value];
    for (const v of values) {
      if (v !== undefined && v !== null && v !== "") search.append(key, String(v));
    }
  }
  const text = search.toString();
  return text ? "?" + text : "";
}

/** Reads a query string ("?a=1&a=2"). */
export function parseQuery(search = "") {
  const params = new URLSearchParams(search);
  return {
    get: (name) => params.get(name),
    getAll: (name) => params.getAll(name),
  };
}

/** A positive whole number from a string/number, else null. Used before putting any id into a URL. */
export function toId(value) {
  if (typeof value === "number") return Number.isSafeInteger(value) && value > 0 ? value : null;
  if (typeof value !== "string" || !/^[1-9][0-9]{0,15}$/.test(value)) return null;
  return Number(value);
}

/**
 * Where to go after login. Only a path on this site is accepted ("/question.html?id=3#comment-9");
 * anything that could leave the site ("//evil.com", "https://...", "/\\evil.com", "javascript:...") falls back.
 */
export function safeNext(next, fallback = "/") {
  if (typeof next !== "string" || next.length === 0 || next.length > 2000) return fallback;
  if (!next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return fallback;
  if (/[\u0000-\u001f\u007f\\]/.test(next)) return fallback;
  return next;
}

/** "1 answer" / "2 answers" */
export function plural(count, singular, pluralForm = singular + "s") {
  return count + " " + (count === 1 ? singular : pluralForm);
}

/** Net score shown between the vote arrows. */
export function netScore(up, down) {
  return (up || 0) - (down || 0);
}

/** Visual nesting level of a reply: grows with depth but stops at `max` (the design caps indentation at 6). */
export function indentLevel(depth, max = 6) {
  return Math.max(0, Math.min(depth, max));
}

/** Pages to show in the pager: [0, "…", 4, 5, 6, "…", 19] style, current page in a window of `radius` either side. */
export function pageWindow(current, total, radius = 2) {
  if (total <= 1) return [];
  const pages = new Set([0, total - 1]);
  for (let p = current - radius; p <= current + radius; p++) {
    if (p >= 0 && p < total) pages.add(p);
  }
  const sorted = [...pages].sort((a, b) => a - b);
  const result = [];
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) result.push("…");
    result.push(p);
  });
  return result;
}

/** Whole-number page from a query value; anything invalid is page 0. */
export function pageFrom(value) {
  const n = Number(value);
  return Number.isSafeInteger(n) && n > 0 ? n : 0;
}
