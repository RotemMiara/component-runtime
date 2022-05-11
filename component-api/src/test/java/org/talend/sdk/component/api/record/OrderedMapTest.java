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
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

class OrderedMapTest {

    @Test
    void orderedMapTest() {
        final OrderedMap<String> container = new OrderedMap<>(Function.identity(), Collections.emptyList());

        String f1 = "f1";
        String f2 = "f2";
        String f3 = "f3";
        String f4 = "f4";
        String f5 = "f5";

        container.addEntry(f1);
        container.addEntry(f2);
        List<String> entries = container.getEntries().collect(Collectors.toList());
        Assertions.assertSame("f1", entries.get(0));
        Assertions.assertSame("f2", entries.get(1));

        container.swap("f1", "f2");
        List<String> entries2 = container.getEntries().collect(Collectors.toList());
        Assertions.assertSame("f2", entries2.get(0));
        Assertions.assertSame("f1", entries2.get(1));

        container.addEntry(f3); // f2, f1, F3
        Assertions.assertEquals("f2,f1,f3", this.reduce(container));
        container.moveBefore("f2", f3); // F3, f2, F1
        Assertions.assertEquals("f3,f2,f1", this.reduce(container));

        container.addEntry(f4);
        Assertions.assertEquals("f3,f2,f1,f4", this.reduce(container));

        container.moveBefore("f2", f4); // F3, f4, f2, F1
        Assertions.assertEquals("f3,f4,f2,f1", this.reduce(container));

        container.moveBefore("f2", f3);
        Assertions.assertEquals("f4,f3,f2,f1", this.reduce(container));

        container.addEntry(f5);
        container.moveAfter("f2", f3);
        Assertions.assertEquals("f4,f2,f3,f1,f5", this.reduce(container));
        container.moveAfter("f5", f2);
        Assertions.assertEquals("f4,f3,f1,f5,f2", this.reduce(container));

        String f1Bis = "f1Bis";
        container.replaceEntry("f1", f1Bis);
        Assertions.assertEquals("f4,f3,f1Bis,f5,f2", this.reduce(container));
    }

    private String reduce(final OrderedMap<String> container) {
        return container.getEntries().collect(Collectors.joining(","));
    }

    @Test
    void orderedMapSwapTest() {
        final OrderedMap<String> container = new OrderedMap<>(Function.identity(), Collections.emptyList());

        String f1 = "f1";
        String f2 = "f2";
        String f3 = "f3";
        String f4 = "f4";
        String f5 = "f5";

        container.addEntry(f1);
        container.addEntry(f2);
        Assertions.assertEquals("f1,f2", this.reduce(container));

        container.swap("f1", "f2");
        Assertions.assertEquals("f2,f1", this.reduce(container));
        container.swap("f1", "f2");
        Assertions.assertEquals("f1,f2", this.reduce(container));

        container.addEntry(f3);
        container.addEntry(f4);
        container.addEntry(f5);

        container.swap("f3", "f5");
        Assertions.assertEquals("f1,f2,f5,f4,f3", this.reduce(container));

        container.swap("f3", "f4");
        Assertions.assertEquals("f1,f2,f5,f3,f4", this.reduce(container));

        container.swap("f1", "f4");
        Assertions.assertEquals("f4,f2,f5,f3,f1", this.reduce(container));
    }

}