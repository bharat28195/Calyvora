import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * The waking-backend retry (see `shouldRetry` in api.ts).
 *
 * <p>The bug this pins: the hosted backend sleeps when idle and takes the better part of a minute to
 * return. While it is down the Next proxy cannot reach it and answers with a non-JSON 500 — so a
 * signed-in person coming back to the tab had their session refresh fail, was declared logged out,
 * and was told "Request failed". Nothing was actually wrong with their account.
 *
 * <p>The safety argument the retry rests on is that a non-JSON error body cannot have come from the
 * application (every error it raises is JSON), so the request never arrived and cannot have been
 * half-applied. These tests exist to stop that distinction being quietly widened later.
 */
describe("api transport: retrying a backend that is still waking up", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  /** Import api.ts fresh, in live mode, with fake timers already installed. */
  async function loadApi() {
    vi.resetModules();
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "live");
    return await import("./api");
  }

  /** A response the Next proxy produces when it cannot reach the backend: 500, non-JSON body. */
  function unreachable() {
    return { status: 500, ok: false, json: () => Promise.reject(new Error("not json")) };
  }

  function json(status: number, body: unknown) {
    return { status, ok: status >= 200 && status < 300, json: () => Promise.resolve(body) };
  }

  beforeEach(() => {
    vi.useFakeTimers();
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it("keeps trying while the proxy cannot reach the backend, then succeeds", async () => {
    const api = await loadApi();
    fetchMock
      .mockResolvedValueOnce(unreachable())
      .mockResolvedValueOnce(unreachable())
      .mockResolvedValueOnce(json(200, { user: { email: "a@b.c" } }));

    const promise = api.api.me();
    await vi.runAllTimersAsync();

    await expect(promise).resolves.toEqual({ user: { email: "a@b.c" } });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("gives up eventually rather than retrying forever, and says the server was unreachable", async () => {
    const api = await loadApi();
    fetchMock.mockResolvedValue(unreachable());

    const promise = api.api.me();
    const assertion = expect(promise).rejects.toMatchObject({
      code: "INFRASTRUCTURE",
      message: expect.stringContaining("could not be reached"),
    });
    await vi.runAllTimersAsync();
    await assertion;

    // Bounded: the six backoff steps plus the original attempt.
    expect(fetchMock).toHaveBeenCalledTimes(7);
  });

  it("does not retry a rate limit — repeating it is what extends it", async () => {
    const api = await loadApi();
    fetchMock.mockResolvedValue({ status: 429, ok: false, json: () => Promise.reject(new Error("not json")) });

    const promise = api.api.me();
    const assertion = expect(promise).rejects.toMatchObject({
      code: "INFRASTRUCTURE",
      message: expect.stringContaining("Too many attempts"),
    });
    await vi.runAllTimersAsync();
    await assertion;

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("does not retry a genuine application error, which is JSON and would only fail again", async () => {
    const api = await loadApi();
    fetchMock.mockResolvedValue(json(500, {
      timestamp: "", status: 500, code: "INTERNAL_ERROR", message: "Something broke",
    }));

    const promise = api.api.me();
    const assertion = expect(promise).rejects.toMatchObject({ message: "Something broke" });
    await vi.runAllTimersAsync();
    await assertion;

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("does not multiply traffic when several calls hit a sleeping backend at once", async () => {
    // The failure this prevents, and the reason it matters more than the cold start itself: a screen
    // loading three calls, each running its own six-step ladder, turns one cold start into 21
    // requests. That reads as abuse to the rate limiter in front of the app, which then answers 429
    // to everything — including the login the person is actually trying to use. The retry meant to
    // hide a cold start would have caused a harder outage than the cold start.
    const api = await loadApi();
    fetchMock.mockResolvedValue(unreachable());

    const all = Promise.allSettled([api.api.me(), api.api.me(), api.api.me()]);
    await vi.runAllTimersAsync();
    const results = await all;

    // One caller works the ladder (7 attempts); the other two sleep through it and spend a single
    // attempt each. Three independent ladders would be 3 x 7 = 21.
    expect(fetchMock.mock.calls.length).toBeLessThanOrEqual(12);
    expect(results.every((r) => r.status === "rejected")).toBe(true);
  });

  it("a follower still gets its answer once the backend comes up", async () => {
    // Sleeping through someone else's wait must not mean giving up: the whole point is that by the
    // time the leader has finished waiting, one more attempt is all anybody needs.
    const api = await loadApi();
    let calls = 0;
    fetchMock.mockImplementation(() => {
      calls += 1;
      return Promise.resolve(calls <= 3 ? unreachable() : json(200, { ok: true }));
    });

    const all = Promise.all([api.api.me(), api.api.me(), api.api.me()]);
    await vi.runAllTimersAsync();

    await expect(all).resolves.toEqual([{ ok: true }, { ok: true }, { ok: true }]);
  });

  it("passes a 401 straight through, so a real logout is still a logout", async () => {
    const api = await loadApi();
    fetchMock.mockResolvedValue(json(401, {
      timestamp: "", status: 401, code: "UNAUTHORIZED", message: "Authentication required",
    }));

    const promise = api.api.me();
    const assertion = expect(promise).rejects.toMatchObject({ code: "UNAUTHORIZED" });
    await vi.runAllTimersAsync();
    await assertion;

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
