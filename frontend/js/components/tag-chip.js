import { h } from "../dom.js";

export const tagChip = (name) => h("span", { class: "tag" }, name);

export const tagList = (tags = []) => h("span", { class: "tags", "aria-label": "Tags" }, tags.map(tagChip));
