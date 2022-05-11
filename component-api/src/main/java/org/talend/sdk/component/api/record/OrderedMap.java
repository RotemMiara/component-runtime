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
package org.talend.sdk.component.api.record;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * On TCK Record, we have to control order of elements with keeping efficient access.
 * LinkedHashMap has efficient access, but order of element is only the order of insertions.
 * List (ArrayList, LinkedList) allow to fine control order but have inefficent access.
 * This class aims to control element order with keeping efficient access.
 * 
 * @param <T> : type of element.
 */
public class OrderedMap<T> {

    @AllArgsConstructor
    static class Node<T> implements Iterable<Node<T>> {

        @Getter
        public T entry;

        public Node<T> next;

        public Node<T> prec;

        public Node<T> insert(final T newEntry) {
            final Node<T> newNode = new Node<>(newEntry, this.next, this);
            return this.insert(newNode);
        }

        public Node<T> insert(final Node<T> newNode) {
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
        public Iterator<Node<T>> iterator() {
            return new EntryNodeIterator(this);
        }

        @AllArgsConstructor
        static class EntryNodeIterator<T> implements Iterator<Node<T>> {

            private Node<T> current;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public Node<T> next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException("no further node");
                }
                final Node<T> next = this.current;
                this.current = next.next;
                return next;
            }
        }
    }

    private Node<T> first;

    private Node<T> last;

    private final Map<String, Node<T>> entryIndex;

    private final Function<T, String> getName;

    public OrderedMap(final Function<T, String> getName) {
        this(getName, Collections.emptyList());
    }

    public OrderedMap(final Function<T, String> getName,
            final Iterable<T> inputEntries) {
        this.entryIndex = new HashMap<>();
        this.getName = getName;
        inputEntries.forEach(this::addEntry);
    }

    public Stream<T> getEntries() {
        if (this.first == null) {
            return Stream.empty();
        }
        final Function<Node<T>, T> tFunction = (Node<T> f) -> f.getEntry();
        return StreamSupport.stream(this.first.spliterator(), false)
                .map(tFunction);
    }

    public void forEachEntry(final Consumer<T> entryConsumer) {
        if (this.first != null) {
            this.first.forEach((Node<T> node) -> entryConsumer.accept(node.getEntry()));
        }
    }

    public void removeEntry(final T schemaEntry) {
        final String name = this.getName.apply(schemaEntry);
        final Node<T> entry = this.entryIndex.remove(name);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "No entry '" + name + "' expected in entries");
        }
        this.removeFromChain(entry);
    }

    private void removeFromChain(final Node<T> node) {
        if (this.first == node) {
            this.first = node.next;
        }
        if (this.last == node) {
            this.last = node.prec;
        }
        node.remove();
    }

    public T getEntry(final String name) {
        if (this.entryIndex != null) {
            return Optional.ofNullable(this.entryIndex.get(name)) //
                    .map(Node::getEntry) //
                    .orElse(null);
        } else {
            return null;
        }
    }

    public void replaceEntry(final String name, final T newEntry) {
        final Node<T> entryNode = this.entryIndex.remove(name);
        if (entryNode != null) {
            entryNode.entry = newEntry;
            final String newEntryName = this.getName.apply(newEntry);
            this.entryIndex.put(newEntryName, entryNode);
        }
    }

    public void addEntry(final T entry) {
        final String name = this.getName.apply(entry);
        if (this.entryIndex != null && this.entryIndex.containsKey(name)) {
            return;
        }
        if (this.first == null) {
            this.first = new Node<>(entry, null, null);
            this.last = this.first;
            this.entryIndex.put(name, this.first);
        } else {
            final Node<T> newNode = this.last.insert(entry);
            this.entryIndex.put(name, newNode);
            this.last = newNode;
        }
    }

    public void moveAfter(final String name, final T newEntry) {
        final Node<T> entryPivot = this.entryIndex.get(name);
        if (entryPivot == null) {
            throw new IllegalArgumentException(String.format("%s not in schema", name));
        }
        String entryName = this.getName.apply(newEntry);
        final Node<T> entryToMove = this.entryIndex.get(entryName);

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
    public void moveBefore(final String name, final T newEntry) {
        final Node<T> entryPivot = this.entryIndex.get(name);
        if (entryPivot == null) {
            throw new IllegalArgumentException(String.format("%s not in schema", name));
        }
        final String newEntryName = this.getName.apply(newEntry);
        final Node<T> entryToMove = this.entryIndex.get(newEntryName);

        this.removeFromChain(entryToMove);
        if (entryPivot == this.first) {
            entryToMove.next = entryPivot;
            entryPivot.prec = entryToMove;
            this.first = entryToMove;
        } else {
            entryPivot.prec.insert(entryToMove);
        }
    }

    public void swap(final String first, final String second) {
        if (first == null || second == null || first.equals(second)) {
            return;
        }
        final Node<T> firstNode = this.entryIndex.get(first);
        if (firstNode == null) {
            throw new IllegalArgumentException(String.format("%s not in schema", first));
        }
        final Node<T> secondNode = this.entryIndex.get(second);
        if (secondNode == null) {
            throw new IllegalArgumentException(String.format("%s not in schema", secondNode));
        }
        final Node<T> firstPrec = firstNode.prec;
        final Node<T> firstNext = firstNode.next;

        final Node<T> secondPrec = secondNode.prec;
        final Node<T> secondNext = secondNode.next;

        if (secondPrec == firstNode) { // case first -> second direct
            secondNode.next = firstNode;
            secondNode.prec = firstPrec;
            if (firstPrec != null) {
                firstPrec.next = secondNode;
            }
            firstNode.prec = secondNode;
            firstNode.next = secondNext;
            if (secondNext != null) {
                secondNext.prec = firstNode;
            }
        } else if (firstPrec == secondNode) { // case second -> first direct
            firstNode.next = secondNode;
            firstNode.prec = secondPrec;
            if (secondPrec != null) {
                secondPrec.next = firstNode;
            }
            secondNode.prec = firstNode;
            secondNode.next = firstNext;
            if (firstNext != null) {
                firstNext.prec = secondNode;
            }
        } else { // general case
                 // Put first node at second place
            firstNode.prec = secondPrec;
            if (secondPrec != null) {
                secondPrec.next = firstNode;
            }
            firstNode.next = secondNext;
            if (secondNext != null) {
                secondNext.prec = firstNode;
            }

            // Put second node at first place
            secondNode.prec = firstPrec;
            if (firstPrec != null) {
                firstPrec.next = secondNode;
            }
            secondNode.next = firstNext;
            if (firstNext != null) {
                firstNext.prec = secondNode;
            }
        }
        if (this.first == firstNode) {
            this.first = secondNode;
        } else if (this.first == secondNode) {
            this.first = firstNode;
        }
        if (this.last == firstNode) {
            this.last = secondNode;
        } else if (this.last == secondNode) {
            this.last = firstNode;
        }
    }

}
