/*
 * Copyright (c) 2006-2026 Chris Collins
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hitorro.jsontypesystem.executors;

import com.hitorro.jsontypesystem.BaseT;
import com.hitorro.jsontypesystem.Group;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reusable {@link Predicate}s for narrowing an {@link ExecutionBuilder}
 * (or any traversal that takes a {@code Predicate<BaseT>}) to a subset
 * of {@link Group}s selected by their {@code tags}.
 *
 * <p>The typical composition — "run groups tagged with any of X, plus
 * untagged groups" — is exposed as {@link #anyOfOrUntagged(Set)} so
 * callers don't have to spell out the or-chain themselves.</p>
 *
 * <p>All predicates return {@code false} for non-Group {@link BaseT}
 * subclasses (Type, Field). Combine with a Group-aware traversal like
 * {@link BaseProjectionFactoryMapper#setPredicate}.</p>
 */
public final class GroupTagPredicates {

    private GroupTagPredicates() { }

    /** Every {@link Group}, regardless of tags. */
    public static Predicate<BaseT> anyGroup() {
        return b -> b instanceof Group;
    }

    /** Groups whose {@link Group#getTags()} include at least one of {@code tags}. */
    public static Predicate<BaseT> anyOf(Set<String> tags) {
        Set<String> copy = Set.copyOf(tags);
        return b -> {
            if (!(b instanceof Group g)) return false;
            List<String> gt = g.getTags();
            if (gt == null) return false;
            for (String t : gt) if (copy.contains(t)) return true;
            return false;
        };
    }

    /** Groups with no tags declared (null or empty). */
    public static Predicate<BaseT> untagged() {
        return b -> {
            if (!(b instanceof Group g)) return false;
            List<String> gt = g.getTags();
            return gt == null || gt.isEmpty();
        };
    }

    /**
     * The common combination used to run tagged enrichment: match any of
     * the requested tags OR run the untagged fallbacks. When {@code tags}
     * is null/empty this degrades to {@link #anyGroup()}.
     */
    public static Predicate<BaseT> anyOfOrUntagged(Set<String> tags) {
        if (tags == null || tags.isEmpty()) return anyGroup();
        return anyOf(tags).or(untagged());
    }
}
