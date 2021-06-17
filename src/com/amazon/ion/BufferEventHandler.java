package com.amazon.ion;

import com.amazon.ion.impl.ReaderLookaheadBuffer;

/**
 * Handles events that occur while processing an Ion stream.
 */
public interface BufferEventHandler {

    /**
     * Invoked each time a value (and any symbol tables that immediately precede it) exceed the buffer size limit
     * specified by the LookaheadReaderWrapper instance, but the symbol tables by themselves do not exceed the
     * limit. This is recoverable. If the implementation wishes to recover, it should simply return normally from
     * this method. The oversized value will be flushed from the input pipe; normal processing will resume with the
     * next value. If the implementation wishes to abort processing immediately, it may throw an exception from this
     * method. Such an exception will propagate upward and will be thrown from
     * {@link ReaderLookaheadBuffer#fillInput()}.
     * @throws Exception if handler logic fails.
     */
    void onOversizedValue() throws Exception;

    /**
     * Invoked whenever the bytes from a value are processed, regardless of whether the bytes are buffered or
     * skipped due to the value being oversized.
     * @param numberOfBytes the number of bytes processed.
     * @throws Exception if handler logic fails.
     */
    void onData(int numberOfBytes) throws Exception;
}
