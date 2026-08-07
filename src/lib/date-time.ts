const BLOG_TIME_ZONE = "Asia/Shanghai";
const BLOG_TIME_OFFSET = "+08:00";
const DATE_TIME_INPUT_PATTERN = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.\d{1,3})?$/;

const BLOG_DATE_TIME_FORMATTER = new Intl.DateTimeFormat("zh-CN", {
  timeZone: BLOG_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23"
});

export function formatDateTime(date: Date): string {
  return BLOG_DATE_TIME_FORMATTER.format(date);
}

export function formatDateTimeInput(date: Date): string {
  const parts = new Map(
    BLOG_DATE_TIME_FORMATTER.formatToParts(date)
      .filter((part) => part.type !== "literal")
      .map((part) => [part.type, part.value])
  );
  return `${parts.get("year")}-${parts.get("month")}-${parts.get("day")}`
    + `T${parts.get("hour")}:${parts.get("minute")}:${parts.get("second")}`;
}

export function parseDateTimeInput(value: string): Date {
  const normalized = value.length === 16 ? `${value}:00` : value;
  const match = DATE_TIME_INPUT_PATTERN.exec(normalized);
  if (!match) {
    throw new RangeError("Invalid date-time input");
  }

  const date = new Date(`${normalized}${BLOG_TIME_OFFSET}`);
  if (Number.isNaN(date.valueOf()) || formatDateTimeInput(date) !== match[1]) {
    throw new RangeError("Invalid date-time input");
  }
  return date;
}

export function normalizeInstant(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) {
    throw new RangeError("Invalid instant");
  }
  return date.toISOString();
}
