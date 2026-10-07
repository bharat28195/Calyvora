import { describe, expect, it } from "vitest";
import { translate } from "@/lib/i18n";
import { formatDate, formatTime, setLocaleConfig, withLocale } from "@/lib/format";

describe("translate", () => {
  it("is English by default and for anything untranslated", () => {
    expect(translate(undefined, "Log out")).toBe("Log out");
    expect(translate("en", "Log out")).toBe("Log out");
    expect(translate("hi", "A phrase nobody has translated")).toBe("A phrase nobody has translated");
    expect(translate("klingon", "Log out")).toBe("Log out");
  });

  it("uses the chosen language and fills placeholders", () => {
    expect(translate("hi", "Log out")).toBe("लॉग आउट");
    expect(translate("es", "Settings")).toBe("Configuración");
    expect(translate("de", "Company default ({zone})", { zone: "UTC" })).toBe("Unternehmensstandard (UTC)");
  });
});

describe("dates and times", () => {
  setLocaleConfig({ currency: "INR", timezone: "America/New_York", language: "en", dateFormat: null, timeFormat: null });

  it("writes a calendar day as that day everywhere, in the chosen order", () => {
    // A west-of-UTC timezone must not turn the 7th into the 6th.
    expect(withLocale({ dateFormat: "DMY" }, () => formatDate("2026-10-07"))).toBe("07/10/2026");
    expect(withLocale({ dateFormat: "MDY" }, () => formatDate("2026-10-07"))).toBe("10/07/2026");
    expect(withLocale({ dateFormat: "YMD" }, () => formatDate("2026-10-07"))).toBe("2026-10-07");
  });

  it("puts timestamps in the person's timezone, 12- or 24-hour", () => {
    const at = "2026-10-07T18:30:00Z"; // 14:30 in New York
    expect(withLocale({ timeFormat: "H24" }, () => formatTime(at))).toContain("14:30");
    expect(withLocale({ timeFormat: "H12" }, () => formatTime(at)).toLowerCase()).toMatch(/02:30\s?pm/);
    expect(withLocale({ timezone: "Asia/Kolkata", timeFormat: "H24" }, () => formatTime(at))).toContain("00:00");
  });
});
