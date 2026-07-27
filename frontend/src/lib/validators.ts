import { z } from "zod";

// Client-side schemas (UX). The server re-validates with Bean Validation (authority) — Sprint1 §12.

const email = z.string().trim().toLowerCase().email("Enter a valid email address").max(255);

const password = z
  .string()
  .min(10, "At least 10 characters")
  .regex(/[A-Za-z]/, "Include at least one letter")
  .regex(/[0-9]/, "Include at least one number");

const name = (label: string) =>
  z.string().trim().min(1, `${label} is required`).max(80);

export const registerSchema = z.object({
  companyName: z.string().trim().min(2, "Company name is too short").max(120),
  firstName: name("First name"),
  lastName: name("Last name"),
  email,
  password,
});
export type RegisterInput = z.infer<typeof registerSchema>;

export const loginSchema = z.object({
  email,
  password: z.string().min(1, "Password is required"),
});
export type LoginInput = z.infer<typeof loginSchema>;

export const inviteSchema = z.object({
  email,
  role: z.enum(["ADMIN", "HR", "MANAGER", "MEMBER"], { message: "Choose a role" }),
});
export type InviteInput = z.infer<typeof inviteSchema>;

export const acceptInviteSchema = z.object({
  firstName: name("First name"),
  lastName: name("Last name"),
  password,
});
export type AcceptInviteInput = z.infer<typeof acceptInviteSchema>;

export const settingsSchema = z.object({
  timezone: z.string().min(1, "Timezone is required").max(64),
  locale: z.enum(["en", "en-GB", "fr", "de", "es", "hi"]),
  currency: z.enum(["INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD"]),
  logoUrl: z
    .union([z.string().url("Must be a valid https URL").max(500), z.literal("")])
    .optional(),
});
export type SettingsInput = z.infer<typeof settingsSchema>;

/** Simple password strength estimate for the register form meter (0–4). */
export function passwordStrength(pw: string): number {
  let score = 0;
  if (pw.length >= 10) score++;
  if (pw.length >= 14) score++;
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) score++;
  if (/[0-9]/.test(pw) && /[^A-Za-z0-9]/.test(pw)) score++;
  return Math.min(score, 4);
}
