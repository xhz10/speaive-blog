import { describe, expect, it } from "vitest";

import { formatDateTime } from "../src/lib/date-time";

describe("formatDateTime", () => {
  it("converts UTC instants to Asia/Shanghai time and includes seconds", () => {
    expect(formatDateTime(new Date("2026-08-07T01:02:03Z")))
      .toBe("2026/08/07 09:02:03");
  });

  it("uses the next calendar day when Shanghai time crosses midnight", () => {
    expect(formatDateTime(new Date("2026-08-07T16:05:06Z")))
      .toBe("2026/08/08 00:05:06");
  });
});
