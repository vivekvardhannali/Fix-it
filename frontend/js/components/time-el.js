import { h } from "../dom.js";
import { timeAgo, formatAbsolute } from "../time.js";

/** <time> showing "3 hours ago", with the exact IST time on hover. */
export function timeEl(iso) {
  return h("time", { datetime: iso || undefined, title: formatAbsolute(iso) || undefined }, timeAgo(iso));
}
