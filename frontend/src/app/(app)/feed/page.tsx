"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Loader2, Send, Trash2, Pin, PinOff, Globe, Users2, MessageCircle,
  PartyPopper, Megaphone, HelpCircle, MessageSquare,
} from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { Department, Post, PostKind, PostVisibility } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { notificationAge } from "@/lib/notifications";

const KIND_META: Record<PostKind, { label: string; icon: React.ReactNode; accent: string }> = {
  UPDATE: { label: "Update", icon: <MessageSquare className="h-3.5 w-3.5" />, accent: "text-fg/50" },
  ANNOUNCEMENT: { label: "Announcement", icon: <Megaphone className="h-3.5 w-3.5" />, accent: "text-amber-400" },
  CELEBRATION: { label: "Celebration", icon: <PartyPopper className="h-3.5 w-3.5" />, accent: "text-violet" },
  QUESTION: { label: "Question", icon: <HelpCircle className="h-3.5 w-3.5" />, accent: "text-sky-400" },
};
const QUICK_EMOJI = ["👍", "🎉", "❤️", "👏", "🚀"];

/**
 * The company feed — birthdays, announcements, questions. Each post is either company-wide or
 * limited to one team, and the backend enforces that on read, not just in the UI.
 */
export default function FeedPage() {
  const { me } = useSession();
  const [posts, setPosts] = useState<Post[] | null>(null);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api.feed()
      .then(setPosts)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load the feed"));
  }, []);

  useEffect(() => {
    load();
    api.listDepartments().then(setDepartments).catch(() => {});
  }, [load]);

  /** Replace one post in place, so reacting doesn't scroll the feed back to the top. */
  const replace = (updated: Post) =>
    setPosts((cur) => cur?.map((p) => (p.id === updated.id ? updated : p)) ?? null);

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Feed</h1>
        <p className="mt-1 text-fg/50">What&apos;s happening across the company.</p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      <Composer departments={departments} onPosted={load} />

      {posts === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : posts.length === 0 ? (
        <Card className="mt-6 text-center text-sm text-fg/50">
          Nothing posted yet. Say hello 👋
        </Card>
      ) : (
        <div className="mt-6 flex flex-col gap-4">
          {posts.map((p) => (
            <PostCard key={p.id} post={p} isAdmin={me?.user.role === "OWNER" || me?.user.role === "ADMIN"}
              onChanged={replace} onDeleted={load} />
          ))}
        </div>
      )}
    </div>
  );
}

function Composer({ departments, onPosted }: { departments: Department[]; onPosted: () => void }) {
  const [body, setBody] = useState("");
  const [kind, setKind] = useState<PostKind>("UPDATE");
  const [visibility, setVisibility] = useState<PostVisibility>("COMPANY");
  const [departmentId, setDepartmentId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!body.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await api.createPost({
        body: body.trim(), kind,
        visibility,
        departmentId: visibility === "DEPARTMENT" ? departmentId : undefined,
      });
      setBody("");
      onPosted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to post");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="mt-8">
      <form onSubmit={submit}>
        {error && <Alert tone="error" className="mb-3">{error}</Alert>}
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={3}
          placeholder="Share something with the team…"
          className="w-full rounded-lg border border-fg/15 bg-fg/5 p-3 text-sm text-fg placeholder:text-fg/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
        />

        <div className="mt-3 flex flex-wrap items-center gap-2">
          {(Object.keys(KIND_META) as PostKind[]).map((k) => (
            <button
              key={k}
              type="button"
              onClick={() => setKind(k)}
              className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs transition-colors ${
                kind === k ? "bg-violet/10 font-medium text-violet" : "text-fg/50 hover:bg-fg/5 hover:text-fg"
              }`}
            >
              {KIND_META[k].icon} {KIND_META[k].label}
            </button>
          ))}

          <span className="ml-auto flex items-center gap-2">
            <select
              value={visibility}
              onChange={(e) => setVisibility(e.target.value as PostVisibility)}
              className="h-9 rounded-lg border border-fg/15 bg-fg/5 px-2 text-sm text-fg"
            >
              <option value="COMPANY" className="bg-surface">Everyone</option>
              <option value="DEPARTMENT" className="bg-surface">One team only</option>
            </select>
            {visibility === "DEPARTMENT" && (
              <select
                value={departmentId}
                onChange={(e) => setDepartmentId(e.target.value)}
                className="h-9 rounded-lg border border-fg/15 bg-fg/5 px-2 text-sm text-fg"
              >
                <option value="" className="bg-surface">Pick a team…</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id} className="bg-surface">{d.name}</option>
                ))}
              </select>
            )}
            <Button type="submit" disabled={busy || !body.trim()}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />} Post
            </Button>
          </span>
        </div>
      </form>
    </Card>
  );
}

function PostCard({
  post, isAdmin, onChanged, onDeleted,
}: { post: Post; isAdmin: boolean; onChanged: (p: Post) => void; onDeleted: () => void }) {
  const [comment, setComment] = useState("");
  const [showComments, setShowComments] = useState(post.comments.length > 0);
  const [busy, setBusy] = useState(false);
  const meta = KIND_META[post.kind];

  async function react(emoji: string) {
    onChanged(await api.reactToPost(post.id, emoji));
  }

  async function submitComment(e: React.FormEvent) {
    e.preventDefault();
    if (!comment.trim()) return;
    setBusy(true);
    try {
      onChanged(await api.commentOnPost(post.id, comment.trim()));
      setComment("");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className={post.pinned ? "border-violet/30" : undefined}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-violet/15 text-sm font-semibold text-violet">
            {initials(post.authorName)}
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{post.authorName}</p>
            <p className="flex flex-wrap items-center gap-1.5 text-xs text-fg/40">
              {post.authorTitle && <span className="truncate">{post.authorTitle}</span>}
              <span>· {notificationAge(post.createdAt)}</span>
              <span className={`inline-flex items-center gap-1 ${meta.accent}`}>· {meta.icon} {meta.label}</span>
              <span className="inline-flex items-center gap-1">
                ·{" "}
                {post.visibility === "COMPANY"
                  ? <><Globe className="h-3 w-3" /> Everyone</>
                  : <><Users2 className="h-3 w-3" /> {post.departmentName ?? "One team"}</>}
              </span>
            </p>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-1">
          {post.pinned && <span className="rounded-full bg-violet/10 px-2 py-0.5 text-[10px] text-violet">Pinned</span>}
          {isAdmin && (
            <button onClick={() => api.pinPost(post.id, !post.pinned).then(onChanged)}
              aria-label={post.pinned ? "Unpin" : "Pin"}
              className="rounded-md p-1.5 text-fg/40 hover:bg-fg/5 hover:text-fg">
              {post.pinned ? <PinOff className="h-4 w-4" /> : <Pin className="h-4 w-4" />}
            </button>
          )}
          {post.canManage && (
            <button onClick={() => confirm("Delete this post?") && api.deletePost(post.id).then(onDeleted)}
              aria-label="Delete post"
              className="rounded-md p-1.5 text-red-400/70 hover:bg-fg/5 hover:text-red-300">
              <Trash2 className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>

      <p className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-fg/85">{post.body}</p>

      <div className="mt-4 flex flex-wrap items-center gap-1.5">
        {QUICK_EMOJI.map((e) => {
          const count = post.reactions[e] ?? 0;
          const mine = post.myReactions.includes(e);
          return (
            <button
              key={e}
              onClick={() => react(e)}
              className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs transition-colors ${
                mine ? "border-violet/40 bg-violet/10 text-violet" : "border-fg/10 text-fg/50 hover:bg-fg/5"
              }`}
            >
              <span>{e}</span>
              {count > 0 && <span>{count}</span>}
            </button>
          );
        })}
        <button
          onClick={() => setShowComments((v) => !v)}
          className="ml-auto inline-flex items-center gap-1 text-xs text-fg/40 hover:text-fg"
        >
          <MessageCircle className="h-3.5 w-3.5" />
          {post.comments.length} {post.comments.length === 1 ? "comment" : "comments"}
        </button>
      </div>

      {showComments && (
        <div className="mt-4 border-t border-fg/5 pt-3">
          <div className="flex flex-col gap-2">
            {post.comments.map((c) => (
              <div key={c.id} className="flex items-start gap-2">
                <span className="mt-0.5 grid h-6 w-6 shrink-0 place-items-center rounded-full bg-fg/10 text-[10px] font-semibold text-fg/60">
                  {initials(c.authorName)}
                </span>
                <div className="min-w-0 flex-1 rounded-lg bg-fg/5 px-3 py-2">
                  <p className="text-xs">
                    <span className="font-medium">{c.authorName}</span>
                    <span className="text-fg/30"> · {notificationAge(c.createdAt)}</span>
                  </p>
                  <p className="mt-0.5 whitespace-pre-wrap text-sm text-fg/80">{c.body}</p>
                </div>
                {c.canDelete && (
                  <button onClick={() => api.deletePostComment(c.id).then(onDeleted)}
                    aria-label="Delete comment" className="mt-1 text-fg/25 hover:text-red-300">
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                )}
              </div>
            ))}
          </div>

          <form onSubmit={submitComment} className="mt-3 flex gap-2">
            <Input value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Write a comment…" />
            <Button type="submit" variant="secondary" disabled={busy || !comment.trim()}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            </Button>
          </form>
        </div>
      )}
    </Card>
  );
}

function initials(name: string): string {
  return name.split(" ").filter(Boolean).slice(0, 2).map((p) => p[0]).join("").toUpperCase();
}
