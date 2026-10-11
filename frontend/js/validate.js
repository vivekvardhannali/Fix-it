// Form checks that mirror the backend's rules, so people get a message before a request is sent.
// The backend still has the last word: if it rejects something, its message is shown too.

export const LIMITS = Object.freeze({
  usernameMin: 3, usernameMax: 30,
  passwordMin: 8, passwordMaxBytes: 72,
  titleMax: 255, bodyMax: 20000, answerMax: 20000, commentMax: 5000, queryMax: 500,
  tagsMin: 1,
});
export const SMAIL_DOMAIN = "smail.iitm.ac.in";

const USERNAME = /^[A-Za-z0-9][A-Za-z0-9_.-]{2,29}$/;

function byteLength(text) {
  return new TextEncoder().encode(text).length;
}

/** Each validator returns an object {field: message}; empty object = valid. */
export function validateLogin({ identifier = "", password = "" }) {
  const errors = {};
  if (!identifier.trim()) errors.identifier = "Enter your username or smail.";
  if (!password) errors.password = "Enter your password.";
  return errors;
}

export function validateRegister({ username = "", smail = "", password = "" }) {
  const errors = {};
  const name = username.trim();
  if (!USERNAME.test(name)) {
    errors.username = "Username must be 3-30 characters: letters, digits, '.', '_' or '-', starting with a letter or digit.";
  }
  const mail = smail.trim().toLowerCase();
  const at = mail.lastIndexOf("@");
  if (at <= 0 || mail.slice(at + 1) !== SMAIL_DOMAIN) {
    errors.smail = `Use your IITM smail (name@${SMAIL_DOMAIN}).`;
  }
  if (password.length < LIMITS.passwordMin) {
    errors.password = `Password must be at least ${LIMITS.passwordMin} characters.`;
  } else if (byteLength(password) > LIMITS.passwordMaxBytes) {
    errors.password = `Password is too long (at most ${LIMITS.passwordMaxBytes} bytes).`;
  } else if (name && password.toLowerCase() === name.toLowerCase()) {
    errors.password = "Password must not be the same as your username.";
  }
  return errors;
}

export function validateQuestion({ title = "", body = "", tags = [] }, { maxTags = 4 } = {}) {
  const errors = {};
  if (!title.trim()) errors.title = "Enter a title.";
  else if (title.trim().length > LIMITS.titleMax) errors.title = `The title must be at most ${LIMITS.titleMax} characters.`;
  if (!body.trim()) errors.body = "Describe your question.";
  else if (body.trim().length > LIMITS.bodyMax) errors.body = `The description must be at most ${LIMITS.bodyMax} characters.`;
  if (!tags || tags.length < LIMITS.tagsMin) errors.tags = "Choose at least one tag.";
  else if (tags.length > maxTags) errors.tags = `Choose at most ${maxTags} tags.`;
  return errors;
}

export function validateAnswer(body = "") {
  if (!body.trim()) return { body: "Write your answer first." };
  if (body.trim().length > LIMITS.answerMax) return { body: `An answer must be at most ${LIMITS.answerMax} characters.` };
  return {};
}

export function validateComment(body = "") {
  if (!body.trim()) return { body: "Write your comment first." };
  if (body.trim().length > LIMITS.commentMax) return { body: `A comment must be at most ${LIMITS.commentMax} characters.` };
  return {};
}

export function validateSearchQuery(q = "") {
  if (!q.trim()) return "Type something to search.";
  if (q.trim().length > LIMITS.queryMax) return `Search text must be at most ${LIMITS.queryMax} characters.`;
  return "";
}
