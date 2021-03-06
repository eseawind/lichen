// Copyright 2013 the original author or authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package lichen.core.internal;

import lichen.core.services.IterableLike;
import lichen.core.services.LichenCoreErrorCode;
import lichen.core.services.LichenException;
import lichen.core.services.Option;
import lichen.core.services.func.Function1;
import lichen.core.services.func.Function2;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 针对{@link IterableLike}的实现.
 *
 * @param <A> 集合中的元素类型
 * @author jcai
 */
public class IterableLikeImpl<A> implements IterableLike<A> {

    //_underlying collection
    private final Collection<A> _underlying;

    /**
     * 通过给定的集合来创建一个{@link IterableLike}对象.
     *
     * @param collection 待操作的集合对象
     */
    public IterableLikeImpl(final Collection<A> collection) {
        this._underlying = collection;
    }
    public IterableLikeImpl(final A ... elements) {
        this._underlying = Arrays.asList(elements);
    }

    /**
     * 私有创建，仅仅供内部调用.
     */
    private IterableLikeImpl() {
        this((Collection<A>) null);
    }

    @Override
    public final <B> IterableLike<B> map(final Function1<A, B> function) {
        final Iterator<A> old = iterator();
        return new IterableLikeImpl<B>() {
            @Override
            public Iterator<B> iterator() {
                return new Iterator<B>() {
                    @Override
                    public boolean hasNext() {
                        return old.hasNext();
                    }

                    @Override
                    public B next() {
                        return function.apply(old.next());
                    }

                    @Override
                    public void remove() {
                        throw new LichenException(
                                LichenCoreErrorCode.UNSUPPORT_REMOVE_ITERATOR);
                    }
                };
            }
        };
    }

    @Override
    public final void foreach(final Function1<A, Void> function) {
        Iterator<A> it = iterator();
        while (it.hasNext()) {
            function.apply(it.next());
        }
    }
    @Override
    public final void foreach(final Function2<A, Integer, Void> function) throws Throwable {
        Iterator<A> it = iterator();
        int i = 0;
        while (it.hasNext()) {
            function.apply(it.next(), i++);
        }
    }

    @Override
    public final Option<A> first(final Function1<A, Boolean> function) {
        Iterator<A> it = iterator();
        while (it.hasNext()) {
            A obj = it.next();
            if (function.apply(obj)) {
                return Option.some(obj);
            }
        }
        return Option.none();
    }

    @Override
    public Iterator<A> iterator() {
        return _underlying.iterator();
    }

    @Override
    public final boolean exists(final Function1<A, Boolean> function) {
        Iterator<A> it = iterator();
        while (it.hasNext()) {
            A obj = it.next();
            if (function.apply(obj)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public final IterableLike<A> filter(final Function1<A, Boolean> function) {
        final Iterator<A> old = iterator();
        return new IterableLikeImpl<A>() {
            @Override
            public Iterator<A> iterator() {
                return new Iterator<A>() {
                    private A _obj = null;
                    private boolean _isNew = false;

                    @Override
                    public boolean hasNext() {
                        if (old.hasNext()) {
                            _obj = old.next();
                            if (function.apply(_obj)) {
                                _isNew = true;
                                return true;
                            } else {
                                return hasNext();
                            }
                        }
                        return false;
                    }

                    @Override
                    public A next() {
                        if(!_isNew){
                            throw new NoSuchElementException();
                        }
                        _isNew = false;
                        return _obj;
                    }

                    @Override
                    public void remove() {
                        throw new LichenException(
                                LichenCoreErrorCode.UNSUPPORT_REMOVE_ITERATOR);
                    }
                };
            }
        };
    }
}
