"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Mail, RefreshCw, Trash2 } from "lucide-react";
import { api } from "@/lib/api";
import { type MailMessage } from "@/lib/mock/backend";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";

/**
 * Dev-only mailbox. Verification and invite "emails" land here so you can click their links without
 * a real SMTP server. Works in both modes: the in-browser mock, and the live backend under the
 * `embedded` profile (served from `GET /api/v1/dev/mailbox`).
 */
export default function MailboxPage() {
  const [messages, setMessages] = useState<MailMessage[]>([]);

  const refresh = () => void api.devMailbox().then(setMessages).catch(() => setMessages([]));
  useEffect(refresh, []);

  return (
    <div className="mx-auto max-w-2xl px-6 py-12">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-semibold">
            <Mail className="h-6 w-6 text-aqua" /> Dev mailbox
          </h1>
          <p className="mt-1 text-sm text-fg/50">Mock emails (local dev only).</p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" size="sm" onClick={refresh}>
            <RefreshCw className="h-4 w-4" /> Refresh
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => void api.clearDevMailbox().then(refresh)}
          >
            <Trash2 className="h-4 w-4" /> Clear mailbox
          </Button>
        </div>
      </div>

      {messages.length === 0 ? (
        <Card className="text-center">
          <CardTitle>No messages yet</CardTitle>
          <CardDescription>
            Register a company or send an invitation, then come back here to click the link.
          </CardDescription>
        </Card>
      ) : (
        <div className="flex flex-col gap-3">
          {messages.map((m, i) => (
            <Card key={i} className="p-4">
              <div className="flex items-center justify-between text-xs text-fg/40">
                <span>To: {m.to}</span>
                <span>{new Date(m.sentAt).toLocaleTimeString()}</span>
              </div>
              <p className="mt-1 font-medium">{m.subject}</p>
              <Link href={m.link} className="mt-2 inline-block text-sm text-violet hover:underline">
                {m.link}
              </Link>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
