import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Renewing an expired access token (see `renewSession` in api.ts).
 *
 * <p>The bug this pins: the access token lives fifteen minutes and is held in memory only, and
 * nothing renewed it. After fifteen minutes every call returned 401, so screens started failing one
 * by one while the person was still apparently signed in — sidebar, name, everything — with no
 * indication that the session was the problem.
 *
 * <p>The dangerous half is the concurrency. A dashboard fires several calls at once; if each one
 * independently presented the refresh cookie, the second presentation would look like token reuse,
 * which the backend correctly treats as theft and answers by revoking the whole family. The naive
 * fix for "session expired" is therefore a way to sign everybody out. These tests exist so that
 * stays fixed.
 */
describe("api transport: an expired access token", () => {
    let fetchMock: ReturnType<typeof vi.fn>;

    async function loadApi() {
        vi.resetModules();
        vi.stubEnv("NEXT_PUBLIC_API_MODE", "live");
        return await import("./api");
    }

    function json(status: number, body: unknown) {
        return { status, ok: status >= 200 && status < 300, json: () => Promise.resolve(body) };
    }

    const expired = () =>
        json(401, { timestamp: "", status: 401, code: "UNAUTHORIZED", message: "Authentication required" });

    /** The URL of the nth fetch call, for asserting what was requested in what order. */
    const pathOf = (call: unknown[]) => String(call[0]);

    beforeEach(() => {
        fetchMock = vi.fn();
        vi.stubGlobal("fetch", fetchMock);
    });

    afterEach(() => {
        vi.unstubAllEnvs();
        vi.unstubAllGlobals();
    });

    /** Sign in first: renewal only applies to a session that exists. */
    async function signedIn(api: Awaited<ReturnType<typeof loadApi>>) {
        fetchMock.mockResolvedValueOnce(json(200, { accessToken: "first", me: { user: {}, company: {} } }));
        await api.api.login("a@b.c", "pw");
        fetchMock.mockClear();
    }

    it("renews once and replays the request, so the screen never sees the 401", async () => {
        const api = await loadApi();
        await signedIn(api);

        fetchMock
            .mockResolvedValueOnce(expired())
            .mockResolvedValueOnce(json(200, { accessToken: "second", me: { user: {}, company: {} } }))
            .mockResolvedValueOnce(json(200, { user: { email: "a@b.c" } }));

        await expect(api.api.me()).resolves.toEqual({ user: { email: "a@b.c" } });

        expect(fetchMock).toHaveBeenCalledTimes(3);
        expect(pathOf(fetchMock.mock.calls[1])).toContain("/auth/refresh");
        // The replay carries the NEW token. Retrying with the dead one would 401 again for ever.
        expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe("Bearer second");
    });

    it("refreshes once for a burst of calls, because a second presentation reads as theft", async () => {
        const api = await loadApi();
        await signedIn(api);

        fetchMock.mockImplementation((url: string) => {
            if (String(url).includes("/auth/refresh")) {
                return Promise.resolve(json(200, { accessToken: "second", me: { user: {}, company: {} } }));
            }
            // Every call fails until the token is renewed; after that they succeed.
            return Promise.resolve(api.auth.get() === "second" ? json(200, { ok: true }) : expired());
        });

        await Promise.all([api.api.me(), api.api.dashboardSummary(), api.api.notifications()]);

        const refreshes = fetchMock.mock.calls.filter((c) => pathOf(c).includes("/auth/refresh"));
        expect(refreshes).toHaveLength(1);
    });

    it("gives up after one renewal rather than looping for ever", async () => {
        const api = await loadApi();
        await signedIn(api);

        // A 401 that survives a fresh token is a real one — a revoked user, a lost role.
        fetchMock.mockImplementation((url: string) =>
            Promise.resolve(String(url).includes("/auth/refresh")
                ? json(200, { accessToken: "second", me: { user: {}, company: {} } })
                : expired()));

        await expect(api.api.me()).rejects.toMatchObject({ status: 401 });
        const attempts = fetchMock.mock.calls.filter((c) => !pathOf(c).includes("/auth/refresh"));
        expect(attempts).toHaveLength(2);
    });

    it("declares the session lost when the refresh itself fails, instead of retrying it", async () => {
        const api = await loadApi();
        await signedIn(api);

        const lost = vi.fn();
        api.setSessionLostHandler(lost);

        fetchMock.mockImplementation((url: string) =>
            Promise.resolve(String(url).includes("/auth/refresh") ? expired() : expired()));

        await expect(api.api.me()).rejects.toMatchObject({ status: 401 });
        expect(lost).toHaveBeenCalledTimes(1);
        // The refresh call must not itself try to renew — that is the loop this guards.
        const refreshes = fetchMock.mock.calls.filter((c) => pathOf(c).includes("/auth/refresh"));
        expect(refreshes).toHaveLength(1);
    });

    it("does not try to renew when nobody was signed in", async () => {
        const api = await loadApi();
        fetchMock.mockResolvedValue(expired());

        await expect(api.api.me()).rejects.toMatchObject({ status: 401 });
        expect(fetchMock.mock.calls.filter((c) => pathOf(c).includes("/auth/refresh"))).toHaveLength(0);
    });

    /**
     * The session bootstrap, which had its own way to the endpoint.
     *
     * <p>The tests above pinned the transport's retry and it held. What they could not see was a
     * second door: `api.refresh()` — what SessionProvider calls on every page load — went straight
     * to `/auth/refresh` and never touched the shared promise. On a hard load the two ran together,
     * because the shell asked for company features before a token existed, that 401'd, and the
     * transport started renewing while the bootstrap's own refresh was still in the air. Two
     * presentations of one rotating cookie is indistinguishable from a stolen token, so the server
     * revoked the family and signed the person out for reloading a page. Found on the live
     * deployment: three of four accounts were thrown back to the login screen on every navigation.
     */
    it("bootstrap and transport share one refresh, so the cookie is presented once", async () => {
        const api = await loadApi();
        await signedIn(api);

        let refreshes = 0;
        fetchMock.mockImplementation((url: string) => {
            if (String(url).includes("/auth/refresh")) {
                refreshes++;
                // A rotating cookie: the second presentation is theft, and the family is burned.
                return Promise.resolve(refreshes === 1
                    ? json(200, { accessToken: "second", me: { user: {}, company: {} } })
                    : expired());
            }
            return Promise.resolve(api.auth.get() === "second" ? json(200, { ok: true }) : expired());
        });

        // Exactly what a page load does: restore the session, while the shell asks for something.
        const [restored] = await Promise.all([api.api.refresh(), api.api.me()]);

        expect(refreshes).toBe(1);
        expect(restored.accessToken).toBe("second");
    });

    it("a failed bootstrap refresh reports the session lost and does not loop", async () => {
        const api = await loadApi();
        await signedIn(api);

        const lost = vi.fn();
        api.setSessionLostHandler(lost);
        fetchMock.mockResolvedValue(expired());

        await expect(api.api.refresh()).rejects.toMatchObject({ status: 401 });

        expect(lost).toHaveBeenCalledTimes(1);
        // One presentation only. A refresh that retries itself is the loop that burns the family.
        expect(fetchMock.mock.calls.filter((c) => pathOf(c).includes("/auth/refresh"))).toHaveLength(1);
    });
});
