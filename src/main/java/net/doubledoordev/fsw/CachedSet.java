/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Dries K. Aka Dries007
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package net.doubledoordev.fsw;

import java.util.*;

@SuppressWarnings("WeakerAccess")
public class CachedSet<T> implements Set<T>
{
    private final HashMap<T, Long> expireTimeMap = new HashMap<>();
    private final int expireTime;

    public CachedSet(int expireTime)
    {
        this.expireTime = expireTime;
    }

    public void cleanup()
    {
        Iterator<Map.Entry<T, Long>> i = expireTimeMap.entrySet().iterator();
        long now = System.currentTimeMillis();
        while (i.hasNext())
        {
            Map.Entry<T, Long> e = i.next();
            if (e.getValue() > now) i.remove();
        }
    }

    @Override
    public int size()
    {
        cleanup();
        return expireTimeMap.size();
    }

    @Override
    public boolean isEmpty()
    {
        cleanup();
        return expireTimeMap.isEmpty();
    }

    @Override
    public boolean contains(Object o)
    {
        cleanup();
        return expireTimeMap.containsKey(o);
    }

    @Override
    public Iterator<T> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T1> T1[] toArray(T1[] a)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(T t)
    {
        return expireTimeMap.put(t, System.currentTimeMillis() + expireTime) != null;
    }

    @Override
    public boolean remove(Object o)
    {
        return expireTimeMap.remove(o) != null;
    }

    @Override
    public boolean containsAll(Collection<?> c)
    {
        return expireTimeMap.keySet().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c)
    {
        long time = System.currentTimeMillis() + expireTime;
        for (T t : c) expireTimeMap.put(t, time);
        return !c.isEmpty();
    }

    @Override
    public boolean retainAll(Collection<?> c)
    {
        return expireTimeMap.keySet().retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c)
    {
        return expireTimeMap.keySet().removeAll(c);
    }

    @Override
    public void clear()
    {
        expireTimeMap.clear();
    }
}
