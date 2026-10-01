package com.calyvora.common.dto;

import java.util.List;
import java.util.function.Function;

/**
 * A slice of a list that grows forever, and where to continue from.
 *
 * <p>Distinct from {@link PageResponse}, which numbers pages. Numbered pages are right for a
 * directory — a fixed set somebody browses and wants to jump around in — and wrong for a queue.
 * Two reasons, both of which bite in practice:
 *
 * <ul>
 *   <li><b>Rows move.</b> These lists are newest-first and things are added to the top constantly.
 *       Read page 1, someone files a leave request, read page 2 — everything shifted down by one, so
 *       the last row of page 1 is also the first row of page 2. The reader sees it twice and never
 *       sees the row that got pushed past the boundary. A cursor names a position in the data rather
 *       than a count of rows, so insertions above it change nothing.
 *   <li><b>OFFSET is not free.</b> The database reaches row 20,000 by counting past the 19,999
 *       before it. Deep pages get slower the further in you go, which is precisely backwards.
 * </ul>
 *
 * <p>A null {@code nextCursor} means this is the end. That is the only way to know: a full page is
 * not a reliable signal, since the last page can be exactly full.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null);
    }

    /** Transform the entries, keeping the position — e.g. to redact fields per viewer. */
    public <R> CursorPage<R> map(Function<T, R> map) {
        return new CursorPage<>(items.stream().map(map).toList(), nextCursor);
    }
}
