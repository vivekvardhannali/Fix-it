// Small inline SVG icons (built with createElementNS, so there is no HTML parsing).
const NS = "http://www.w3.org/2000/svg";

function svg(path) {
  const el = document.createElementNS(NS, "svg");
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
