// "3 hours ago" style times. The API sends ISO-8601 timestamps with an explicit +05:30 offset, so new Date(text) is exact.

const IST = "Asia/Kolkata";

function toDate(iso) {
  if (typeof iso !== "string" || iso.trim() === "") return null;      // new Date(null) would be 1970, not "no date"
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** Relative text; dates older than 30 days become "Oct 4 '26". `now` is injectable for tests. */
export function timeAgo(iso, now = Date.now()) {
  const date = toDate(iso);
  if (!date) return "";
  const seconds = Math.round((now - date.getTime()) / 1000);
  if (seconds < 45) return "just now";              // also covers small clock differences (negative values)
  const minutes = Math.round(seconds / 60);
  if (minutes < 2) return "1 min ago";
  if (minutes < 45) return minutes + " mins ago";
  const hours = Math.round(minutes / 60);
  if (hours < 2) return "1 hour ago";
  if (hours < 22) return hours + " hours ago";
  const days = Math.round(hours / 24);
  if (days < 2) return "1 day ago";
  if (days <= 30) return days + " days ago";
  return shortDate(iso);
}

/** "Oct 4 '26" (in IST). */
export function shortDate(iso) {
  const date = toDate(iso);
  if (!date) return "";
  const parts = new Intl.DateTimeFormat("en-US", { timeZone: IST, month: "short", day: "numeric", year: "2-digit" })
    .formatToParts(date)
    .reduce((acc, p) => ({ ...acc, [p.type]: p.value }), {});
  return `${parts.month} ${parts.day} '${parts.year}`;
}

/** Full time for tooltips, always in IST: "4 Oct 2026, 11:30 pm IST". */
export function formatAbsolute(iso) {
  const date = toDate(iso);
  if (!date) return "";
  return new Intl.DateTimeFormat("en-IN", { timeZone: IST, dateStyle: "medium", timeStyle: "short" }).format(date) + " IST";
}

/** True when two timestamps are more than a minute apart (used to decide whether to show "edited"/"modified"). */
export function differs(a, b) {
  const x = toDate(a), y = toDate(b);
  if (!x || !y) return false;
  return Math.abs(x.getTime() - y.getTime()) > 60 * 1000;
}
