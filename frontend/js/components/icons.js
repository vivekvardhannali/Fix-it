// Small inline SVG icons (built with createElementNS, so there is no HTML parsing).
const NS = "http://www.w3.org/2000/svg";

function svg(path, className) {
  const el = document.createElementNS(NS, "svg");
  if (className) el.setAttribute("class", className);
  el.setAttribute("viewBox", "0 0 24 24");
  el.setAttribute("aria-hidden", "true");
  el.setAttribute("focusable", "false");
  const p = document.createElementNS(NS, "path");
  p.setAttribute("d", path);
  el.append(p);
  return el;
}

export const bellIcon = () => svg("M12 2a6 6 0 0 0-6 6v3.6L4.3 15.3A1 1 0 0 0 5.2 17h13.6a1 1 0 0 0 .9-1.7L18 11.6V8a6 6 0 0 0-6-6zm0 20a2.5 2.5 0 0 0 2.4-2H9.6A2.5 2.5 0 0 0 12 22z");
export const userIcon = () => svg("M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0 2c-4.4 0-8 2.2-8 5v1.5h16V19c0-2.8-3.6-5-8-5z");
export const searchIcon = () => svg("M10 2a8 8 0 0 1 6.32 12.9l5.39 5.4-1.41 1.4-5.4-5.39A8 8 0 1 1 10 2zm0 2a6 6 0 1 0 0 12 6 6 0 0 0 0-12z", "search-icon");
export const chevronIcon = () => svg("M7.4 9.3 12 13.9l4.6-4.6L18 10.7l-6 6-6-6z", "chevron");
/** Logo mark: a wrench ("fix it"). */
export const wrenchIcon = () => svg("M22.7 19 13.6 9.9c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z");
