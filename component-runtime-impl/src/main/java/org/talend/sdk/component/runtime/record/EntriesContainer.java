/**
 * Copyright (C) 2006-2022 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.runtime.record;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.talend.sdk.component.api.record.Schema;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class EntriesContainer {

    @AllArgsConstructor
    static class EntryNode implements Iterable<EntryNode> {

        @Getter
        public Schema.Entry entry;

        public EntryNode next;

        public EntryNode prec;

        public EntryNode insert(final Schema.Entry newEntry) {
            final EntryNode newNode = new EntryNode(newEntry, this.next, this);
            return this.insert(newNode);
        }

        public EntryNode insert(final EntryNode newNode) {
            if (newNode == this) {
                return newNode;
            }
            if (this.next != null) {
                this.next.prec = newNode;
            }
            newNode.prec = this;
            newNode.next = this.next;
            this.next = newNode;

            return newNode;
        }

        public void remove() {
            if (next != null) {
                this.next.prec = this.prec;
            }
            if (this.prec != null) {
                this.prec.next = this.next;
            }
            this.next = null;
            this.prec = null;
        }

        @Override
        public Iterator<EntryNode> iterator() {
            return new EntryNode.EntryNodeIterator(this);
        }

        @AllArgsConstructor
        static class EntryNodeIterator implements Iterator<EntryNode> {

            private EntryNode current;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public EntryNode next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException("no further node");
                }
                final EntryNode next = this.current;
                this.current = next.next;
                return next;
            }
        }
    }

    private EntryNode first;

    private EntryNode last;

    private Map<String, EntryNode> entryIndex;

    public EntriesContainer() {
        this(Collections.emptyList());
    }

    public EntriesContainer(final Iterable<Schema.Entry> inputEntries) {
        this.entryIndex = new HashMap<>();
        inputEntries.forEach(this::addEntry);
    }

    public Stream<Schema.Entry> getEntries() {
        if (this.first == null) {
            return Stream.empty();
        }
        return StreamSupport.stream(this.first.spliterator(), false)
                .map(EntryNode::getEntry);
    }

    public void forEachEntry(final Consumer<Schema.Entry> entryConsumer) {
        if (this.first != null) {
            this.first.forEach((EntryNode node) -> entryConsumer.accept(node.entry));
        }
    }

    public void removeEntry(final Schema.Entry schemaEntry) {
        final EntryNode entry = this.entryIndex.remove(schemaEntry.getName());
        if (entry == null) {
            throw new IllegalArgumentException(
                    "No entry '" + schemaEntry.getName() + "' expected in entries");
        }
        this.removeFromChain(entry);
    }

    private void removeFromChain(final EntryNode node) {
        if (this.first == node) {
            this.first = node.next;
        }
        if (this.last == node) {
            this.last = node.prec;
        }
        node.remove();
    }

    public Schema.Entry getEntry(final String name) {
        if (this.entryIndex != null) {
            return Optional.ofNullable(this.entryIndex.get(name)).map(EntryNode::getEntry).orElse(null);
        } else {
            return null;
        }
    }

    public void replaceEntry(final String name, final Schema.Entry newEntry) {
        final EntryNode entryNode = this.entryIndex.remove(name);
        if (entryNode != null) {
            entryNode.entry = newEntry;
            this.entryIndex.put(newEntry.getName(), entryNode);
        }
    }

    public void addEntry(final Schema.Entry entry) {
        if (this.entryIndex != null && this.entryIndex.containsKey(entry.getName())) {
            return;
        }
        if (this.first == null) {
            this.first = new EntryNode(entry, null, null);
            this.last = this.first;
            this.entryIndex.put(entry.getName(), this.first);
        } else {
            final EntryNode newNode = this.last.insert(entry);
            this.entryIndex.put(entry.getName(), newNode);
            this.last = newNode;
        }
    }

    public void moveAfter(final String name, final Schema.Entry newEntry) {
        final EntryNode entryPivot = this.entryIndex.get(name);
        if (entryPivot == null) {
            throw new IllegalArgumentException(String.format("%s not in schema", name));
        }
        final EntryNode entryToMove = this.entryIndex.get(newEntry.getName());

        this.removeFromChain(entryToMove);
        entryPivot.insert(entryToMove);
        if (entryPivot == this.last) {
            this.last = entryToMove;
        }
    }

    /**
     * New Entry should take place before 'name' entry
     *
     * @param name : entry to move before (pivot).
     * @param newEntry : entry to move.
     */
    public void moveBefore(final String name, final Schema.Entry newEntry) {
        final EntryNode entryPivot = this.entryIndex.get(name);
        if (entryPivot == null) {
            throw new IllegalArgumentException(String.format("%s not in schema", name));
        }
        final EntryNode entryToMove = this.entryIndex.get(newEntry.getName());

        this.removeFromChain(entryToMove);
        if (entryPivot == this.first) {
            entryToMove.next = entryPivot;
            entryPivot.prec = entryToMove;
            this.first = entryToMove;
        } else {
            entryPivot.prec.insert(entryToMove);
        }
    }

    public void swap(final String name, final String with) {
        final EntryNode firstNode = this.entryIndex.get(name);
        if (firstNode == null) {
            throw new IllegalArgumentException(String.format("%s not in schema", name));
        }
        final EntryNode second = this.entryIndex.get(with);
        if (second == null) {
            throw new IllegalArgumentException(String.format("%s not in schema", second));
        }
        final boolean changeLast = this.last == second;

        final EntryNode secondPrec = second.prec;
        final EntryNode firstPrec = firstNode.prec;

        if (secondPrec != firstNode) {
            this.removeFromChain(firstNode);
        }
        if (firstPrec != second) {
            this.removeFromChain(second);
        }
        if (secondPrec != firstNode) {
            this.insertNode(secondPrec, firstNode);
        }
        if (firstPrec != second) {
            this.insertNode(firstPrec, second);
        }

        if (changeLast) {
            this.last = firstNode;
        }
    }

    private void insertNode(final EntryNode before, final EntryNode node) {
        if (before != null) {
            before.insert(node);
        }
        if (first == null) {
            this.first = node;
            node.next = null;
            node.prec = null;
        } else {
            final EntryNode oldFirst = this.first;
            this.first = node;
            node.next = oldFirst;
            oldFirst.prec = node;
        }
    }

}
