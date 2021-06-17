package com.amazon.ion.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * A stack whose elements are recycled. This can be useful when the stack needs to grow and shrink
 * frequently and has a predictable maximum depth.
 * @param <T> the type of elements stored.
 */
public final class _Private_RecyclingStack<T> {

    /**
     * Factory for new stack elements.
     * @param <T> the type of element.
     */
    public interface ElementFactory<T> {

        /**
         * @return a new instance.
         */
        T newElement();
    }

    private final List<T> elements;
    private final ElementFactory<T> elementFactory;
    private int currentIndex;
    private T top;

    /**
     * @param initialCapacity the initial capacity of the underlying collection.
     * @param elementFactory the factory used to create a new element on {@link #push()} when the stack has
     *                       not previously grown to the new depth.
     */
    public _Private_RecyclingStack(int initialCapacity, ElementFactory<T> elementFactory) {
        elements = new ArrayList<T>(initialCapacity);
        this.elementFactory = elementFactory;
        currentIndex = -1;
        top = null;
    }

    /**
     * Pushes an element onto the top of the stack, instantiating a new element only if the stack has not
     * previously grown to the new depth.
     * @return the element at the top of the stack after the push. This element must be initialized by the caller.
     */
    public T push() {
        currentIndex++;
        if (currentIndex >= elements.size()) {
            top = elementFactory.newElement();
            elements.add(top);
        }  else {
            top = elements.get(currentIndex);
        }
        return top;
    }

    /**
     * @return the element at the top of the stack, or null if the stack is empty.
     */
    public T peek() {
        return top;
    }

    /**
     * Pops an element from the stack, retaining a reference to the element so that it can be reused the
     * next time the stack grows to the element's depth.
     * @return the element that was at the top of the stack before the pop, or null if the stack was empty.
     */
    public T pop() {
        T popped = top;
        currentIndex--;
        if (currentIndex >= 0) {
            top = elements.get(currentIndex);
        } else {
            top = null;
            currentIndex = -1;
        }
        return popped;
    }

    /**
     * @return true if the stack is empty; otherwise, false.
     */
    public boolean isEmpty() {
        return top == null;
    }

    /**
     * @return the number of elements on the stack.
     */
    public int size() {
        return currentIndex + 1;
    }
}
