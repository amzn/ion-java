package com.amazon.ion;

import java.util.Iterator;

public class BinaryIonReaderIteratorSystemProcessingTest
    extends IteratorSystemProcessingTestCase
{
    private byte[] myBytes;

    @Override
    protected void prepare(String text)
    {
        myMissingSymbolTokensHaveText = false;
        myBytes = encode(text);
    }

    @Override
    protected Iterator<IonValue> iterate()
    {
        return system().iterate(getStreamingMode().newIonReader(system().getCatalog(), myBytes));
    }

    @Override
    protected Iterator<IonValue> systemIterate()
    {
        return system().systemIterate(system().newSystemReader(myBytes));
    }

    @Override
    protected int expectedLocalNullSlotSymbolId() {
        // The spec allows for implementations to treat these malformed symbols as "null slots", and all "null slots"
        // in local symbol tables as equivalent to symbol zero.
        return getStreamingMode() == StreamingMode.NEW_STREAMING_INCREMENTAL ? 0 : 10;
    }
}
