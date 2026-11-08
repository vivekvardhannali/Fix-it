// Who is logged in. A 401 from any call sends the browser to the login page (see api.js).

import { api } from "./api.js";

let userPromise = null;

/** The logged-in user {userId, username, smail}; cached for the life of the page. */
export function currentUser() {
  if (!userPromise) {
    userPromise = api.me().catch((error) => {
      userPromise = null;
      throw error;
    });
  }
  return userPromise;
}

/** What to show for a person: their username (every account has one). */
export function displayName(user) {
  return user.username;
}

export async function logout() {
  try {
    await api.logout();
  } finally {
    userPromise = null;
    location.assign("/login.html");
  }
}
