import { describe, expect, it } from "vitest";
import { rupeesInWords } from "./words";

describe("rupeesInWords", () => {
  it("writes an amount the way a TRACES certificate does", () => {
    expect(rupeesInWords(170576)).toBe("One Lakh Seventy Thousand Five Hundred and Seventy Six Only");
  });

  it("handles crore, round figures, teens and zero", () => {
    expect(rupeesInWords(12345678)).toBe("One Crore Twenty Three Lakh Forty Five Thousand Six Hundred and Seventy Eight Only");
    expect(rupeesInWords(100000)).toBe("One Lakh Only");
    expect(rupeesInWords(1015)).toBe("One Thousand Fifteen Only");
    expect(rupeesInWords(305261.4)).toBe("Three Lakh Five Thousand Two Hundred and Sixty One Only");
    expect(rupeesInWords(0)).toBe("Zero Only");
  });
});
