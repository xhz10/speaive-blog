import { describe, expect, it } from "vitest";

import {
  formatDateTime,
  formatDateTimeInput,
  normalizeInstant,
  parseDateTimeInput
} from "../src/lib/date-time";

describe("formatDateTime", () => {
  it("converts UTC instants to Asia/Shanghai time and includes seconds", () => {
    expect(formatDateTime(new Date("2026-08-07T01:02:03Z")))
      .toBe("2026/08/07 09:02:03");
  });

  it("uses the next calendar day when Shanghai time crosses midnight", () => {
    expect(formatDateTime(new Date("2026-08-07T16:05:06Z")))
      .toBe("2026/08/08 00:05:06");
  });

  it("formats UTC instants for a second-precision Shanghai date-time input", () => {
    expect(formatDateTimeInput(new Date("2026-08-07T09:05:06Z")))
      .toBe("2026-08-07T17:05:06");
  });

  it("converts Shanghai date-time input values back to UTC instants", () => {
    expect(parseDateTimeInput("2026-08-07T17:05:06").toISOString())
      .toBe("2026-08-07T09:05:06.000Z");
  });

  it("accepts date-time input values that omit zero seconds", () => {
    const parsed = parseDateTimeInput("2026-08-07T17:05");
    expect(parsed.toISOString()).toBe("2026-08-07T09:05:00.000Z");
    expect(formatDateTimeInput(parsed)).toBe("2026-08-07T17:05:00");
  });

  it("parses fractional seconds without breaking dirty-state snapshots", () => {
    expect(parseDateTimeInput("2026-08-07T17:05:06.123").toISOString())
      .toBe("2026-08-07T09:05:06.123Z");
  });

  it("rejects impossible Shanghai date-time input values", () => {
    expect(() => parseDateTimeInput("2026-02-30T17:05:06"))
      .toThrow(RangeError);
  });

  it("normalizes equivalent UTC instant strings for stable snapshots", () => {
    expect(normalizeInstant("2026-08-07T09:05:06Z"))
      .toBe(normalizeInstant("2026-08-07T09:05:06.000Z"));
  });
});
