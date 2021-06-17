package com.amazon.ion;

import com.amazon.ion.impl.IonReaderLookaheadBuffer;

/**
 * Handles events that occur while processing a binary Ion stream.
 */
public interface IonBufferEventHandler extends BufferEventHandler {

    /**
     * Invoked when the user specifies a finite maximum buffer size and that size is exceeded by symbol table(s)
     * alone. Because symbol tables cannot be truncated without corrupting values that follow in the stream,
     * this condition is not recoverable. After this method is called,
     * {@link IonReaderLookaheadBuffer#fillInput()} has no effect.
     * @throws Exception if handler logic fails.
     */
    void onOversizedSymbolTable() throws Exception;
}
