import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * The read cache in api.ts: going back to a screen you just left should not wait on the network
 * again, and nothing written may ever be hidden behind a stale read.
 */
describe("api transport: recently read screens", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  async function loadApi() {
    vi.resetModules();
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "live");
    return await import("./api");
  }

  function json(status: number, body: unknown) {
    return { status, ok: status >= 200 && status < 300, json: () => Promise.resolve(body) };
  }

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it("answers a repeat read from memory, and two readers at once share one request", async () => {
    const { api } = await loadApi();
    fetchMock.mockResolvedValue(json(200, { headcount: 7 }));

    const [a, b] = await Promise.all([api.teamOverview(), api.teamOverview()]);
    const c = await api.teamOverview();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(a).toEqual(b);
    expect(c).toEqual({ headcount: 7 });
    // Each caller gets its own copy: one screen sorting a list must not reorder another's.
    expect(a).not.toBe(c);
  });

  it("forgets everything after any write, so a check-in shows straight away", async () => {
    const { api } = await loadApi();
    fetchMock
      .mockResolvedValueOnce(json(200, { status: null }))
      .mockResolvedValueOnce(json(200, { status: "PRESENT" }))
      .mockResolvedValueOnce(json(200, { status: "PRESENT" }));

    await api.attendanceToday();
    await api.checkIn();
    const after = await api.attendanceToday();

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(after).toEqual({ status: "PRESENT" });
  });

  it("goes back to the server once the cached answer is old", async () => {
    vi.useFakeTimers();
    const { api } = await loadApi();
    fetchMock.mockResolvedValue(json(200, { headcount: 7 }));

    await api.teamOverview();
    vi.advanceTimersByTime(21_000);
    await api.teamOverview();

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
