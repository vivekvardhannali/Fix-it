import { h } from "../dom.js";

/** Up to two initials from a display name ("cs23b043" -> "CS", "alice_b" -> "AB", "Ravi Kumar" -> "RK"). */
export function initials(name = "") {
  const parts = String(name).trim().split(/[\s._-]+/).filter(Boolean);
  if (parts.length === 0) return "?";
  const text = parts.length > 1 ? parts[0][0] + parts[1][0] : parts[0].slice(0, 2);
  return text.toUpperCase();
}

/** Same name -> same colour (one of 8 classes, all AA with white text). */
function colourClass(name = "") {
  let hash = 0;
  for (const ch of String(name)) hash = (hash * 31 + ch.codePointAt(0)) >>> 0;
  return "avatar--c" + (hash % 8);
}

/** A decorative initials circle; the name itself is always shown as text next to it, so this is aria-hidden. */
export function avatar(name, { size = "" } = {}) {
  return h("span", { class: `avatar ${colourClass(name)}${size ? " avatar--" + size : ""}`, "aria-hidden": "true" }, initials(name));
}
