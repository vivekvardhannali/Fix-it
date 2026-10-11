// Login and sign-up (username + password). A session cookie is set by the backend; afterwards we go to `next` or the homepage.

import { h, setChildren, whileBusy } from "../dom.js";
import { api } from "../api.js";
import { parseQuery, safeNext } from "../util.js";
import { validateLogin, validateRegister, SMAIL_DOMAIN } from "../validate.js";
import { textField, applyErrors, waitText } from "../components/form.js";
import { showMessage, clearMessage } from "../components/message-bar.js";

const next = safeNext(parseQuery(location.search).get("next"), "/");
const card = document.getElementById("login-card");
const messages = h("div", { "aria-live": "polite" });

function friendly(error) {
  if (error.status === 429) return `Too many attempts. Try again in ${waitText(error.retryAfter)}.`;
  return error.message;
}

// ---- log in
const login = {
  identifier: textField({ id: "login-identifier", label: "Username or smail", autocomplete: "username", maxlength: 255 }),
  password: textField({ id: "login-password", label: "Password", type: "password", autocomplete: "current-password", maxlength: 200 }),
};
const loginSubmit = h("button", { type: "submit", class: "btn btn-primary", id: "login-submit" }, "Log in");
const loginForm = h("form", { id: "login-form", novalidate: true }, login.identifier.element, login.password.element, loginSubmit);
loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearMessage(messages);
  const fields = { identifier: login.identifier, password: login.password };
  const values = { identifier: login.identifier.input.value, password: login.password.input.value };
  if (!applyErrors(fields, validateLogin(values))) return;
  await whileBusy(loginSubmit, async () => {
    try {
      await api.login(values.identifier.trim(), values.password);
      location.assign(next);
    } catch (e) {
      showMessage(messages, "error", friendly(e));
      login.password.input.select();
    }
  });
});

// ---- sign up
const signup = {
  username: textField({ id: "signup-username", label: "Username", autocomplete: "username", maxlength: 30, hint: "3-30 characters: letters, digits, . _ -" }),
  smail: textField({ id: "signup-smail", label: "IITM smail", type: "text", autocomplete: "email", maxlength: 255, hint: `Your @${SMAIL_DOMAIN} address. It is not verified by email.` }),
  password: textField({ id: "signup-password", label: "Password", type: "password", autocomplete: "new-password", maxlength: 200, hint: "At least 8 characters." }),
};
const signupSubmit = h("button", { type: "submit", class: "btn btn-primary", id: "signup-submit" }, "Sign up");
const signupForm = h("form", { id: "signup-form", novalidate: true, hidden: true }, signup.username.element, signup.smail.element, signup.password.element, signupSubmit);
signupForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearMessage(messages);
  const values = { username: signup.username.input.value, smail: signup.smail.input.value, password: signup.password.input.value };
  if (!applyErrors(signup, validateRegister(values))) return;
  await whileBusy(signupSubmit, async () => {
    try {
      await api.register(values.username.trim(), values.smail.trim(), values.password);   // also signs the new user in
      location.assign(next);
    } catch (e) {
      showMessage(messages, "error", friendly(e));
    }
  });
});

// ---- switching between the two
const toSignup = h("button", { type: "button", class: "btn btn-secondary", id: "show-signup", "aria-controls": "signup-form" }, "Create an account");
const toLogin = h("button", { type: "button", class: "btn btn-secondary", id: "show-login", "aria-controls": "login-form" }, "I already have an account");
const switchRow = h("div", { class: "switch" });

function show(mode) {
  const signingUp = mode === "signup";
  loginForm.hidden = signingUp;
  signupForm.hidden = !signingUp;
  clearMessage(messages);
  setChildren(switchRow, signingUp ? ["Already registered? ", toLogin] : ["New here? ", toSignup]);
  document.title = (signingUp ? "Sign up" : "Log in") + " - Fix It";
  (signingUp ? signup.username : login.identifier).input.focus();
  history.replaceState(null, "", location.pathname + location.search + (signingUp ? "#signup" : ""));
}
toSignup.addEventListener("click", () => show("signup"));
toLogin.addEventListener("click", () => show("login"));

setChildren(card,
  h("h1", {}, "Fix It"),
  h("p", { class: "tagline" }, "Ask. Search. Get it fixed."),
  messages, loginForm, signupForm, h("hr"), switchRow);
setChildren(switchRow, "New here? ", toSignup);
if (location.hash === "#signup") show("signup");

// TESTING-CONVENIENCE (D23). Already logged in? Skip the form - except in the per-tab testing mode, where a tab without a login of its own may be looking at
// the browser's shared cookie login and must still be able to log in as someone else.
try {
  const me = await api.me({ auth: false });
  if (me.perTabSessions && !api.hasTabToken()) {
    const who = me.username || "user" + me.userId;
    messages.after(h("div", { class: "msg msg--info", id: "already-logged-in", role: "status" },
      `This browser is already logged in as ${who}. `, h("a", { href: next, id: "continue-as" }, `Continue as ${who}`), " or log in below as someone else in this tab."));
  } else {
    location.replace(next);
  }
} catch { /* not logged in: stay */ }
