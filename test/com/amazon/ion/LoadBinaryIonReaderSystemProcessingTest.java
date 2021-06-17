package com.amazon.ion;

public class LoadBinaryIonReaderSystemProcessingTest
    extends DatagramIteratorSystemProcessingTest
{
    private byte[] myBytes;

    @Override
    protected void prepare(String text)
    {
        myMissingSymbolTokensHaveText = false;
        myBytes = encode(text);
    }

    @Override
    protected IonDatagram load() throws Exception
    {
        IonLoader loader = loader();
        IonReader reader = getStreamingMode().newIonReader(system().getCatalog(), myBytes);
        IonDatagram datagram = loader.load(reader);
        reader.close();
        return datagram;
    }

    @Override
    protected int expectedLocalNullSlotSymbolId() {
        // The spec allows for implementations to treat these malformed symbols as "null slots", and all "null slots"
        // in local symbol tables as equivalent to symbol zero.
        return getStreamingMode() == StreamingMode.NEW_STREAMING_INCREMENTAL ? 0 : 10;
    }
}
