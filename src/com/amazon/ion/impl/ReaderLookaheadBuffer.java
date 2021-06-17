package com.amazon.ion.impl;

import com.amazon.ion.BufferEventHandler;
import com.amazon.ion.IonReader;
import com.amazon.ion.system.IonReaderBuilder;

/**
 * Interface for lookahead buffers that enable incremental reading of streaming data.
 */
public interface ReaderLookaheadBuffer {

    /**
     * If possible, fills the input pipe with enough bytes to enable one more successful call to
     * {@link IonReader#next()} on the non-incremental reader attached to the pipe. If not enough bytes were available
     * in the raw input stream to complete the next top-level user value, calling {@link #moreDataRequired()} after this
     * method returns will return `true`. In this case, this method must be called again before calling
     * `IonReader.next()` to position the reader on this value. Otherwise, `moreDataRequired()` will return `false` and
     * a call to `IonReader.next()` may be made to position the reader on this value. Implementations may throw
     * `IonException` if invalid Ion data is detected. Any exceptions thrown when invoking {@link BufferEventHandler}
     * methods will be propagated. Implementations may define additional exceptional cases.
     * @throws Exception if thrown by a handler method or if an IOException is thrown by the underlying InputStream.
     */
    void fillInput() throws Exception;

    /**
     * Indicates whether more data must become present in the raw input stream before a successful call to
     * {@link IonReader#next()} may be made to position the reader on the most-recently-buffered value.
     * @return true if more data is required to complete the next top-level user value; otherwise, false.
     */
    boolean moreDataRequired();

    /**
     * Indicates how much data is currently stored in the internal buffer. This can be used to detect whether
     * calls to fillInput() are successfully retrieving data.
     * @return The number of bytes waiting in the input buffer.
     */
    int available();

    /**
     * Builds a reader over the input pipe. NOTE: because IonReader construction consumes bytes from the stream
     * (to determine whether the stream is binary or text Ion), {@link #fillInput()} must have been called (such that
     * {@link #moreDataRequired()} returns `false`)  before calling this method. For the same reason, this method must
     * only be called once per instance of this class.
     * @param builder the builder containing the reader's configuration.
     * @return a new IonReader.
     */
    IonReader newIonReader(IonReaderBuilder builder);
}
