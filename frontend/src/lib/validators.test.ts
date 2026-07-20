import { describe, expect, it } from "vitest";
import { registerSchema, loginSchema, inviteSchema, passwordStrength } from "./validators";

describe("registerSchema", () => {
  it("accepts a valid registration and normalizes the email", () => {
    const parsed = registerSchema.parse({
      companyName: "Acme Inc.",
      firstName: "Ada",
      lastName: "Lovelace",
      email: "  ADA@Acme.com ",
      password: "password1234",
    });
    expect(parsed.email).toBe("ada@acme.com");
  });

  it("rejects a password with no number", () => {
    const result = registerSchema.safeParse({
      companyName: "Acme",
      firstName: "Ada",
      lastName: "Lovelace",
      email: "ada@acme.com",
      password: "onlyletters",
    });
    expect(result.success).toBe(false);
  });

  it("rejects a too-short company name", () => {
    const result = registerSchema.safeParse({
      companyName: "A",
      firstName: "Ada",
      lastName: "Lovelace",
      email: "ada@acme.com",
      password: "password1234",
    });
    expect(result.success).toBe(false);
  });
});

describe("loginSchema", () => {
  it("requires a password", () => {
    expect(loginSchema.safeParse({ email: "ada@acme.com", password: "" }).success).toBe(false);
  });
});

describe("inviteSchema", () => {
  it("does not allow inviting an OWNER", () => {
    expect(inviteSchema.safeParse({ email: "x@y.com", role: "OWNER" }).success).toBe(false);
  });
  it("allows ADMIN and MEMBER", () => {
    expect(inviteSchema.safeParse({ email: "x@y.com", role: "ADMIN" }).success).toBe(true);
    expect(inviteSchema.safeParse({ email: "x@y.com", role: "MEMBER" }).success).toBe(true);
  });
});

describe("passwordStrength", () => {
  it("scores stronger passwords higher", () => {
    expect(passwordStrength("short")).toBeLessThan(passwordStrength("password1234"));
    expect(passwordStrength("L0ng-and-Str0ng-pass")).toBe(4);
  });
});
