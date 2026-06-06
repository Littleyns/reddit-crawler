import { describe, it, expect } from "vitest";
import { buildExportUrl, filterBySearch } from "@/lib/utils";

describe("buildExportUrl", () => {
  it("preserves filters in export requests", () => {
    const url = buildExportUrl("json", "posts", {
      page: 2,
      pageSize: 25,
      search: "agent",
      subreddit: "machinelearning",
    });

    expect(url).toContain("format=json");
    expect(url).toContain("type=posts");
    expect(url).toContain("page=2");
    expect(url).toContain("pageSize=25");
    expect(url).toContain("search=agent");
    expect(url).toContain("subreddit=machinelearning");
  });
});

describe("filterBySearch", () => {
  it("matches across provided fields", () => {
    const rows = [
      { id: "1", title: "Crawler metrics", author: "amina" },
      { id: "2", title: "Dashboard layout", author: "yousef" },
    ];

    expect(filterBySearch(rows, "amina", ["title", "author"])).toEqual([rows[0]]);
    expect(filterBySearch(rows, "dashboard", ["title", "author"])).toEqual([rows[1]]);
  });
});
