package com.amazon.ion.impl;

import com.amazon.ion.IonBufferConfiguration;
import com.amazon.ion.IonBufferEventHandler;
import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonException;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.SymbolTable;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonReaderBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.util.Equivalence;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.amazon.ion.BitUtils.bytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class IonReaderLookaheadBufferTest
    extends IonReaderLookaheadBufferTestBase<IonBufferEventHandler, IonBufferConfiguration, IonBufferConfiguration.Builder>
{

    private interface BuilderSupplier {
        IonBufferConfiguration.Builder get();
    }

    private static final BuilderSupplier DEFAULT_BUILDER_SUPPLIER = new BuilderSupplier() {

        @Override
        public IonBufferConfiguration.Builder get() {
            return IonBufferConfiguration.Builder.standard();
        }
    };

    private BuilderSupplier builderSupplier = DEFAULT_BUILDER_SUPPLIER;

    @Before
    public void setup() {
        builderSupplier = DEFAULT_BUILDER_SUPPLIER;
    }

    @Override
    IonBufferConfiguration.Builder createLookaheadWrapperBuilder() {
        return builderSupplier.get();
    }

    @Override
    ReaderLookaheadBufferBase build(IonBufferConfiguration.Builder configuration, InputStream inputStream) {
        return new IonReaderLookaheadBuffer(configuration.build(), inputStream);
    }

    @Override
    IonBufferEventHandler createThrowingEventHandler() {
        return new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                throw new IllegalStateException();
            }

            @Override
            public void onOversizedValue() {
                throw new IllegalStateException();
            }

            @Override
            public void onData(int numberOfBytes) {
                throw new IllegalStateException();
            }
        };
    }

    @Override
    IonBufferEventHandler createCountingEventHandler(final AtomicLong byteCount) {
        return new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                throw new IllegalStateException();
            }

            @Override
            public void onOversizedValue() {
                throw new IllegalStateException();
            }

            @Override
            public void onData(int numberOfBytes) {
                byteCount.addAndGet(numberOfBytes);
            }
        };
    }

    public static class BinaryStream {
        public final InputStream inputStream;
        public final long length;

        BinaryStream(ByteArrayOutputStream source) {
            this(source.toByteArray());
        }

        BinaryStream(byte[] bytes) {
            inputStream = new ByteArrayInputStream(bytes);
            length = bytes.length;
        }
    }

    public static BinaryStream binaryIonStream(String textIon) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        _Private_IonSystem system = (_Private_IonSystem) IonSystemBuilder.standard().build();
        IonReader reader = system.newSystemReader(textIon);
        IonWriter writer = IonBinaryWriterBuilder.standard().build(output);
        writer.writeValues(reader);
        reader.close();
        writer.close();
        return new BinaryStream(output);
    }

    @Override
    byte[] toBytes(String textIon) throws IOException {
        BinaryStream binaryStream = binaryIonStream(textIon);
        byte[] bytes = new byte[(int) binaryStream.length];
        assertEquals(binaryStream.length, binaryStream.inputStream.read(bytes));
        return bytes;
    }

    @Override
    byte[] intZeroWithoutIvm() {
        return bytes(0x20);
    }

    @Test
    public void nopPadding() throws Exception {
        byte[] threeByteNopPadBeforeIntegerZero = bytes(0xE0, 0x01, 0x00, 0xEA, 0x0E, 0x81, 0x00, 0x20);
        readSingleValueOneByteAtATime(
            threeByteNopPadBeforeIntegerZero,
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.INT, reader.next());
                    assertEquals(0, reader.intValue());
                }
            }
        );
    }

    @Test
    public void annotationWithMultiByteSID() throws Exception {
        // The SID 1010 takes multiple bytes to express, but the total length of the annotation wrapper is still small
        // enough that the length is encoded in the lower nibble of the type ID. This test verifies that the
        // annot_length VarUInt is correctly treated as a length of the SIDs, not the number of SIDs.
        readSingleValueOneByteAtATime(
            toBytes("$ion_symbol_table::{imports:[{name:\"foo\", version:1, max_id:1000}], symbols:[\"abc\"]} $1010::123"),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.INT, reader.next());
                    String[] annotations = reader.getTypeAnnotations();
                    assertEquals(1, annotations.length);
                    assertEquals("abc", annotations[0]);
                    assertEquals(123, reader.intValue());
                }
            }
        );
        // This test is similar, except that the annotation wrapper's length requires the 'length' VarUInt.
        readSingleValueOneByteAtATime(
            toBytes("$ion_symbol_table::{imports:[{name:\"foo\", version:1, max_id:1000}], symbols:[\"abc\"]} $1010::\"abcdefghijklmnopqrstuvwxyz0123456789\""),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.STRING, reader.next());
                    String[] annotations = reader.getTypeAnnotations();
                    assertEquals(1, annotations.length);
                    assertEquals("abc", annotations[0]);
                    assertEquals("abcdefghijklmnopqrstuvwxyz0123456789", reader.stringValue());
                }
            }
        );
        // Two multibyte SIDs.
        readSingleValueOneByteAtATime(
            toBytes("$ion_symbol_table::{imports:[{name:\"foo\", version:1, max_id:1000}], symbols:[\"abc\"]} $1010::$1010::123"),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.INT, reader.next());
                    String[] annotations = reader.getTypeAnnotations();
                    assertEquals(2, annotations.length);
                    assertEquals("abc", annotations[0]);
                    assertEquals("abc", annotations[1]);
                    assertEquals(123, reader.intValue());
                }
            }
        );
        // One multibyte SID and one one-byte SID.
        readSingleValueOneByteAtATime(
            toBytes("$ion_symbol_table::{imports:[{name:\"foo\", version:1, max_id:1000}], symbols:[\"abc\"]} $4::$1010::\"abcdefghijklmnopqrstuvwxyz0123456789\""),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.STRING, reader.next());
                    String[] annotations = reader.getTypeAnnotations();
                    assertEquals(2, annotations.length);
                    assertEquals("name", annotations[0]); // SID 4 is "name"
                    assertEquals("abc", annotations[1]);
                    assertEquals("abcdefghijklmnopqrstuvwxyz0123456789", reader.stringValue());
                }
            }
        );
    }

    @Test
    public void symbolTableAnnotationOnStructNotFirst() throws Exception {
        readSingleValueOneByteAtATime(
            // Note: the following more readable line will be possible once ion-java#222 is fixed.
            // toBinaryIon("name::$ion_symbol_table::{}"),
            bytes(0xE0, 0x01, 0x00, 0xEA, 0xE4, 0x82, 0x84, 0x83, 0xD0),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.STRUCT, reader.next());
                    String[] annotations = reader.getTypeAnnotations();
                    assertEquals(2, annotations.length);
                    assertEquals("name", annotations[0]);
                    assertEquals("$ion_symbol_table", annotations[1]);
                    reader.stepIn();
                    assertNull(reader.next());
                    reader.stepOut();
                }
            }
        );
    }

    @Test
    public void errorOnInvalidTypeF() throws Exception {
        // Type F is illegal in Ion 1.0.
        thrown.expect(IonException.class);
        readSingleValueOneByteAtATime(
            bytes(0xE0, 0x01, 0x00, 0xEA, 0xF0),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    fail();
                }
            }
        );
    }

    @Test
    public void errorOnInvalidAnnotationTooShort() throws Exception {
        // Annotation wrappers must have length at least three.
        thrown.expect(IonException.class);
        readSingleValueOneByteAtATime(
            bytes(0xE0, 0x01, 0x00, 0xEA, 0xE2, 0x81, 0x80),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    fail();
                }
            }
        );
    }

    @Test
    public void errorOnInvalidAnnotationNull() throws Exception {
        // Annotation wrappers cannot be null.
        thrown.expect(IonException.class);
        readSingleValueOneByteAtATime(
            bytes(0xE0, 0x01, 0x00, 0xEA, 0xE2),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    fail();
                }
            }
        );
    }

    @Test
    public void errorOnIVMInAnnotation() throws Exception {
        // The IVM must only occur at the top level.
        thrown.expect(IonException.class);
        readSingleValueOneByteAtATime(
            bytes(0xE0, 0x01, 0x00, 0xEA, 0xE6, 0x81, 0x83, 0xE0, 0x01, 0x00, 0xEA),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    fail();
                }
            }
        );
    }

    @Test
    public void errorOnInvalidAnnotationWithinAnnotation() throws Exception {
        // An annotation wrapper cannot wrap another annotation wrapper. This is an example of invalid Ion
        // that is not detected by the lookahead wrapper. The error will be conveyed by the reader as normal.
        readSingleValueOneByteAtATime(
            bytes(0xE0, 0x01, 0x00, 0xEA, 0xE6, 0x81, 0x84, 0xE3, 0x81, 0x84, 0x20),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    thrown.expect(IonException.class);
                    reader.next();
                }
            }
        );
    }

    private static final int VALUE_BITS_PER_VARUINT_BYTE = 7;
    private static final int LOWER_SEVEN_BITS_BITMASK = 0x7F;

    @Test
    public void succeedsOnVarUIntMaxLong() throws Exception {
        ResizingPipedInputStream input = new ResizingPipedInputStream(5);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(
            IonBufferConfiguration.Builder.standard().build(),
            input
        );
        // The IVM and the start of a variable-length NOP pad.
        input.receive(bytes(0xE0, 0x01, 0x00, 0xEA, 0x0E));
        lookahead.fillInput();
        assertTrue(lookahead.moreDataRequired());
        // The following writes the VarUInt representation of Long.MAX_VALUE one byte at a time.
        // Note: Long.MAX_VALUE is represented in Long.SIZE - 1 bits because long is signed.
        for (int i = Long.SIZE - 1; i > 0; i -= VALUE_BITS_PER_VARUINT_BYTE) {
            int bitsToShift = i - VALUE_BITS_PER_VARUINT_BYTE;
            int endMask = 0;
            if (i <= VALUE_BITS_PER_VARUINT_BYTE) {
                endMask = 0x80;
                bitsToShift = 0;
            }
            input.receive((byte) (((Long.MAX_VALUE >>> bitsToShift) & LOWER_SEVEN_BITS_BITMASK) | endMask));
            lookahead.fillInput();
            assertTrue(lookahead.moreDataRequired());
        }
        // This is the first of the Long.MAX_VALUE bytes of NOP pad that the Ion reader would skip. Writing
        // all of them is out of scope for this test, but testing that the first one succeeds verifies that
        // the lookahead wrapper has exited its VarUInt parsing state.
        input.receive(0);
        lookahead.fillInput();
        assertTrue(lookahead.moreDataRequired());
    }

    @Test
    public void errorOnVarUIntTooLarge() throws Exception {
        BigInteger longPlusOne = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        ResizingPipedInputStream input = new ResizingPipedInputStream(5);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(
            IonBufferConfiguration.Builder.standard().build(),
            input
        );
        // The IVM and the start of a variable-length NOP pad.
        input.receive(bytes(0xE0, 0x01, 0x00, 0xEA, 0x0E));
        lookahead.fillInput();
        // The following writes the VarUInt representation of `longPlusOne` one byte at a time.
        for (int i = longPlusOne.bitLength(); i > 0; i -= VALUE_BITS_PER_VARUINT_BYTE) {
            assertTrue(lookahead.moreDataRequired());
            if (i >= VALUE_BITS_PER_VARUINT_BYTE) {
                int bitsToShift = i - VALUE_BITS_PER_VARUINT_BYTE;
                input.receive(
                    longPlusOne
                        .shiftRight(bitsToShift)
                        .and(BigInteger.valueOf(LOWER_SEVEN_BITS_BITMASK))
                        .byteValue()
                );
                if (bitsToShift < VALUE_BITS_PER_VARUINT_BYTE) {
                    // This is the second-to-last byte. A failure is expected because this is not the end
                    // byte, so no matter what the next byte is, the value can't fit in a long.
                    thrown.expect(IonException.class);
                }
                lookahead.fillInput();
            } else {
                fail("Reached the last byte, which should not be possible.");
            }
        }
    }

    @Test
    public void errorOnRewindAtBeginning() {
        ResizingPipedInputStream input = new ResizingPipedInputStream(5);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(
            IonBufferConfiguration.Builder.standard().build(),
            input
        );
        thrown.expect(IllegalStateException.class);
        lookahead.rewind();
    }

    @Test
    public void errorOnRewindToValueStartAtBeginning() {
        ResizingPipedInputStream input = new ResizingPipedInputStream(5);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(
            IonBufferConfiguration.Builder.standard().build(),
            input
        );
        thrown.expect(IllegalStateException.class);
        lookahead.rewindToValueStart();
    }

    @Test
    public void errorOnMarkWhenMoreDataIsRequired() throws Exception {
        ResizingPipedInputStream input = new ResizingPipedInputStream(5);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(
            IonBufferConfiguration.Builder.standard().build(),
            input
        );
        // The IVM and the start of a variable-length NOP pad.
        input.receive(bytes(0xE0, 0x01, 0x00, 0xEA, 0xE0));
        lookahead.fillInput();
        assertTrue(lookahead.moreDataRequired());
        thrown.expect(IllegalStateException.class);
        lookahead.mark();
    }

    @Test
    public void errorOnRewindToValueStartWhenDataWouldBeLost() throws Exception {
        ResizingPipedInputStream input = new ResizingPipedInputStream(5);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(
            IonBufferConfiguration.Builder.standard().build(),
            input
        );
        // The IVM and the start of a variable-length NOP pad.
        input.receive(bytes(0xE0, 0x01, 0x00, 0xEA, 0xE0));
        lookahead.fillInput();
        assertTrue(lookahead.moreDataRequired());
        thrown.expect(IllegalStateException.class);
        lookahead.rewindToValueStart();
    }

    @Test
    public void errorOnRewindAfterClear() throws Exception {
        ResizingPipedInputStream input = new ResizingPipedInputStream(5);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(
            IonBufferConfiguration.Builder.standard().build(),
            input
        );
        // The IVM followed by int 0.
        input.receive(bytes(0xE0, 0x01, 0x00, 0xEA, 0x20));
        lookahead.fillInput();
        lookahead.mark();
        lookahead.clearMark();
        thrown.expect(IllegalStateException.class);
        lookahead.rewind();
    }

    @Test
    public void errorOnRewindToValueStartWhenNoValueIsBuffered() throws Exception {
        ResizingPipedInputStream input = new ResizingPipedInputStream(5);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(
            IonBufferConfiguration.Builder.standard().build(),
            input
        );
        // The IVM followed by int 0.
        input.receive(bytes(0xE0, 0x01, 0x00, 0xEA, 0x20));
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        IonReader reader = lookahead.newIonReader(IonReaderBuilder.standard());
        assertEquals(IonType.INT, reader.next());
        assertEquals(0, reader.intValue());
        lookahead.fillInput();
        assertTrue(lookahead.moreDataRequired());
        thrown.expect(IllegalStateException.class);
        lookahead.rewindToValueStart();
    }

    @Test
    public void errorOnRewindAfterFillInput() throws Exception {
        ResizingPipedInputStream input = new ResizingPipedInputStream(5);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(
            IonBufferConfiguration.Builder.standard().build(),
            input
        );
        // The IVM followed by int 0.
        input.receive(bytes(0xE0, 0x01, 0x00, 0xEA, 0x20));
        lookahead.fillInput();
        lookahead.mark();
        input.receive(bytes(0x20));
        lookahead.fillInput();
        thrown.expect(IllegalStateException.class);
        lookahead.rewind();
    }

    @Test
    public void rewind() throws Exception {
        ResizingPipedInputStream input = new ResizingPipedInputStream(1);
        IonBufferConfiguration.Builder builder = IonBufferConfiguration.Builder.standard();
        if (initialBufferSize != null) {
            builder.withInitialBufferSize(initialBufferSize);
        }
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(builder.build(), input);
        input.receive(bytes(0xE0, 0x01, 0x00, 0xEA, 0x20));
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        lookahead.mark();
        IonReader reader = lookahead.newIonReader(IonReaderBuilder.standard());
        assertEquals(IonType.INT, reader.next());
        assertEquals(0, reader.intValue());
        lookahead.rewind();
        // 4-byte IVM + 1-byte value
        assertEquals(5, lookahead.available());
        assertEquals(IonType.INT, reader.next());
        assertEquals(0, reader.intValue());
        input.receive(bytes(0x21, 0x01));
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        lookahead.mark();
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());
        lookahead.rewind();
        assertEquals(2, lookahead.available());
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());
        lookahead.rewind();
        assertEquals(2, lookahead.available());
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());
        assertTrue(lookahead.moreDataRequired());
        assertEquals(0, lookahead.available());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void rewindToValueStart() throws Exception {
        ResizingPipedInputStream input = new ResizingPipedInputStream(1);
        IonBufferConfiguration.Builder builder = IonBufferConfiguration.Builder.standard();
        if (initialBufferSize != null) {
            builder.withInitialBufferSize(initialBufferSize);
        }
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(builder.build(), input);
        input.receive(bytes(0xE0, 0x01, 0x00, 0xEA, 0x20));
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        IonReader reader = lookahead.newIonReader(IonReaderBuilder.standard());
        assertEquals(IonType.INT, reader.next());
        assertEquals(0, reader.intValue());
        lookahead.rewindToValueStart();
        // No IVM, just 1-byte value.
        assertEquals(1, lookahead.available());
        assertEquals(IonType.INT, reader.next());
        assertEquals(0, reader.intValue());
        input.receive(bytes(0x21, 0x01));
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());
        lookahead.rewindToValueStart();
        assertEquals(2, lookahead.available());
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());
        lookahead.rewindToValueStart();
        assertEquals(2, lookahead.available());
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());
        assertTrue(lookahead.moreDataRequired());
        assertEquals(0, lookahead.available());
        assertNull(reader.next());
        reader.close();
    }

    private static class BinaryIonAppender {
        private static final IonSystem SYSTEM = IonSystemBuilder.standard().build();
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private IonDatagram datagram = null;

        BinaryIonAppender append(int... data) throws Exception {
            out.write(bytes(data));
            return this;
        }

        byte[] toByteArray() {
            byte[] data = out.toByteArray();
            datagram = SYSTEM.getLoader().load(data);
            return data;
        }
    }

    private static class RecordingEventHandler implements IonBufferEventHandler {

        int oversizedSymbolTableCount = 0;
        int oversizedValueCount = 0;
        long numberOfBytesProcessed = 0;

        @Override
        public void onOversizedSymbolTable() {
            oversizedSymbolTableCount++;
        }

        @Override
        public void onOversizedValue() {
            oversizedValueCount++;
        }

        @Override
        public void onData(int numberOfBytes) {
            numberOfBytesProcessed += numberOfBytes;
        }
    }

    @Test
    public void skipOversizedValues() throws Exception {
        if (initialBufferSize == null) {
            // This test tests buffers of limited size, while an initialBufferSize of null indicates that the size
            // of the buffer is unlimited. Skip.
            return;
        }
        // 14 values ranging in size from 1-14 bytes. There are 14 - x values larger than x bytes.
        BinaryIonAppender appender = new BinaryIonAppender();
        appender.append(0xE0, 0x01, 0x00, 0xEA);
        appender.append(0x11);
        appender.append(0x21, 0x00); // int 0 (overpadded)
        appender.append(0x22, 0x00, 0x01); // int 1 (overpadded)
        appender.append(0x33, 0x00, 0x00, 0x02); // int -2 (overpadded)
        appender.append(0x44, 0x00, 0x00, 0x00, 0x00); // float 0e0
        appender.append(0x55, 0x00, 0x00, 0x80, 0x00, 0x00); // decimal 0d0 (overpadded)
        appender.append(0x66, 0x80, 0x81, 0x81, 0x81, 0x80, 0x80); // timestamp 0001-01-01T00:00Z
        appender.append(0x7E, 0x00, 0x00, 0x00, 0x00, 0x82, 0x00, 0x00); // symbol 0 (overpadded length and value)
        appender.append(0x88, 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'); // string abcdefgh
        appender.append(0x99, 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'); // clob abcdefghi
        appender.append(0xAA, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A); // blob
        // list with ten int 0
        appender.append(0xBB, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20);
        // sexp with ten decimal 0d0
        appender.append(0xCC, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50);
        // struct with system symbol field names and int 0 values (some overpadded)
        appender.append(0xDD, 0x84, 0x20, 0x85, 0x20, 0x86, 0x20, 0x87, 0x21, 0x00, 0x88, 0x22, 0x00, 0x00);
        byte[] data = appender.toByteArray();
        IonDatagram expectedValues = appender.datagram;
        for (int maxValueSize : Arrays.asList(5, 7, 8, 10, 13)) {
            if (maxValueSize < initialBufferSize) {
                // This would violate the IonBufferConfiguration contract; no need to test here.
                continue;
            }
            ByteArrayInputStream input = new ByteArrayInputStream(data);
            RecordingEventHandler eventHandler = new RecordingEventHandler();
            IonBufferConfiguration.Builder builder = IonBufferConfiguration.Builder.standard()
                .withMaximumBufferSize(maxValueSize)
                .withHandler(eventHandler)
                .withInitialBufferSize(initialBufferSize);
            IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(builder.build(), input);
            IonReader reader = null;
            try {
                int valueIndex = 0;
                while (true) {
                    lookahead.fillInput();
                    if (lookahead.moreDataRequired()) {
                        break;
                    }
                    if (reader == null) {
                        reader = lookahead.newIonReader(IonReaderBuilder.standard());
                    }
                    assertNotNull(reader.next());
                    assertTrue(Equivalence.ionEquals(expectedValues.get(valueIndex), BinaryIonAppender.SYSTEM.newValue(reader)));
                    valueIndex++;
                }
                // There is one value per size. For a max size of x, `moreDataRequired` should be false x times.
                assertEquals(valueIndex, maxValueSize);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
            int expectedOversizedValues = expectedValues.size() - maxValueSize;
            assertEquals(
                String.format("Expect %d oversized values for max size %d", expectedOversizedValues, maxValueSize),
                expectedOversizedValues,
                eventHandler.oversizedValueCount
            );
            assertEquals(0, eventHandler.oversizedSymbolTableCount);
            ResizingPipedInputStream buffer = (ResizingPipedInputStream) lookahead.getPipe();
            // The buffer grows in increments of its initial buffer size. Therefore, the final capacity will be at
            // most `initialBufferSize` larger than the lookahead wrapper's configured max value size.
            assertTrue(buffer.capacity() <= maxValueSize + buffer.getInitialBufferSize());
        }
    }

    @Test
    public void skipOversizedValuesButRetainSymbolTablesWhenReadingOneByOne() throws Exception {
        if (initialBufferSize == null) {
            // This test tests buffers of limited size, while an initialBufferSize of null indicates that the size
            // of the buffer is unlimited. Skip.
            return;
        }
        // 4 byte IVM + 30 bytes for the symbol tables + 2 bytes for a symbol value.
        final int maximumSize = 36;
        BinaryIonAppender appender = new BinaryIonAppender();
        appender.append(0xE0, 0x01, 0x00, 0xEA);
        // Total buffered data: 4 bytes. The following value is 33 bytes in order to exceed the max of 36.
        appender.append(0xBE, 0x9F) // list of size 31
                .append(0x7E, 0x00, 0x00, 0x00, 0x00, 0x82, 0x00, 0x00) // symbol 0 (overpadded length and value)
                .append(0xAA, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A) // blob
                .append(0xBB, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20); // list with ten int 0
        appender.append(0xE7, 0x81, 0x83, 0xD4, 0x87, 0xB2, 0x81, 'A'); // symbol table declaring the symbol 'A'.
        // Total buffered data: 4 + 8 = 12 bytes. The following value is 25 bytes in order to exceed the max of 36.
        appender.append(0xBE, 0x97) // list of size 23
                .append(0xAA, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A) // blob
                .append(0xBB, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20); // list with ten int 0
        appender.append(0xEA, 0x81, 0x83, 0xD7, 0x86, 0x71, 0x03, 0x87, 0xB2, 0x81, 'B'); // symbol table appending the symbol 'B'.
        // Total buffered data: 4 + 8 + 11 = 23 bytes. The following value is 14 bytes in order to exceed the max of 36.
        // struct with system symbol field names and int 0 values (some overpadded)
        appender.append(0xDD, 0x84, 0x20, 0x85, 0x20, 0x86, 0x20, 0x87, 0x21, 0x00, 0x88, 0x22, 0x00, 0x00);
        appender.append(0xEA, 0x81, 0x83, 0xD7, 0x86, 0x71, 0x03, 0x87, 0xB2, 0x81, 'C'); // symbol table appending the symbol 'C'.
        // Total buffered data: 4 + 8 + 11 + 11 = 34 bytes. The following value is 3 bytes in order to exceed the max of 36.
        appender.append(0x22, 0x00, 0x01); // int 1 (overpadded)
        appender.append(0x71, 0x0A); // symbol 'A'
        // Since the previous value was successfully read, the buffer is now empty. A 3-byte value can be read.
        appender.append(0x72, 0x00, 0x0B); // symbol 'B'
        appender.append(0x71, 0x0C); // symbol 'C'

        ByteArrayInputStream input = new ByteArrayInputStream(appender.toByteArray());
        RecordingEventHandler eventHandler = new RecordingEventHandler();
        IonBufferConfiguration.Builder builder = IonBufferConfiguration.Builder.standard()
            .withMaximumBufferSize(maximumSize)
            .withHandler(eventHandler)
            .withInitialBufferSize(initialBufferSize);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(builder.build(), input);
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        IonReader reader = lookahead.newIonReader(IonReaderBuilder.standard());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("A", reader.stringValue());

        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("B", reader.stringValue());

        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("C", reader.stringValue());

        lookahead.fillInput();
        assertTrue(lookahead.moreDataRequired());
        reader.close();

        assertEquals(4, eventHandler.oversizedValueCount);
        assertEquals(0, eventHandler.oversizedSymbolTableCount);
        ResizingPipedInputStream buffer = (ResizingPipedInputStream) lookahead.getPipe();
        // The buffer grows in increments of its initial buffer size. Therefore, the final capacity will be at
        // most `initialBufferSize` larger than the lookahead wrapper's configured max value size.
        assertTrue(buffer.capacity() <= maximumSize + buffer.getInitialBufferSize());
    }

    @Test
    public void skipOversizedValuesButRetainSymbolTablesWhenReadingAllAtOnce() throws Exception {
        if (initialBufferSize == null) {
            // This test tests buffers of limited size, while an initialBufferSize of null indicates that the size
            // of the buffer is unlimited. Skip.
            return;
        }
        // 4 byte IVM + 8 bytes for the symbol tables + 6 bytes for the integer and symbol values.
        final int maximumSize = 18;
        BinaryIonAppender appender = new BinaryIonAppender();
        appender.append(0xE0, 0x01, 0x00, 0xEA);
        appender.append(0xE7, 0x81, 0x83, 0xD4, 0x87, 0xB2, 0x81, 'A'); // symbol table declaring the symbol 'A'.
        // Total buffered data: 12 bytes. The following value is 7 bytes in order to exceed the max of 18.
        appender.append(0x66, 0x80, 0x81, 0x81, 0x81, 0x80, 0x80); // timestamp 0001-01-01T00:00Z
        appender.append(0x21, 0x01); // int 1
        appender.append(0x71, 0x0A); // symbol 'A'
        appender.append(0x31, 0x01); // int -1

        ByteArrayInputStream input = new ByteArrayInputStream(appender.toByteArray());
        RecordingEventHandler eventHandler = new RecordingEventHandler();
        IonBufferConfiguration.Builder builder = IonBufferConfiguration.Builder.standard()
            .withMaximumBufferSize(maximumSize)
            .withHandler(eventHandler)
            .withInitialBufferSize(initialBufferSize);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(builder.build(), input);
        // Skips the oversized values and buffers the int 1.
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());

        // Buffers the symbol value 'A'.
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());

        // Buffers the int -1.
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());

        // End of the input.
        lookahead.fillInput();
        assertTrue(lookahead.moreDataRequired());

        IonReader reader = lookahead.newIonReader(IonReaderBuilder.standard());
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());

        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("A", reader.stringValue());

        assertEquals(IonType.INT, reader.next());
        assertEquals(-1, reader.intValue());

        assertNull(reader.next());
        reader.close();

        assertEquals(1, eventHandler.oversizedValueCount);
        assertEquals(0, eventHandler.oversizedSymbolTableCount);
        ResizingPipedInputStream buffer = (ResizingPipedInputStream) lookahead.getPipe();
        // The buffer grows in increments of its initial buffer size. Therefore, the final capacity will be at
        // most `initialBufferSize` larger than the lookahead wrapper's configured max value size.
        assertTrue(buffer.capacity() <= maximumSize + buffer.getInitialBufferSize());
    }

    @Test
    public void oversizedSymbolTable() throws Exception {
        // 4 byte IVM + 3 byte value.
        final int maximumSize = 7;
        if (initialBufferSize == null || initialBufferSize > maximumSize) {
            // This test tests buffers of limited size, while an initialBufferSize of null indicates that the size
            // of the buffer is unlimited. Skip.
            return;
        }
        BinaryIonAppender appender = new BinaryIonAppender();
        appender.append(0xE0, 0x01, 0x00, 0xEA);
        appender.append(0x22, 0x00, 0x01); // int 1 (overpadded)
        appender.append(0xE7, 0x81, 0x83, 0xD4, 0x87, 0xB2, 0x81, 'A'); // symbol table declaring the symbol 'A'.
        appender.append(0x71, 0x0A); // symbol 'A'

        ByteArrayInputStream input = new ByteArrayInputStream(appender.toByteArray());
        RecordingEventHandler eventHandler = new RecordingEventHandler();
        IonBufferConfiguration.Builder builder = IonBufferConfiguration.Builder.standard()
            .withMaximumBufferSize(maximumSize)
            .withHandler(eventHandler)
            .withInitialBufferSize(initialBufferSize);
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(builder.build(), input);

        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        IonReader reader = lookahead.newIonReader(IonReaderBuilder.standard());
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());

        // This causes the oversized symbol table to be skipped.
        lookahead.fillInput();
        assertEquals(0, lookahead.available());
        assertTrue(lookahead.moreDataRequired());
        assertEquals(1, eventHandler.oversizedSymbolTableCount);

        // Subsequent invocations of fillInput have no effect.
        lookahead.fillInput();
        assertEquals(0, lookahead.available());
        assertTrue(lookahead.moreDataRequired());
        assertEquals(1, eventHandler.oversizedSymbolTableCount);
        reader.close();

        assertEquals(0, eventHandler.oversizedValueCount);
        ResizingPipedInputStream buffer = (ResizingPipedInputStream) lookahead.getPipe();
        // The buffer grows in increments of its initial buffer size. Therefore, the final capacity will be at
        // most `initialBufferSize` larger than the lookahead wrapper's configured max value size.
        assertTrue(buffer.capacity() <= maximumSize + buffer.getInitialBufferSize());
    }

    @Test
    public void moreDataRequiredInTheMiddleOfOversizedValue() throws Exception {
        // 4 byte IVM + 3 byte value.
        final int maximumSize = 7;
        if (initialBufferSize == null || initialBufferSize > maximumSize) {
            // This test tests buffers of limited size, while an initialBufferSize of null indicates that the size
            // of the buffer is unlimited. Skip.
            return;
        }
        BinaryIonAppender appender = new BinaryIonAppender();
        appender.append(0xE0, 0x01, 0x00, 0xEA);
        // Once the fourth byte in the following value is reached, the value is considered oversized. It will remain
        // that way even though fillInput yields back to the user for every remaining byte in the value.
        appender.append(0x66, 0x80, 0x81, 0x81, 0x81, 0x80, 0x80); // timestamp 0001-01-01T00:00Z
        appender.append(0x20); // int 0
        final RecordingEventHandler eventHandler = new RecordingEventHandler();
        builderSupplier = new BuilderSupplier() {
            @Override
            public IonBufferConfiguration.Builder get() {
                return IonBufferConfiguration.Builder.standard()
                    .withMaximumBufferSize(maximumSize)
                    .withHandler(eventHandler);
            }
        } ;

        readSingleValueOneByteAtATime(
            appender.toByteArray(),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.INT, reader.next());
                    assertEquals(0, reader.intValue());
                    assertNull(reader.next());
                }
            }
        );

        assertEquals(1, eventHandler.oversizedValueCount);
        assertEquals(0, eventHandler.oversizedSymbolTableCount);
    }

    @Test
    public void oversizedIncompleteGzipValueDoesNotThrow() throws Exception {
        // Tests that InputStream implementations that throw EOFException when too many bytes are requested to be
        // skipped do not cause the lookahead wrapper to throw.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStream gzip = new GZIPOutputStream(out);
        gzip.write(bytes(0xE0, 0x01, 0x00, 0xEA));
        gzip.write(bytes(0x66, 0x80, 0x81, 0x81, 0x81, 0x80, 0x80)); // timestamp 0001-01-01T00:00Z
        gzip.close();
        byte[] bytes = out.toByteArray();
        final int maximumSize = 5; // Less than the length of the timestamp value.
        // Cutting off 15 bytes removes the entire GZIP trailer and part of the value.
        byte[] truncatedBytes = new byte[bytes.length - 15];
        System.arraycopy(bytes, 0, truncatedBytes, 0, truncatedBytes.length);
        final RecordingEventHandler eventHandler = new RecordingEventHandler();
        builderSupplier = new BuilderSupplier() {
            @Override
            public IonBufferConfiguration.Builder get() {
                return IonBufferConfiguration.Builder.standard()
                    .withMaximumBufferSize(maximumSize)
                    .withInitialBufferSize(maximumSize)
                    .withHandler(eventHandler);
            }
        };

        InputStream input = new GZIPInputStream(new ByteArrayInputStream(truncatedBytes));
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(builderSupplier.get().build(), input);
        lookahead.fillInput();
        assertTrue(lookahead.moreDataRequired());
        lookahead.fillInput();
        assertTrue(lookahead.moreDataRequired());
        input.close();
        // Note: even though the value would be considered oversize, the event handler may not have been notified
        // before the input is closed, so nothing is asserted here. The purpose of this test is to verify that
        // an EOFException is not thrown.
    }

    @Test
    public void rewindToValueStartWithLstAppend() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().withLocalSymbolTableAppendEnabled().build(out);
        writer.writeSymbol("abc");
        writer.flush();
        writer.writeSymbol("def");
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(builderSupplier.get().build(), input);
        lookahead.fillInput();
        IonReader reader = lookahead.newIonReader(IonReaderBuilder.standard());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("abc", reader.stringValue());
        assertTrue(lookahead.moreDataRequired());
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("def", reader.stringValue());
        lookahead.rewindToValueStart();
        // 2-byte value (0x71 0x0B). No IVM or symbol table.
        assertEquals(2, lookahead.available());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("def", reader.stringValue());
        // Note: if mark() / rewind() is used instead of rewindToValueStart(), the following lines will fail; there
        // will be 12 local symbols and "def" will occur twice in the symbol table. That's because mark() includes
        // the symbol table (in this case an LST append), and rewinding past the symbol table causes the append
        // to be processed a second time by IonReader.next().
        SymbolTable symbolTable = reader.getSymbolTable();
        assertEquals(symbolTable.getSystemSymbolTable().getMaxId() + 2, reader.getSymbolTable().getMaxId());
        List<String> symbols = new ArrayList<String>(2);
        Iterator<String> iterator = symbolTable.iterateDeclaredSymbolNames();
        while (iterator.hasNext()) {
            symbols.add(iterator.next());
        }
        assertEquals(Arrays.asList("abc", "def"), symbols);
        input.close();
    }

    private static void assertIonTypeId(IonType expectedType, int expectedLowerNibble, IonTypeID actualTypeID) {
        assertEquals(expectedType, actualTypeID.type);
        assertEquals(expectedLowerNibble, actualTypeID.lowerNibble);
    }

    /**
     * Returns one of the provided indices, depending on the value of the 'initialBufferSize' test parameter. Bytes may
     * occur at different indices in the buffer depending on the buffer's initial size because space will be reclaimed
     * when possible to avoid unnecessary growth. So a buffer that starts out larger than the total size of the data,
     * for example, will have ever-increasing indices, while smaller buffers may have indices that roll back to the
     * beginning when space is reclaimed.
     * @param indexIfSizeIs1 the expected index when the initial buffer size is 1.
     * @param indexIfSizeIs10 the expected index when the initial buffer size is 10.
     * @param indexIfSizeIsLarge the expected index when the initial buffer size is larger than the test data.
     * @return the index for the current value of initialBufferSize.
     */
    private int indexForInitialBufferSize(int indexIfSizeIs1, int indexIfSizeIs10, int indexIfSizeIsLarge) {
        if (initialBufferSize == null) {
            return indexIfSizeIsLarge;
        }
        if (initialBufferSize == 1) {
            return indexIfSizeIs1;
        }
        if (initialBufferSize == 10) {
            return indexIfSizeIs10;
        }
        throw new IllegalStateException("Add a branch for initialBufferSize " + initialBufferSize);
    }

    @Test
    public void valueMarkersAreSet() throws Exception {
        BinaryIonAppender appender = new BinaryIonAppender();
        // Second byte of IVM is at byte index 1.
        appender.append(0xE0, 0x01, 0x00, 0xEA);
        // Value start is at byte index 5. Type ID is 0x21. Value end is at byte index 6.
        appender.append(0x21, 0x0D);
        // The value will be consumed, so the indices reset if the initial buffer size is smaller than the data size.
        appender.append(0xE0, 0x01, 0x00, 0xEA);
        // Type ID is 0xD1.
        appender.append(0xD1, 0x82, 0x84, 0x20);
        // The value will not be consumed, so the indices continue.
        // Annotations are SIDs 4 (name) and 5 (version). Type ID is 0xE5.
        appender.append(0xE5, 0x82, 0x84, 0x85, 0x31, 0x01);
        // The value will not be consumed, so the indices continue.
        // Type ID is 0x20.
        appender.append(0x20);
        InputStream input = new ByteArrayInputStream(appender.toByteArray());
        IonBufferConfiguration.Builder configurationBuilder = IonBufferConfiguration.Builder.standard();
        if (initialBufferSize != null) {
            configurationBuilder.withInitialBufferSize(initialBufferSize);
        }
        IonReaderLookaheadBuffer lookahead =  new IonReaderLookaheadBuffer(configurationBuilder.build(), input);
        lookahead.fillInput();
        assertEquals(1, lookahead.getIvmIndex());
        assertTrue(lookahead.getAnnotationSids().isEmpty());
        assertTrue(lookahead.getSymbolTableMarkers().isEmpty());
        assertEquals(5, lookahead.getValueStart());
        assertIonTypeId(IonType.INT, 0x1, lookahead.getValueTid());
        assertEquals(6, lookahead.getValueEnd());
        IonReader reader = lookahead.newIonReader(IonReaderBuilder.standard());
        assertEquals(IonType.INT, reader.next());
        assertEquals(0x0D, reader.intValue());
        assertTrue(lookahead.moreDataRequired());

        lookahead.fillInput();
        assertEquals(indexForInitialBufferSize(1, 1, 7), lookahead.getIvmIndex());
        lookahead.resetIvmIndex();
        assertEquals(-1, lookahead.getIvmIndex());
        assertTrue(lookahead.getAnnotationSids().isEmpty());
        assertTrue(lookahead.getSymbolTableMarkers().isEmpty());
        assertEquals(indexForInitialBufferSize(6, 6, 12), lookahead.getValueStart());
        assertIonTypeId(IonType.STRUCT, 0x1, lookahead.getValueTid());
        assertEquals(indexForInitialBufferSize(8, 8, 14), lookahead.getValueEnd());

        lookahead.fillInput();
        assertEquals(-1, lookahead.getIvmIndex());
        assertTrue(lookahead.getSymbolTableMarkers().isEmpty());
        assertEquals(Arrays.asList(4, 5), lookahead.getAnnotationSids());
        assertEquals(indexForInitialBufferSize(12, 12, 18), lookahead.getValueStart());
        assertIonTypeId(IonTypeID.ION_TYPE_ANNOTATION_WRAPPER, 0x5, lookahead.getValueTid());
        assertEquals(indexForInitialBufferSize(14, 14, 20), lookahead.getValueEnd());

        lookahead.fillInput();
        assertEquals(-1, lookahead.getIvmIndex());
        assertTrue(lookahead.getSymbolTableMarkers().isEmpty());
        assertTrue(lookahead.getAnnotationSids().isEmpty());
        assertEquals(indexForInitialBufferSize(15, 15, 21), lookahead.getValueStart());
        assertIonTypeId(IonType.INT, 0x0, lookahead.getValueTid());
        assertEquals(indexForInitialBufferSize(15, 15, 21), lookahead.getValueEnd());

        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.INT, reader.next());
        assertEquals("name", reader.getFieldName());
        assertEquals(0, reader.intValue());
        reader.stepOut();

        assertEquals(IonType.INT, reader.next());
        assertArrayEquals(new String[]{"name", "version"}, reader.getTypeAnnotations());
        assertEquals(-1, reader.intValue());

        assertEquals(IonType.INT, reader.next());
        assertEquals(0, reader.intValue());

        assertTrue(lookahead.moreDataRequired());
        reader.close();
        input.close();
    }

    @Test
    public void symbolTableMarkersAreSet() throws Exception {
        BinaryIonAppender appender = new BinaryIonAppender();
        // Second byte of IVM is at byte index 1.
        appender.append(0xE0, 0x01, 0x00, 0xEA);
        // Symbol table declaring symbol 'x'.
        appender.append(0xE7, 0x81, 0x83, 0xD4, 0x87, 0xB2, 0x81, 'x');
        // Symbol value with SID 10 ('x').
        appender.append(0x71, 0x0A);
        // The value will not be consumed, so the indices continue.
        // Symbol table appending symbol 'y'.
        appender.append(0xEA, 0x81, 0x83, 0xD7, 0x86, 0x71, 0x03, 0x87, 0xB2, 0x81, 'y');
        // Symbol value with SID 11 ('y').
        appender.append(0x71, 0x0B);
        // The value will be consumed, so the indices reset if the initial buffer size is smaller than the data size.
        // Second byte of IVM is at byte index 1. The IVM resets the symbol table markers.
        appender.append(0xE0, 0x01, 0x00, 0xEA);
        // Symbol table declaring symbol 'a'.
        appender.append(0xE7, 0x81, 0x83, 0xD4, 0x87, 0xB2, 0x81, 'a');
        // Symbol table declaring symbol 'b'.
        appender.append(0xE7, 0x81, 0x83, 0xD4, 0x87, 0xB2, 0x81, 'b');
        // The IVM resets the symbol table markers.
        appender.append(0xE0, 0x01, 0x00, 0xEA);
        // Symbol table declaring symbol 'c'.
        appender.append(0xE7, 0x81, 0x83, 0xD4, 0x87, 0xB2, 0x81, 'c');
        // Symbol table declaring symbol 'd'.
        appender.append(0xE7, 0x81, 0x83, 0xD4, 0x87, 0xB2, 0x81, 'd');
        // Symbol value with SID 10 ('d').
        appender.append(0x71, 0x0A);
        InputStream input = new ByteArrayInputStream(appender.toByteArray());
        IonBufferConfiguration.Builder configurationBuilder = IonBufferConfiguration.Builder.standard();
        if (initialBufferSize != null) {
            configurationBuilder.withInitialBufferSize(initialBufferSize);
        }
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(configurationBuilder.build(), input);
        lookahead.fillInput();
        assertEquals(1, lookahead.getIvmIndex());
        assertTrue(lookahead.getAnnotationSids().isEmpty());
        List<IonReaderLookaheadBuffer.SymbolTableMarker> symbolTableMarkers = lookahead.getSymbolTableMarkers();
        assertEquals(1, symbolTableMarkers.size());
        assertEquals(8, symbolTableMarkers.get(0).startIndex);
        assertEquals(12, symbolTableMarkers.get(0).endIndex);
        assertEquals(13, lookahead.getValueStart());
        assertIonTypeId(IonType.SYMBOL, 0x1, lookahead.getValueTid());
        assertEquals(14, lookahead.getValueEnd());
        lookahead.resetSymbolTableMarkers();
        assertTrue(lookahead.getSymbolTableMarkers().isEmpty());

        lookahead.fillInput();
        symbolTableMarkers = lookahead.getSymbolTableMarkers();
        assertEquals(1, symbolTableMarkers.size());
        assertEquals(18, symbolTableMarkers.get(0).startIndex);
        assertEquals(25, symbolTableMarkers.get(0).endIndex);
        assertEquals(26, lookahead.getValueStart());
        assertIonTypeId(IonType.SYMBOL, 0x1, lookahead.getValueTid());
        assertEquals(27, lookahead.getValueEnd());

        IonReader reader = lookahead.newIonReader(IonReaderBuilder.standard());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("x", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("y", reader.stringValue());

        lookahead.fillInput();
        symbolTableMarkers = lookahead.getSymbolTableMarkers();
        assertEquals(2, symbolTableMarkers.size());
        assertEquals(indexForInitialBufferSize(28, 28, 55), symbolTableMarkers.get(0).startIndex);
        assertEquals(indexForInitialBufferSize(32, 32, 59), symbolTableMarkers.get(0).endIndex);
        assertEquals(indexForInitialBufferSize(36, 36, 63), symbolTableMarkers.get(1).startIndex);
        assertEquals(indexForInitialBufferSize(40, 40, 67), symbolTableMarkers.get(1).endIndex);
        assertEquals(indexForInitialBufferSize(41, 41, 68), lookahead.getValueStart());
        assertIonTypeId(IonType.SYMBOL, 0x1, lookahead.getValueTid());
        assertEquals(indexForInitialBufferSize(42, 42, 69), lookahead.getValueEnd());

        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("d", reader.stringValue());

        assertTrue(lookahead.moreDataRequired());
        reader.close();
        input.close();
    }

    @Test
    public void nopPadFollowedByValueThatOverflowsBufferNotOversized() throws Exception {
        if (initialBufferSize == null || initialBufferSize != 1) {
            return;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(bytes(0x03, 0x00, 0x00, 0x00)); // 4 byte NOP pad.
        out.write(bytes(0x21, 0x01)); // Int 1.
        // The IVM is 4 bytes and the NOP pad is 4 bytes. The first byte of the value is the 9th byte and fits in the
        // buffer. Even though there is a 10th byte, the value should not be considered oversize because the NOP pad
        // can be discarded.
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonBufferConfiguration.Builder configurationBuilder = IonBufferConfiguration.Builder.standard()
            .withInitialBufferSize(9)
            .withMaximumBufferSize(9)
            .withHandler(createCountingEventHandler(new AtomicLong()));
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(configurationBuilder.build(), input);
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        IonReader reader = lookahead.newIonReader(IonReaderBuilder.standard());
        lookahead.resetNopPadIndex();
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());
        reader.close();
    }

    @Test
    public void nopPadsInterspersedWithSystemValuesDoNotCauseOversizedErrors() throws Exception {
        if (initialBufferSize == null || initialBufferSize != 1) {
            return;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        // One byte no-op pad.
        out.write(0x00);
        // Two byte no-op pad.
        out.write(0x01);
        out.write(0xFF);
        // Three byte no-op pad.
        out.write(0x02);
        out.write(0x99);
        out.write(0x42);
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        // 15-byte no-op pad.
        out.write(0x0E);
        out.write(0x8D);
        out.write(new byte[13]);
        // Symbol table with the symbol 'hello'.
        out.write(bytes(0xEB, 0x81, 0x83, 0xD8, 0x87, 0xB6, 0x85, 'h', 'e', 'l', 'l', 'o'));
        // Three byte no-op pad.
        out.write(0x02);
        out.write(0x99);
        out.write(0x42);
        // Symbol 10 (hello)
        out.write(bytes(0x71, 0x0A));

        // Set the maximum size at 2 IVMs (8 bytes) + the symbol table (12 bytes) + the value (2 bytes).
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonBufferConfiguration.Builder configurationBuilder = IonBufferConfiguration.Builder.standard()
            .withInitialBufferSize(22)
            .withMaximumBufferSize(22)
            .withHandler(createCountingEventHandler(new AtomicLong()));
        IonReaderLookaheadBuffer lookahead = new IonReaderLookaheadBuffer(configurationBuilder.build(), input);
        lookahead.fillInput();
        assertFalse(lookahead.moreDataRequired());
        IonReader reader = lookahead.newIonReader(IonReaderBuilder.standard());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("hello", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }
}
