"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Check, Link as LinkIcon, Loader2, MailPlus, Trash2, UserPlus, Mail } from "lucide-react";
import { api, ApiError, isLive } from "@/lib/api";
import { inviteSchema } from "@/lib/validators";
import type { Invitation, Member } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Modal } from "@/components/ui/modal";

// The dev mailbox is a local-development convenience with no equivalent on a deployed backend, so
// pointing a real admin at it would only confuse them.
const showDevMailbox = !isLive || process.env.NODE_ENV !== "production";

export default function MembersPage() {
  const [members, setMembers] = useState<Member[] | null>(null);
  const [invites, setInvites] = useState<Invitation[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [m, i] = await Promise.all([api.listMembers(), api.listInvitations()]);
      setMembers(m);
      setInvites(i);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load members");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function revoke(id: string) {
    await api.revokeInvitation(id);
    void load();
  }

  /**
   * Put a fresh joining link on the clipboard. The token is stored hashed, so the original can't be
   * read back — this issues a new one, which also invalidates a link that went to the wrong place.
   */
  async function copyLink(id: string) {
    setError(null);
    try {
      const invitation = await api.invitationLink(id);
      if (!invitation.acceptUrl) throw new Error("no link");
      await navigator.clipboard.writeText(invitation.acceptUrl);
      setCopiedId(id);
      setTimeout(() => setCopiedId((c) => (c === id ? null : c)), 2500);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Couldn't copy the invite link");
    }
  }

  const loading = members === null || invites === null;

  return (
    <div>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Members</h1>
          <p className="mt-1 text-fg/50">Your team and pending invitations.</p>
        </div>
        <Button onClick={() => setInviteOpen(true)}>
          <UserPlus className="h-4 w-4" /> Invite
        </Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {loading ? (
        <Card className="mt-8">
          <Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" />
        </Card>
      ) : (
        <>
          <Card className="mt-8 overflow-hidden p-0">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-fg/10 text-xs uppercase tracking-wide text-fg/40">
                <tr>
                  <th className="px-5 py-3 font-medium">Name</th>
                  <th className="px-5 py-3 font-medium">Email</th>
                  <th className="px-5 py-3 font-medium">Role</th>
                  <th className="px-5 py-3 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {members.map((m) => (
                  <tr key={m.id} className="border-b border-fg/5 last:border-0">
                    <td className="px-5 py-3">{m.firstName} {m.lastName}</td>
                    <td className="px-5 py-3 text-fg/70">{m.email}</td>
                    <td className="px-5 py-3"><Badge value={m.role} /></td>
                    <td className="px-5 py-3"><Badge value={m.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>

          <h2 className="mt-10 text-sm font-medium uppercase tracking-wide text-fg/40">
            Pending invitations
          </h2>
          {invites.length === 0 ? (
            <Card className="mt-3 flex items-center gap-3 text-sm text-fg/50">
              <MailPlus className="h-5 w-5 text-fg/30" />
              No pending invitations. Invite someone to get started.
            </Card>
          ) : (
            <Card className="mt-3 overflow-hidden p-0">
              <table className="w-full text-left text-sm">
                <tbody>
                  {invites.map((inv) => (
                    <tr key={inv.id} className="border-b border-fg/5 last:border-0">
                      <td className="px-5 py-3 text-fg/80">{inv.email}</td>
                      <td className="px-5 py-3"><Badge value={inv.role} /></td>
                      <td className="px-5 py-3 text-fg/40">
                        invited by {inv.invitedByEmail}
                      </td>
                      <td className="px-5 py-3 text-right">
                        <div className="flex items-center justify-end gap-3">
                          {/* Joining doesn't depend on the email arriving — the admin can copy the
                              link and send it any way they like. */}
                          <button
                            onClick={() => copyLink(inv.id)}
                            className="inline-flex items-center gap-1 text-xs text-fg/60 hover:text-fg"
                          >
                            {copiedId === inv.id
                              ? <><Check className="h-3.5 w-3.5 text-emerald-400" /> Copied</>
                              : <><LinkIcon className="h-3.5 w-3.5" /> Copy invite link</>}
                          </button>
                          <button
                            onClick={() => revoke(inv.id)}
                            className="inline-flex items-center gap-1 text-xs text-red-400 hover:text-red-300"
                          >
                            <Trash2 className="h-3.5 w-3.5" /> Revoke
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          )}
        </>
      )}

      <InviteDialog
        open={inviteOpen}
        onClose={() => setInviteOpen(false)}
        onInvited={() => {
          void load();
        }}
      />
    </div>
  );
}

function InviteDialog({
  open,
  onClose,
  onInvited,
}: {
  open: boolean;
  onClose: () => void;
  onInvited: () => void;
}) {
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<"ADMIN" | "HR" | "MANAGER" | "MEMBER">("MEMBER");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [link, setLink] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  function reset() {
    setEmail("");
    setRole("MEMBER");
    setError(null);
    setSentTo(null);
    setLink(null);
    setCopied(false);
  }

  function close() {
    reset();
    onClose();
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const parsed = inviteSchema.safeParse({ email, role });
    if (!parsed.success) {
      setError(parsed.error.issues[0].message);
      return;
    }
    setBusy(true);
    try {
      const invitation = await api.createInvitation(parsed.data.email, parsed.data.role);
      setSentTo(parsed.data.email);
      setLink(invitation.acceptUrl ?? null);
      onInvited();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to send invitation");
    } finally {
      setBusy(false);
    }
  }

  if (sentTo) {
    return (
      <Modal open={open} onClose={close} title="Invitation sent">
        <div className="flex flex-col gap-4">
          <Alert tone="success">
            We&apos;ve emailed an invitation to <span className="font-medium">{sentTo}</span>.
          </Alert>

          {/* Always offer the link too. Mail can be slow, land in spam, or not be configured yet —
              and none of that should stop someone joining while you're sat next to them. */}
          {link && (
            <div className="rounded-lg border border-fg/10 bg-fg/5 p-4 text-sm text-fg/70">
              <p className="font-medium text-fg">Or share the link directly</p>
              <p className="mt-1.5">
                They&apos;ll set their own password and be signed in. The link works once and expires
                in 7 days.
              </p>
              <div className="mt-3 flex items-center gap-2">
                <code className="min-w-0 flex-1 truncate rounded-md bg-fg/10 px-2 py-1.5 text-xs">
                  {link}
                </code>
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={async () => {
                    await navigator.clipboard.writeText(link);
                    setCopied(true);
                    setTimeout(() => setCopied(false), 2500);
                  }}
                >
                  {copied ? <><Check className="h-4 w-4 text-emerald-400" /> Copied</> : <><LinkIcon className="h-4 w-4" /> Copy</>}
                </Button>
              </div>
            </div>
          )}

          {showDevMailbox && (
            <p className="text-xs text-fg/40">
              Local dev: the message is also in the{" "}
              <Link href="/dev/mailbox" target="_blank" className="text-violet hover:underline">dev mailbox</Link>.
            </p>
          )}

          <div className="mt-1 flex justify-end gap-2">
            <Button variant="ghost" onClick={() => reset()}>Invite another</Button>
            <Button onClick={close}>Done</Button>
          </div>
        </div>
      </Modal>
    );
  }

  return (
    <Modal open={open} onClose={close} title="Invite a team member">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Email" htmlFor="invite-email">
          <Input id="invite-email" type="email" value={email} placeholder="teammate@acme.com"
            onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Role" htmlFor="invite-role">
          <select
            id="invite-role"
            value={role}
            onChange={(e) => setRole(e.target.value as "ADMIN" | "HR" | "MANAGER" | "MEMBER")}
            className="h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
          >
            <option value="MEMBER" className="bg-surface">Member — self-service only</option>
            <option value="MANAGER" className="bg-surface">Manager — leads a team</option>
            <option value="HR" className="bg-surface">HR — people, payroll, recruiting</option>
            <option value="ADMIN" className="bg-surface">Admin — full company access</option>
          </select>
        </Field>
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={close}>Cancel</Button>
          <Button type="submit" disabled={busy}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />} Send invite
          </Button>
        </div>
      </form>
    </Modal>
  );
}
