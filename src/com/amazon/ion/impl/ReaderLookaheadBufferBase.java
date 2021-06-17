package com.amazon.ion.impl;

import com.amazon.ion.BufferConfiguration;
import com.amazon.ion.BufferEventHandler;
import com.amazon.ion.IonReader;
import com.amazon.ion.system.IonReaderBuilder;

import java.io.IOException;
import java.io.InputStream;

/**
 * Base class for lookahead buffers that enable incremental reading of streaming data.
 */
abstract class ReaderLookaheadBufferBase implements ReaderLookaheadBuffer {

    /**
     * An InputStream over binary Ion data.
     */
    private final InputStream input;

    /**
     * A buffer for the bytes required for a successful call to {@link IonReader#next()}.
     */
    protected final ResizingPipedInputStream pipe;

    /**
     * The maximum number of bytes that will be buffered.
     */
    private final int maximumBufferSize;

    /**
     * The handler that will be notified when the maximum buffer size is exceeded.
     */
    protected final BufferEventHandler eventHandler;

    /**
     * The current mark for the pipe's value of 'available'.
     */
    private int markedAvailable;

    /**
     * The current mark for the pipe's value of 'readIndex'.
     */
    private int markedReadIndex;

    /**
     * Indicates whether the current value is being skipped due to being oversized.
     */
    private boolean isSkippingCurrentValue;

    /**
     * Constructs a wrapper over the given stream using the given configuration.
     * @param configuration the buffer configuration.
     * @param inputStream an InputStream over Ion data.
     */
    ReaderLookaheadBufferBase(final BufferConfiguration<?, ?> configuration, final InputStream inputStream) {
        input = inputStream;
        pipe = new ResizingPipedInputStream(
            configuration.getInitialBufferSize(),
            configuration.getMaximumBufferSize(),
            true
        );
        maximumBufferSize = configuration.getMaximumBufferSize();
        eventHandler = configuration.getHandler();
        clearMark();
    }

    @Override
    public final void fillInput() throws Exception {
        clearMark();
        fillInputHelper();
    }

    /**
     * Implements the behavior described in {@link #fillInput()}, except for clearing the mark.
     * @throws IOException if thrown by the underlying input stream.
     * @throws Exception if thrown by a handler method.
     */
    protected abstract void fillInputHelper() throws Exception;

    @Override
    public final int available() {
        return pipe.available();
    }

    @Override
    public final IonReader newIonReader(final IonReaderBuilder builder) {
        return builder.build(pipe);
    }

    /**
     * Marks the current read position in the underlying buffer. This mark remains valid only until the next call
     * to {@link #fillInput()}.
     * @throws IllegalStateException if more data is required to complete a value.
     */
    public final void mark() {
        if (moreDataRequired()) {
            throw new IllegalStateException("moreDataRequired() must be false before calling mark().");
        }
        uncheckedMark();
    }

    /**
     * Sets the mark without verifying that a complete value is buffered.
     */
    final void uncheckedMark() {
        markedAvailable = available();
        markedReadIndex = pipe.getReadIndex();
    }

    /**
     * Rewinds the underlying buffer to the mark.
     * @see #mark()
     * @throws IllegalStateException if there is no valid mark.
     */
    public final void rewind() {
        if (markedReadIndex < 0 || markedAvailable < 0) {
            throw new IllegalStateException("Must call mark() before rewind().");
        }
        pipe.rewind(markedReadIndex, markedAvailable);
    }

    /**
     * Truncates the buffer to the end of the last complete value.
     * @throws Exception if thrown by a handler.
     */
    abstract void truncateToEndOfPreviousValue() throws Exception;

    /**
     * Clears the mark.
     * @see #mark()
     */
    public final void clearMark() {
        markedAvailable = -1;
        markedReadIndex = -1;
    }

    /**
     * @return the underlying pipe.
     */
    protected InputStream getPipe() {
        return pipe;
    }

    /**
     * @return the underlying input.
     */
    protected InputStream getInput() {
        return input;
    }

    /**
     * Prepares for the start of a new value by clearing the {@link #isSkippingCurrentValue} flag.
     */
    protected void startNewValue() {
        isSkippingCurrentValue = false;
    }

    /**
     * Start skipping the current value, if it is not already being skipped. This should be called when the value
     * is determined to be oversize. This truncates the buffer to the end of the previous value, reclaiming the space.
     * @throws Exception if thrown by the event handler.
     */
    protected void startSkippingValue() throws Exception {
        if (!isSkippingCurrentValue) {
            isSkippingCurrentValue = true;
            truncateToEndOfPreviousValue();
        }
    }

    /**
     * Indicates whether the current value is being skipped due to being oversized.
     * @return true if the value is being skipped; otherwise, false.
     */
    protected boolean isSkippingCurrentValue() {
        return isSkippingCurrentValue;
    }

    /**
     * @return the maximum size of the buffer.
     */
    protected int getMaximumBufferSize() {
        return maximumBufferSize;
    }
}
