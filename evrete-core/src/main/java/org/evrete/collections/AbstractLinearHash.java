package org.evrete.collections;

import org.evrete.api.ReIterable;
import org.evrete.api.ReIterator;
import org.evrete.util.CollectionUtils;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.StringJoiner;
import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A simple implementation of linear probing hash table without fail-fast bells and whistles and alike.
 * Unlike the stock Java's HashMap, this implementation can shrink down its bucket table when necessary,
 * thus preserving the real O(N) scan complexity. See benchmark tests for performance comparisons.
 *
 * @param <E> Entry type
 */
//TODO !!! implement minimal size
public abstract class AbstractLinearHash<E> implements ReIterable<E> {
    static final ToIntFunction<Object> DEFAULT_HASH = Object::hashCode;
    static final BiPredicate<Object, Object> DEFAULT_EQUALS = Object::equals;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final int MINIMUM_CAPACITY = 2;
    int size = 0;
    private Object[] data;
    private boolean[] deletedIndices;
    private int deletes = 0;
    private static final int NULL_VALUE = -1;
    private final int minCapacity;
    int currentInsertIndex;
    private int[] unsignedIndices;


    protected AbstractLinearHash(int minCapacity) {
        //super(tableSizeFor(minCapacity));
        int capacity = tableSizeFor(minCapacity);

        this.unsignedIndices = new int[capacity];
        CollectionUtils.systemFill(this.unsignedIndices, NULL_VALUE);
        this.currentInsertIndex = 0;

        this.minCapacity = minCapacity;
        this.data = new Object[capacity];
        this.deletedIndices = new boolean[capacity];
    }

/*
    protected AbstractLinearHash() {
        this(DEFAULT_INITIAL_CAPACITY);
    }
*/

    private static int findBinIndexFor(Object key, int hash, Object[] destination, BiPredicate<Object, Object> eqTest) {
        int mask = destination.length - 1;
        int addr = hash & mask;
        Object found;
        while ((found = destination[addr]) != null) {
            if (eqTest.test(key, found)) {
                return addr;
            } else {
                addr = (addr + 1) & mask;
            }
        }
        return addr;
    }

    private static int findEmptyBinIndex(int hash, Object[] destination) {
        int mask = destination.length - 1;
        int addr = hash & mask;
        while (destination[addr] != null) {
            addr = (addr + 1) & mask;
        }
        return addr;
    }

    @SuppressWarnings("unchecked")
    private static <K, E> int findBinIndex(K key, int hash, Object[] destination, BiPredicate<E, K> eqTest) {
        int mask = destination.length - 1;
        int addr = hash & mask;
        Object found;
        while ((found = destination[addr]) != null) {
            if (eqTest.test((E) found, key)) {
                return addr;
            } else {
                addr = (addr + 1) & mask;
            }
        }
        return addr;
    }

    /**
     * Returns a power of two size for the given target capacity.
     */
    private static int tableSizeFor(int capacity) {
        int cap = Math.max(capacity, MINIMUM_CAPACITY);
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        int ret = (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
        assert ret >= capacity;
        return ret;
    }

    @SuppressWarnings("unchecked")
    public E get(int addr) {
        return deletedIndices[addr] ? null : (E) data[addr];
    }

    private int findBinIndexFor(Object key, int hash, BiPredicate<Object, Object> eqTest) {
        return findBinIndexFor(key, hash, data, eqTest);
    }

    public <K> int findBinIndex(K key, int hash, BiPredicate<? super E, K> eqTest) {
        return findBinIndex(key, hash, data, eqTest);
    }

    public final boolean addVerbose(E element) {
        resize();
        BiPredicate<Object, Object> eq = getEqualsPredicate();
        int hash = getHashFunction().applyAsInt(element);
        int addr = findBinIndexFor(element, hash, eq);
        E old = saveDirect(element, addr);
        return old == null || !eq.test(element, old);
    }

    public final void addSilent(E element) {
        resize();
        addNoResize(element);
    }

    @SuppressWarnings("unused")
    public final E add(E element) {
        resize();
        return addGetPrevious(element);
    }

    private void addNoResize(E element) {
        int hash = getHashFunction().applyAsInt(element);
        int addr = findBinIndexFor(element, hash, getEqualsPredicate());
        saveDirect(element, addr);
    }

    private E addGetPrevious(E element) {
        int hash = getHashFunction().applyAsInt(element);
        int addr = findBinIndexFor(element, hash, getEqualsPredicate());
        return saveDirect(element, addr);
    }

    protected final <Z extends AbstractLinearHash<E>> void bulkAdd(Z other) {
        resize(size + other.size);

        ToIntFunction<Object> hashFunc = getHashFunction();
        BiPredicate<Object, Object> eqPredicate = getEqualsPredicate();

        int i, idx;
        E o;
        for (i = 0; i < other.currentInsertIndex; i++) {
            idx = other.getAt(i);
            if ((o = other.get(idx)) != null) {
                int hash = hashFunc.applyAsInt(o);
                int addr = findBinIndexFor(o, hash, eqPredicate);
                saveDirect(o, addr);
            }
        }
    }


    @SuppressWarnings("unchecked")
    public final E saveDirect(E element, int addr) {
        Object old = data[addr];
        data[addr] = element;
        if (old == null) {
            addNew(addr);
            size++;
        } else {
            if (deletedIndices[addr]) {
                deletedIndices[addr] = false;
                deletes--;
                size++;
            }
        }
        return (E) old;
    }

    private static int optimalArrayLen(int dataSize) {
        switch (dataSize) {
            case 0:
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 5;
            default:
                return (dataSize + 1) * 3 / 2;
        }
    }

    void addNew(int value) {
        //if (currentInsertIndex == unsignedIndices.length - 1) {
        //    expand();
        // }
        unsignedIndices[currentInsertIndex++] = value;
    }

    int getAt(int pos) {
        return unsignedIndices[pos];
    }

    private void expand() {
        int newLen = optimalArrayLen(this.unsignedIndices.length);
        this.unsignedIndices = Arrays.copyOf(this.unsignedIndices, newLen);
        CollectionUtils.systemFill(unsignedIndices, currentInsertIndex, newLen, NULL_VALUE);
    }

    protected abstract ToIntFunction<Object> getHashFunction();

    protected abstract BiPredicate<Object, Object> getEqualsPredicate();

    final int dataSize() {
        return data.length;
    }

    public final int size() {
        return size;
    }

    final void deleteEntries(Predicate<E> predicate) {
        int initialDeletes = this.deletes;
        forEachDataEntry((e, i) -> {
            if (predicate.test(e)) {
                markDeleted(i);
            }
        });
        if (initialDeletes != this.deletes) {
            resize();
        }
    }

    public void forEachDataEntry(Consumer<E> consumer) {
        E obj;
        for (int i = 0; i < currentInsertIndex; i++) {
            if ((obj = get(getAt(i))) != null) {
                consumer.accept(obj);
            }
        }
    }

    private void forEachDataEntry(ObjIntConsumer<E> consumer) {
        int i, idx;
        E obj;
        for (i = 0; i < currentInsertIndex; i++) {
            idx = getAt(i);
            if ((obj = get(idx)) != null) {
                consumer.accept(obj, idx);
            }
        }
    }

    public void markDeleted(int addr) {
        if (!deletedIndices[addr]) {
            deletedIndices[addr] = true;
            deletes++;
            size--;
        }
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        forEachDataEntry(k -> joiner.add(k.toString()));
        return joiner.toString();
    }

    public void clear() {
        clearTmp();
        CollectionUtils.systemFill(this.data, null);
        CollectionUtils.systemFill(this.deletedIndices, false);
        this.size = 0;
        this.deletes = 0;
    }

    void clearTmp() {
        this.currentInsertIndex = 0;
    }

    boolean containsEntry(E e) {
        int addr = findBinIndexFor(e, getHashFunction().applyAsInt(e), getEqualsPredicate());
        return data[addr] != null && !deletedIndices[addr];
    }

    boolean removeEntry(Object e) {
        int addr = findBinIndexFor(e, getHashFunction().applyAsInt(e), getEqualsPredicate());
        return removeEntry(addr);
    }

    private boolean removeEntry(int addr) {
        if (data[addr] == null) {
            // Nothing to delete
            return false;
        } else {
            if (deletedIndices[addr]) {
                // Nothing to delete
                return false;
            } else {
                removeNonEmpty(addr);
                return true;
            }
        }
    }

    private void removeNonEmpty(int addr) {
        markDeleted(addr);
        resize();
    }

    @Override
    public ReIterator<E> iterator() {
        return new It();
    }

    @SuppressWarnings("unchecked")
    public Stream<E> stream() {
        return intStream().filter(i -> !deletedIndices[i]).mapToObj(value -> (E) data[value]);
    }

    int currentInsertIndex() {
        return currentInsertIndex;
    }

    public void resize() {
        assert currentInsertIndex() == this.size + this.deletes : "indices: " + currentInsertIndex() + " size: " + this.size + ", deletes: " + this.deletes;
        resize(this.size);
    }

    IntStream intStream() {
        return Arrays.stream(unsignedIndices, 0, currentInsertIndex);
    }

    void copyFrom(AbstractLinearHash other) {
        this.unsignedIndices = other.unsignedIndices;
        this.currentInsertIndex = other.currentInsertIndex;
    }

    public void resize(int targetSize) {
        if (targetSize < 0) throw new IllegalArgumentException();
        boolean expand = 2 * (targetSize + deletes) >= data.length;
        boolean shrink = deletes > 0 && targetSize < deletes;
        if (expand) {
            int newSize = tableSizeFor(Math.max(minCapacity, targetSize * 2 + 1));
            if (newSize > MAXIMUM_CAPACITY) throw new OutOfMemoryError();

            Object[] newData = (Object[]) Array.newInstance(data.getClass().getComponentType(), newSize);
            int[] newUnsignedIndices = new int[newSize];
            int newCurrentInsertIndex = 0;

            ToIntFunction<Object> hashFunction = getHashFunction();
            E obj;
            for (int i = 0; i < currentInsertIndex; i++) {
                if ((obj = get(getAt(i))) != null) {
                    int addr = findEmptyBinIndex(hashFunction.applyAsInt(obj), newData);
                    newData[addr] = obj;
                    newUnsignedIndices[newCurrentInsertIndex++] = addr;
                    //consumer.accept(obj);
                }
            }

            //if (targetSize > 0) {
/*
                forEachDataEntry(e -> {
                    int addr = findEmptyBinIndex(hashFunction.applyAsInt(e), newData);
                    newData[addr] = e;
                    newIndices.addNew(addr);
                });
*/
            //}

            this.data = newData;
            //this.copyFrom(newIndices);
            this.deletes = 0;
            this.deletedIndices = new boolean[newSize];
            this.currentInsertIndex = newCurrentInsertIndex;
            this.unsignedIndices = newUnsignedIndices;
            return;
        }

        if (shrink) {
            int newSize = tableSizeFor(Math.max(minCapacity, targetSize * 2 + 1));
            if (newSize <= minCapacity) return;
            if (newSize > MAXIMUM_CAPACITY) throw new OutOfMemoryError();

            Object[] newData = (Object[]) Array.newInstance(data.getClass().getComponentType(), newSize);
            //UnsignedIntArray newIndices = new UnsignedIntArray(newSize);
            int[] newUnsignedIndices = new int[newSize];
            int newCurrentInsertIndex = 0;

            ToIntFunction<Object> hashFunction = getHashFunction();
            E obj;
            for (int i = 0; i < currentInsertIndex; i++) {
                if ((obj = get(getAt(i))) != null) {
                    int addr = findEmptyBinIndex(hashFunction.applyAsInt(obj), newData);
                    newData[addr] = obj;
                    newUnsignedIndices[newCurrentInsertIndex++] = addr;
                    //consumer.accept(obj);
                }
            }

/*
            if (targetSize > 0) {
                ToIntFunction<Object> hashFunction = getHashFunction();
                forEachDataEntry(e -> {
                    int addr = findEmptyBinIndex(hashFunction.applyAsInt(e), newData);
                    newData[addr] = e;
                    newIndices.addNew(addr);
                });
            }
*/

            this.data = newData;
            this.deletes = 0;
            this.deletedIndices = new boolean[newSize];
            this.currentInsertIndex = newCurrentInsertIndex;
            this.unsignedIndices = newUnsignedIndices;

        }

    }

    void assertStructure() {
        int indices = currentInsertIndex();
        int deletes = this.deletes;
        assert indices == size + deletes : "indices: " + indices + " size: " + size + ", deletes: " + deletes;
    }

    private class It implements ReIterator<E> {
        int pos = 0;
        int nextIndex;
        int currentIndex = -1;

        private It() {
            // Initial advance
            nextIndex = computeNextIndex();
        }


        @Override
        public long reset() {
            pos = 0;
            nextIndex = computeNextIndex();
            return size;
        }

        @Override
        public boolean hasNext() {
            return nextIndex >= 0;
        }

        private int computeNextIndex() {
            while (pos < currentInsertIndex) {
                int idx = getAt(pos);
                if (deletedIndices[idx]) {
                    pos++;
                } else {
                    return idx;
                }
            }
            return -1;
        }


        @Override
        @SuppressWarnings("unchecked")
        public E next() {
            if (nextIndex < 0) {
                throw new NoSuchElementException();
            } else {
                pos++;
                currentIndex = nextIndex;
                nextIndex = computeNextIndex();
                return (E) data[currentIndex];
            }
        }

        @Override
        public void remove() {
            if (currentIndex < 0) {
                throw new NoSuchElementException();
            } else {
                markDeleted(currentIndex);
            }
        }
    }
}
