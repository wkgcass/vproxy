/*
 * The MIT License
 *
 * Copyright 2019 wkgcass (https://github.com/wkgcass)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package vjson.deserializer.rule;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class ArrayRule<L, E> extends Rule<L> {
    public final Supplier<L> construct;
    public final BiConsumer<L, E> add;
    public final Rule elementRule;

    public ArrayRule(Supplier<L> construct, BiConsumer<L, E> add, Rule<E> elementRule) {
        this.elementRule = elementRule;
        this.add = add;
        this.construct = construct;
    }

    @Override
    void toString(StringBuilder sb, Set<Rule> processedListsOrObjects) {
        if (!processedListsOrObjects.add(this)) {
            sb.append("Array[...recursive...]");
            return;
        }
        sb.append("Array[");
        elementRule.toString(sb, processedListsOrObjects);
        sb.append("]");
    }
}
