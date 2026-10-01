package com.calyvora.common.web;

import com.calyvora.common.dto.CursorPage;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Where a newest-first list was last read up to.
 *
 * <p>A cursor is the sort key of the last row handed out: its creation instant, and its id as a
 * tiebreaker. <b>The tiebreaker is not decoration.</b> These rows are frequently created in bulk —
 * a seeder, an import, an approval sweep — and land on the same microsecond. Ordering by timestamp
 * alone leaves their relative order undefined, so a page boundary falling inside such a group drops
 * some of them and repeats others, which looks exactly like data loss to whoever is reading the
 * queue. Ordering by {@code (createdAt, id)} makes the sequence total, and the cursor names a point
 * in it rather than a rough time.
 *
 * <p>The encoding is opaque on purpose: Base64 of an internal pair. It is not a promise about what
 * is inside, and a client that starts parsing it can be broken by a change of sort key. It is not
 * encrypted either, so nothing secret goes in — an id and a timestamp the caller already has.
 */
public final class Cursors {

    private Cursors() {
    }

    /**
     * The position a first read starts from: later than anything, so the first comparison excludes
     * nothing.
     *
     * <p>This exists so paging needs one query rather than two. The alternative is a separate
     * "first page" finder without the keyset predicate, and then two nearly-identical queries per
     * list that can drift apart — which is how a filter ends up applied on page two but not page one.
     */
    public static final Instant BEGINNING = Instant.parse("9999-12-31T23:59:59Z");

    /** Larger than any real UUID, for the same reason. */
    public static final UUID HIGHEST_ID = new UUID(-1L, -1L);

    /** One decoded cursor: read everything strictly older than this. */
    public record Position(Instant createdAt, UUID id) {
    }

    /** The start of a list, when the caller passed no cursor. */
    public static Position start() {
        return new Position(BEGINNING, HIGHEST_ID);
    }

    public static String encode(Instant createdAt, UUID id) {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decode a cursor the caller sent back, or start from the beginning when they sent none.
     *
     * <p>A malformed cursor is a 400 rather than a silent restart from the top. Silently restarting
     * makes a client bug look like an infinite list — it pages forever, always receiving the first
     * page — and that is far harder to find than an error naming the parameter.
     */
    public static Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return start();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int split = raw.lastIndexOf('|');
            if (split < 0) {
                throw new IllegalArgumentException("no separator");
            }
            return new Position(Instant.parse(raw.substring(0, split)),
                    UUID.fromString(raw.substring(split + 1)));
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "That 'cursor' is not one this list handed out. Omit it to start from the newest.");
        }
    }

    /** How many rows a caller may ask for at once, and what they get if they don't ask. */
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    /**
     * A sane page size, whatever the caller asked for.
     *
     * <p>Clamped rather than rejected: a limit of 10,000 is a client being optimistic, not an error
     * worth failing their screen over, and the ceiling is the point of the parameter existing.
     */
    public static int limit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    /**
     * Turn one over-fetched row into the answer to "is there more?".
     *
     * <p>Callers ask the database for {@code limit + 1} rows. Getting them all back means at least
     * one more exists, so the extra is dropped and a cursor is issued; getting fewer means this is
     * the end. Counting the whole table to answer the same question is a second query over
     * everything, on every page, to render one button.
     */
    public static <E, T> CursorPage<T> of(List<E> fetched, int limit,
                                          Function<E, T> render,
                                          Function<E, Instant> createdAt,
                                          Function<E, UUID> id) {
        boolean more = fetched.size() > limit;
        List<E> rows = more ? fetched.subList(0, limit) : fetched;
        String next = null;
        if (more && !rows.isEmpty()) {
            E last = rows.get(rows.size() - 1);
            next = encode(createdAt.apply(last), id.apply(last));
        }
        return new CursorPage<>(rows.stream().map(render).toList(), next);
    }
}
