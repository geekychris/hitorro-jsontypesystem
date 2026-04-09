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
package com.hitorro.jsontypesystem.propreaders;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.util.core.diff.GenericDiffer;
import com.hitorro.util.core.diff.GenericDifferCallback;
import com.hitorro.util.core.iterator.AbstractIterator;
import com.hitorro.util.core.iterator.CollectionIterator;
import com.hitorro.util.core.params.ConfigChange;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessComp;
import com.hitorro.util.json.keys.propaccess.PropaccessError;
import com.hitorro.util.json.keys.propaccess.PropaccessIterator;

import java.util.ArrayList;
import java.util.List;

public class JVSConfigDiffer implements GenericDifferCallback<Propaccess> {
    private final JVS jvs1, jvs2;
    private final List<ConfigChange> changes = new ArrayList<>();

    public JVSConfigDiffer(final JVS jvs1, final JVS jvs2) {
        this.jvs1 = jvs1;
        this.jvs2 = jvs2;
    }

    @Override
    public void call(final Propaccess a, final Propaccess b, final GenericDiffer.Mode mode) {
        if (mode == GenericDiffer.Mode.Modify) {
            try {
                JsonNode n1 = jvs1.get(a);
                JsonNode n2 = jvs2.get(b);
                if (!n1.equals(n2)) {
                    changes.add(new ConfigChange(ConfigChange.ConfigChangeType.Updated, a.toString()));
                }
            } catch (PropaccessError e) {
                // ignore
            }
        } else if (mode == GenericDiffer.Mode.Add) {
            changes.add(new ConfigChange(ConfigChange.ConfigChangeType.Added, b.toString()));
        } else {
            changes.add(new ConfigChange(ConfigChange.ConfigChangeType.Deleted, a.toString()));
        }
    }

    public AbstractIterator<ConfigChange> getDiffs() {
        changes.clear();
        GenericDiffer differ = new GenericDiffer();
        PropaccessIterator iter1 = jvs1.getPropertyIter();
        PropaccessIterator iter2 = jvs2.getPropertyIter();
        differ.diff(iter1, iter2, PropaccessComp.comp, this);
        return new CollectionIterator<>(changes);
    }
}
