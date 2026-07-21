"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Sparkles, X, Send, Loader2, FileText } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AssistantResponse, AssistantSource } from "@/lib/types";

type Turn = { role: "user" } | ({ role: "assistant" } & AssistantResponse) | { role: "error"; answer: string };

const SUGGESTIONS = [
  "How many open tickets do we have?",
  "How does our authentication work?",
  "Who's on the team?",
  "What's in the incident runbook?",
];

export function AssistantPanel() {
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState("");
  const [turns, setTurns] = useState<Turn[]>([]);
  const [loading, setLoading] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [turns, loading]);

  // Keep user-turn texts in a parallel list so we can render them.
  const [userTexts, setUserTexts] = useState<string[]>([]);
  let userIdx = -1;

  async function send(question: string) {
    const text = question.trim();
    if (!text || loading) return;
    setQ("");
    setUserTexts((u) => [...u, text]);
    setTurns((t) => [...t, { role: "user" }]);
    setLoading(true);
    try {
      const res = await api.askAssistant(text);
      setTurns((t) => [...t, { role: "assistant", ...res }]);
    } catch (err) {
      setTurns((t) => [...t, { role: "error", answer: err instanceof ApiError ? err.message : "Something went wrong." }]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      {!open && (
        <button
          onClick={() => setOpen(true)}
          className="fixed bottom-6 right-6 z-40 inline-flex items-center gap-2 rounded-full bg-gradient-to-r from-violet to-aqua px-4 py-3 text-sm font-medium text-white shadow-lg shadow-violet/30 hover:opacity-90"
        >
          <Sparkles className="h-4 w-4" /> Ask AI
        </button>
      )}

      {open && (
        <div className="fixed bottom-6 right-6 z-40 flex h-[min(560px,80vh)] w-[min(420px,92vw)] flex-col overflow-hidden rounded-2xl border border-white/10 bg-ink shadow-2xl">
          <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
            <div className="flex items-center gap-2 text-sm font-medium">
              <span className="grid h-6 w-6 place-items-center rounded-full bg-gradient-to-br from-violet to-aqua">
                <Sparkles className="h-3.5 w-3.5 text-white" />
              </span>
              Calyvora Assistant
            </div>
            <button onClick={() => setOpen(false)} className="text-white/40 hover:text-white" aria-label="Close">
              <X className="h-4 w-4" />
            </button>
          </div>

          <div ref={scrollRef} className="flex-1 space-y-4 overflow-y-auto px-4 py-4">
            {turns.length === 0 && (
              <div className="text-sm text-white/50">
                <p>Ask anything about your company — People, Work, or Knowledge. Answers are grounded in your real data.</p>
                <div className="mt-4 flex flex-col gap-2">
                  {SUGGESTIONS.map((s) => (
                    <button key={s} onClick={() => send(s)}
                      className="rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-left text-sm text-white/80 hover:bg-white/10">
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {turns.map((turn, i) => {
              if (turn.role === "user") {
                userIdx += 1;
                return (
                  <div key={i} className="flex justify-end">
                    <div className="max-w-[85%] rounded-2xl rounded-br-sm bg-violet px-3 py-2 text-sm text-white">
                      {userTexts[userIdx]}
                    </div>
                  </div>
                );
              }
              const isError = turn.role === "error";
              return (
                <div key={i} className="flex flex-col gap-2">
                  <div className="max-w-[90%] rounded-2xl rounded-bl-sm border border-white/10 bg-white/[0.03] px-3 py-2 text-sm">
                    <Markdown text={turn.answer} />
                    {!isError && "mode" in turn && (
                      <div className="mt-2 flex items-center gap-1.5 text-[10px] text-white/30">
                        <span className={`rounded-full px-1.5 py-0.5 ${turn.mode === "claude" ? "bg-violet/20 text-violet" : "bg-white/10"}`}>
                          {turn.mode === "claude" ? "Claude" : "grounded"}
                        </span>
                      </div>
                    )}
                  </div>
                  {!isError && "sources" in turn && turn.sources.length > 0 && (
                    <div className="flex flex-wrap gap-1.5">
                      {turn.sources.map((s: AssistantSource, j: number) => (
                        <Link key={j} href={s.href} onClick={() => setOpen(false)}
                          className="inline-flex items-center gap-1 rounded-full border border-white/10 bg-white/5 px-2 py-1 text-xs text-white/60 hover:bg-white/10">
                          <FileText className="h-3 w-3 text-emerald-400" /> {s.title}
                        </Link>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}

            {loading && (
              <div className="flex items-center gap-2 text-sm text-white/40">
                <Loader2 className="h-4 w-4 animate-spin" /> Thinking…
              </div>
            )}
          </div>

          <form
            onSubmit={(e) => { e.preventDefault(); send(q); }}
            className="flex items-center gap-2 border-t border-white/10 p-3"
          >
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Ask about your company…"
              className="h-10 flex-1 rounded-lg border border-white/10 bg-white/5 px-3 text-sm text-white placeholder:text-white/30 focus:border-violet focus:outline-none"
            />
            <button type="submit" disabled={loading || !q.trim()}
              className="grid h-10 w-10 place-items-center rounded-lg bg-violet text-white hover:bg-violet/90 disabled:opacity-40">
              <Send className="h-4 w-4" />
            </button>
          </form>
        </div>
      )}
    </>
  );
}

/** Minimal markdown: **bold** and line breaks. Input is our own grounded text, not user HTML. */
function Markdown({ text }: { text: string }) {
  const lines = text.split("\n");
  return (
    <div className="space-y-1 whitespace-pre-wrap break-words">
      {lines.map((line, i) => (
        <p key={i}>
          {line.split(/(\*\*[^*]+\*\*)/g).map((part, j) =>
            part.startsWith("**") && part.endsWith("**") ? (
              <strong key={j} className="font-semibold text-white">{part.slice(2, -2)}</strong>
            ) : (
              <span key={j}>{part}</span>
            ),
          )}
        </p>
      ))}
    </div>
  );
}
