/*
MIT License

Copyright (c) 2017 Zheng Sun

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package io.vproxy.base.util.coll;

import java.util.ListIterator;

/**
 * Reusable list iterator
 *
 * @author <a href="mailto:szhnet@gmail.com">szh</a>
 */
public interface ReusableListIterator<E> extends ListIterator<E>, ReusableIterator<E> {

    /**
     * This method is identical to {@code rewind(0)}.
     *
     * @return this iterator
     */
    ReusableListIterator<E> rewind();


    /**
     * Reset the iterator and specify index of the first element to be returned from the iterator.
     *
     * @param index index of the first element to be returned from the iterator
     * @return this iterator
     */
    ReusableListIterator<E> rewind(int index);

}
