// The ONLY file that calls fetch(). One function per backend call; every call returns parsed JSON (or null for 204)
// or throws ApiError { status, message, retryAfter }. Messages are safe to show to a person.

import { buildQuery } from "./util.js";

export class ApiError extends Error {
  constructor(status, message, { retryAfter = null } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.retryAfter = retryAfter;
  }
}

const GENERIC = "Something went wrong. Please try again.";
const OFFLINE = "Could not reach the server. Check your connection and try again.";

/** Statuses whose backend `message` is written for people (see the backend's GlobalExceptionHandler). */
const FRIENDLY_STATUSES = new Set([400, 401, 403, 404, 405, 409, 422, 429, 503]);

// TESTING-CONVENIENCE (per-tab sessions, see NOTES_AND_OPEN_DECISIONS.md D23). A browser shares one login cookie between all tabs of
// a site, so two tabs cannot be two different accounts. When the backend has the feature on, login/sign-up return a `sessionToken`;
// this tab keeps it in ITS OWN sessionStorage and sends it as "Authorization: Bearer ...", which beats the shared cookie.
// When the backend has the feature off, no token is returned and nothing here does anything.
const TOKEN_KEY = "fixit.tabSession";

function defaultStorage() {
  try { return globalThis.sessionStorage ?? null; } catch { return null; }      // can throw when site data is blocked
}

function defaultOnUnauthorized() {
  if (location.pathname.endsWith("/login.html")) return;
  const here = location.pathname + location.search + location.hash;
  location.assign("/login.html?next=" + encodeURIComponent(here));
}

export function createApi({ fetchImpl = (...args) => fetch(...args), onUnauthorized = defaultOnUnauthorized, storage = defaultStorage() } = {}) {
  const getToken = () => { try { return storage?.getItem(TOKEN_KEY) || null; } catch { return null; } };
  const setToken = (token) => { try { token ? storage?.setItem(TOKEN_KEY, token) : storage?.removeItem(TOKEN_KEY); } catch { /* storage unavailable: cookie login still works */ } };

  /**
   * @param auth false for login/register, where a 401 means "wrong password", not "session ended"
   */
  async function request(method, path, { body, query, auth = true } = {}, attempt = 0) {
    const headers = { Accept: "application/json" };
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    let response;
    try {
      response = await fetchImpl(path + buildQuery(query), {
        method,
        credentials: "same-origin",
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch {
      throw new ApiError(0, OFFLINE);
    }

    let data = null;
    if (response.status !== 204) {
      try { data = await response.json(); } catch { data = null; }
    }
    if (response.ok) return data;

    if (response.status === 401 && auth) {
      setToken(null);                                  // this tab's login is over (or its token expired)
      onUnauthorized();
      throw new ApiError(401, "Please sign in again.");
    }
    // One retry for "Conflicting update, please retry" (two identical requests racing on the same row).
    if (response.status === 409 && attempt === 0 && method !== "GET" && data?.message === "Conflicting update, please retry") {
      return request(method, path, { body, query, auth }, 1);
    }
    const retryAfter = Number(response.headers?.get?.("Retry-After")) || null;
    const message = FRIENDLY_STATUSES.has(response.status) && typeof data?.message === "string" && data.message ? data.message : GENERIC;
    throw new ApiError(response.status, message, { retryAfter });
  }

  /** Keeps the tab's token (if the backend issued one) from a login/sign-up response. */
  function remember(data) {
    if (data?.sessionToken) setToken(data.sessionToken);
    return data;
  }

  const get = (path, query, opts) => request("GET", path, { query, ...opts });
  const send = (method, path, body, opts) => request(method, path, { body, ...opts });

  return {
    request,
    /** TESTING-CONVENIENCE (D23): does THIS tab have its own login token? */
    hasTabToken: () => getToken() !== null,
    // --- account
    register: async (username, smail, password) => remember(await send("POST", "/api/auth/register", { username, smail, password }, { auth: false })),
    login: async (identifier, password) => remember(await send("POST", "/api/auth/login", { identifier, password }, { auth: false })),
    logout: async () => { try { return await send("POST", "/api/logout"); } finally { setToken(null); } },   // the request still carries the token so the server revokes it
    me: (opts) => get("/api/me", undefined, opts),
    myQuestions: (page = 0, size = 20) => get("/api/me/questions", { page, size }),
    // --- tags / lists / search
    tags: () => get("/api/tags"),
    listQuestions: ({ tags = [], page = 0, size = 20 } = {}) => get("/api/questions", { tags, page, size }),
    search: ({ q, tags = [], mode } = {}) => get("/api/search", { q, tags, mode }),
    // --- questions
    getQuestion: (id) => get(`/api/questions/${id}`),
    createQuestion: (title, body, tags) => send("POST", "/api/questions", { title, body, tags }),
    updateQuestion: (id, title, body, tags) => send("PUT", `/api/questions/${id}`, { title, body, tags }),
    acceptAnswer: (questionId, answerId) => send("PUT", `/api/questions/${questionId}/accepted-answer`, { answerId }),
    // --- answers
    listAnswers: (questionId) => get(`/api/questions/${questionId}/answers`),
    addAnswer: (questionId, body) => send("POST", `/api/questions/${questionId}/answers`, { body }),
    updateAnswer: (answerId, body) => send("PUT", `/api/answers/${answerId}`, { body }),
    // --- comments
    listComments: (questionId) => get(`/api/questions/${questionId}/comments`),
    addComment: (questionId, body) => send("POST", `/api/questions/${questionId}/comments`, { body }),
    replyToComment: (commentId, body) => send("POST", `/api/comments/${commentId}/replies`, { body }),
    updateComment: (commentId, body) => send("PUT", `/api/comments/${commentId}`, { body }),
    vote: (commentId, voteType) => send("PUT", `/api/comments/${commentId}/vote`, { voteType }),
    // --- "+"
    interest: (questionId) => get(`/api/questions/${questionId}/interest`),
    toggleInterest: (questionId) => send("POST", `/api/questions/${questionId}/interest`),
    // --- notifications
    notifications: ({ limit = 10, unreadOnly = false } = {}) => get("/api/notifications", { limit, unreadOnly: unreadOnly || undefined }),
    unreadCount: () => get("/api/notifications/unread-count"),
    markRead: (notificationId) => send("PUT", `/api/notifications/${notificationId}/read`),
  };
}

export const api = createApi();
