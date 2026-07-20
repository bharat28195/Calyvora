"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, MailPlus, Trash2, UserPlus } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { inviteSchema } from "@/lib/validators";
import type { Invitation, Member } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Modal } from "@/components/ui/modal";

export default function MembersPage() {
  const [members, setMembers] = useState<Member[] | null>(null);
  const [invites, setInvites] = useState<Invitation[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);

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

  const loading = members === null || invites === null;

  return (
    <div>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Members</h1>
          <p className="mt-1 text-white/50">Your team and pending invitations.</p>
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
              <thead className="border-b border-white/10 text-xs uppercase tracking-wide text-white/40">
                <tr>
                  <th className="px-5 py-3 font-medium">Name</th>
                  <th className="px-5 py-3 font-medium">Email</th>
                  <th className="px-5 py-3 font-medium">Role</th>
                  <th className="px-5 py-3 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {members.map((m) => (
                  <tr key={m.id} className="border-b border-white/5 last:border-0">
                    <td className="px-5 py-3">{m.firstName} {m.lastName}</td>
                    <td className="px-5 py-3 text-white/70">{m.email}</td>
                    <td className="px-5 py-3"><Badge value={m.role} /></td>
                    <td className="px-5 py-3"><Badge value={m.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>

          <h2 className="mt-10 text-sm font-medium uppercase tracking-wide text-white/40">
            Pending invitations
          </h2>
          {invites.length === 0 ? (
            <Card className="mt-3 flex items-center gap-3 text-sm text-white/50">
              <MailPlus className="h-5 w-5 text-white/30" />
              No pending invitations. Invite someone to get started.
            </Card>
          ) : (
            <Card className="mt-3 overflow-hidden p-0">
              <table className="w-full text-left text-sm">
                <tbody>
                  {invites.map((inv) => (
                    <tr key={inv.id} className="border-b border-white/5 last:border-0">
                      <td className="px-5 py-3 text-white/80">{inv.email}</td>
                      <td className="px-5 py-3"><Badge value={inv.role} /></td>
                      <td className="px-5 py-3 text-white/40">
                        invited by {inv.invitedByEmail}
                      </td>
                      <td className="px-5 py-3 text-right">
                        <button
                          onClick={() => revoke(inv.id)}
                          className="inline-flex items-center gap-1 text-xs text-red-400 hover:text-red-300"
                        >
                          <Trash2 className="h-3.5 w-3.5" /> Revoke
                        </button>
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
          setInviteOpen(false);
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
  const [role, setRole] = useState<"ADMIN" | "MEMBER">("MEMBER");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

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
      await api.createInvitation(parsed.data.email, parsed.data.role);
      setEmail("");
      setRole("MEMBER");
      onInvited();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to send invitation");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Invite a team member">
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
            onChange={(e) => setRole(e.target.value as "ADMIN" | "MEMBER")}
            className="h-11 w-full rounded-lg border border-white/15 bg-white/5 px-3 text-sm text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
          >
            <option value="MEMBER" className="bg-ink">Member</option>
            <option value="ADMIN" className="bg-ink">Admin</option>
          </select>
        </Field>
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />} Send invite
          </Button>
        </div>
      </form>
    </Modal>
  );
}
