/*
 * Copyright (c) 2006-2025 Chris Collins
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
package com.hitorro.jsontypesystem.grouppredicates;

import com.hitorro.jsontypesystem.Group;

import java.util.function.Predicate;

public class GroupNameFilter implements Predicate<Group> {
    // Raw Predicate declarations retained for compatibility with
    // BaseProjectionFactoryMapper.predicate, which is typed Predicate<BaseT> (BaseT is Group's
    // supertype, so the actual runtime instance works — but the compile-time types wouldn't line
    // up if we tightened these to Predicate<Group>).  Making them `final` is the useful
    // immutability win; the wider typing cleanup requires refactoring the visitor's predicate
    // parameter shape and is deferred.
    @SuppressWarnings("rawtypes") public static final Predicate indexFilter       = new GroupNameFilter("index");
    @SuppressWarnings("rawtypes") public static final Predicate enrichFilter      = new GroupNameFilter("enrich");
    @SuppressWarnings("rawtypes") public static final Predicate redactFilter      = new GroupNameFilter("redact");
    @SuppressWarnings("rawtypes") public static final Predicate validateFilter    = new GroupNameFilter("validate");
    @SuppressWarnings("rawtypes") public static final Predicate fingerprintFilter = new GroupNameFilter("fingerprint");
    @SuppressWarnings("rawtypes") public static final Predicate materializeFilter = new GroupNameFilter("materialize");
    @SuppressWarnings("rawtypes") public static final Predicate i18nFilter        = new GroupNameFilter("i18n");
    @SuppressWarnings("rawtypes") public static final Predicate vectorizeFilter   = new GroupNameFilter("vectorize");
    private String name;

    public GroupNameFilter(String name) {
        this.name = name;
    }

    @Override
    public boolean test(final Group group) {
        return name.equals(group.getName());
    }
}
