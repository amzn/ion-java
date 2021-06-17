package com.amazon.ion.impl;

import com.amazon.ion.BufferConfiguration;
import com.amazon.ion.BufferEventHandler;
import com.amazon.ion.Decimal;
import com.amazon.ion.IonLob;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonText;
import com.amazon.ion.IonType;
import com.amazon.ion.SymbolToken;
import com.amazon.ion.Timestamp;
import com.amazon.ion.system.IonReaderBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;

public abstract class IonReaderLookaheadBufferTestBase<
    T extends BufferEventHandler,
    U extends BufferConfiguration<T, U>,
    V extends BufferConfiguration.Builder<T, U, V>
> {

    private static final IonSystem ION_SYSTEM = IonSystemBuilder.standard().build();
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Parameters(name = "initialBufferSize={0}")
    public static Iterable<Integer> initialBufferSizes() {
        return Arrays.asList(1, 10, null);
    }

    @Parameter
    public Integer initialBufferSize;

    private T eventHandler = null;

    private Integer maximumBufferSize = null;

    @Before
    public void setup() {
        eventHandler = null;
        maximumBufferSize = null;
    }

    interface IonReaderAssertion {
        void evaluate(IonReader reader);
    }

    static class Value {
        byte[] stream;
        IonReaderAssertion assertion;

        Value(byte[] stream, IonReaderAssertion assertion) {
            this.stream = stream;
            this.assertion = assertion;
        }
    }

    abstract V createLookaheadWrapperBuilder();

    abstract ReaderLookaheadBufferBase build(V builder, InputStream inputStream);

    abstract T createThrowingEventHandler();
    abstract T createCountingEventHandler(AtomicLong byteCount);

    void readValuesOneByteAtATime(Value... values) throws Exception {
        IonReader reader = null;
        ResizingPipedInputStream input = new ResizingPipedInputStream(1);
        V builder = createLookaheadWrapperBuilder();
        if (initialBufferSize != null) {
            builder.withInitialBufferSize(initialBufferSize);
        }
        if (maximumBufferSize != null) {
            builder.withMaximumBufferSize(maximumBufferSize);
        }
        if (eventHandler != null) {
            builder.withHandler(eventHandler);
        }
        ReaderLookaheadBufferBase lookahead = build(builder, input);
        try {
            for (Value value : values) {
                for (int i = -1; i < value.stream.length; i++) {
                    if (i >= 0) {
                        input.receive(value.stream[i]);
                        lookahead.fillInput();
                    }
                    assertEquals(i < value.stream.length - 1, lookahead.moreDataRequired());
                }
                if (reader == null) {
                    reader = lookahead.newIonReader(IonReaderBuilder.standard());
                }
                value.assertion.evaluate(reader);
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    void readSingleValueOneByteAtATime(byte[] stream, IonReaderAssertion assertion) throws Exception {
        readValuesOneByteAtATime(new Value(stream, assertion));
    }

    abstract byte[] toBytes(String textIon) throws IOException;
    abstract byte[] intZeroWithoutIvm();

    private void typedNull(final IonType type) throws Exception {
        readSingleValueOneByteAtATime(
            toBytes("null." + type.name().toLowerCase()),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(type, reader.next());
                    assertTrue(reader.isNullValue());
                }
            }
        );
    }

    @Test
    public void nulls() throws Exception {
        for (IonType type : IonType.values()) {
            if (type == IonType.DATAGRAM) continue;
            typedNull(type);
        }
    }

    private void booleanValue(final boolean value) throws Exception {
        readSingleValueOneByteAtATime(
            toBytes("" + value),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.BOOL, reader.next());
                    assertEquals(value, reader.booleanValue());
                }
            }
        );
    }

    @Test
    public void booleans() throws Exception {
        booleanValue(true);
        booleanValue(false);
    }

    private void integerValue(final int value) throws Exception {
        readSingleValueOneByteAtATime(
            toBytes("" + value),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.INT, reader.next());
                    assertEquals(value, reader.intValue());
                }
            }
        );
    }

    @Test
    public void integers() throws Exception {
        integerValue(0);
        integerValue(-1);
        integerValue(Integer.MAX_VALUE);
        integerValue(Integer.MIN_VALUE);
    }

    private void floatValue(final String ion, final double value) throws Exception {
        readSingleValueOneByteAtATime(
            toBytes(ion),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.FLOAT, reader.next());
                    assertEquals(value, reader.doubleValue(), 1e-9);
                }
            }
        );
    }

    @Test
    public void floats() throws Exception {
        floatValue("0e0", 0.);
        floatValue("nan", Double.NaN);
        floatValue("+inf", Double.POSITIVE_INFINITY);
        floatValue("-inf", Double.NEGATIVE_INFINITY);
    }

    private void decimalValue(final String ion, final Decimal value) throws Exception {
        readSingleValueOneByteAtATime(
            toBytes(ion),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.DECIMAL, reader.next());
                    assertEquals(value, reader.decimalValue());
                }
            }
        );
    }

    @Test
    public void decimals() throws Exception {
        decimalValue("0.", Decimal.ZERO);
        decimalValue("-0.", Decimal.NEGATIVE_ZERO);
        decimalValue("1000000000000.0", Decimal.valueOf(new BigDecimal("1000000000000.0")));
    }

    private void timestampValue(final Timestamp value) throws Exception {
        readSingleValueOneByteAtATime(
            toBytes(value.toString()),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.TIMESTAMP, reader.next());
                    assertEquals(value, reader.timestampValue());
                }
            }
        );
    }

    @Test
    public void timestamps() throws Exception {
        timestampValue(Timestamp.valueOf("2000T"));
        timestampValue(Timestamp.valueOf("2000-01T"));
        timestampValue(Timestamp.valueOf("2000-01-01"));
        timestampValue(Timestamp.valueOf("2000-01-01T00:00Z"));
        timestampValue(Timestamp.valueOf("2000-01-01T00:00:00Z"));
        timestampValue(Timestamp.valueOf("2000-01-01T00:00:00.000Z"));
        timestampValue(Timestamp.valueOf("2000-01-01T00:00:00.000-07:00"));
    }

    private void textValue(final IonText value) throws Exception {
        readSingleValueOneByteAtATime(
            toBytes(value.toString()),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(value.getType(), reader.next());
                    assertEquals(value.stringValue(), reader.stringValue());
                }
            }
        );
    }

    @Test
    public void symbols() throws Exception {
        // NOTE: these exercise skipping over a symbol table.
        textValue(ION_SYSTEM.newSymbol(""));
        textValue(ION_SYSTEM.newString("abc"));
        textValue(ION_SYSTEM.newString("abcdefghijklmnopqrstuvwxyz0123456789"));
    }

    @Test
    public void symbolWithUnknownText() throws Exception {
        readSingleValueOneByteAtATime(
            toBytes("$0"),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.SYMBOL, reader.next());
                    SymbolToken symbolToken = reader.symbolValue();
                    assertNull(symbolToken.getText());
                    assertEquals(0, symbolToken.getSid());
                }
            }
        );
    }

    @Test
    public void strings() throws Exception {
        textValue(ION_SYSTEM.newString(""));
        textValue(ION_SYSTEM.newString("abc"));
        textValue(ION_SYSTEM.newString("abcdefghijklmnopqrstuvwxyz0123456789"));
    }

    private void lobValue(final IonLob lob) throws Exception {
        readSingleValueOneByteAtATime(
            toBytes(lob.toString()),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(lob.getType(), reader.next());
                    assertArrayEquals(lob.getBytes(), reader.newBytes());
                }
            }
        );
    }

    @Test
    public void clobs() throws Exception {
        lobValue(ION_SYSTEM.newClob("a".getBytes(UTF_8)));
        lobValue(ION_SYSTEM.newClob("abcdefghijklmnopqrstuvwxyz0123456789".getBytes(UTF_8)));
    }

    @Test
    public void blobs() throws Exception {
        lobValue(ION_SYSTEM.newBlob(new byte[]{}));
        lobValue(ION_SYSTEM.newBlob(new byte[]{ 0x01 }));
        lobValue(ION_SYSTEM.newBlob("abcdefghijklmnopqrstuvwxyz0123456789".getBytes(UTF_8)));
    }

    private void emptyContainer(final String ion, final IonType type) throws Exception {
        readSingleValueOneByteAtATime(
            toBytes(ion),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(type, reader.next());
                    reader.stepIn();
                    assertNull(reader.next());
                    reader.stepOut();
                }
            }
        );
    }

    @Test
    public void emptyContainers() throws Exception {
        emptyContainer("[]", IonType.LIST);
        emptyContainer("()", IonType.SEXP);
        emptyContainer("{}", IonType.STRUCT);
    }

    @Test
    public void annotations() throws Exception {
        readSingleValueOneByteAtATime(
            toBytes("abc::0"),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.INT, reader.next());
                    String[] annotations = reader.getTypeAnnotations();
                    assertEquals(1, annotations.length);
                    assertEquals("abc", annotations[0]);
                    assertEquals(0, reader.intValue());
                }
            }
        );
        readSingleValueOneByteAtATime(
            toBytes("abc::abcdefghijklmnopqrstuvwxyz0123456789"),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.SYMBOL, reader.next());
                    String[] annotations = reader.getTypeAnnotations();
                    assertEquals(1, annotations.length);
                    assertEquals("abc", annotations[0]);
                    assertEquals("abcdefghijklmnopqrstuvwxyz0123456789", reader.stringValue());
                }
            }
        );
        readSingleValueOneByteAtATime(
            toBytes("abc::def::abcdefghijklmnopqrstuvwxyz0123456789::\"abcdefghijklmnopqrstuvwxyz0123456789\""),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.STRING, reader.next());
                    String[] annotations = reader.getTypeAnnotations();
                    assertEquals(3, annotations.length);
                    assertEquals("abc", annotations[0]);
                    assertEquals("def", annotations[1]);
                    assertEquals("abcdefghijklmnopqrstuvwxyz0123456789", annotations[2]);
                    assertEquals("abcdefghijklmnopqrstuvwxyz0123456789", reader.stringValue());
                }
            }
        );
    }

    @Test
    public void list() throws Exception {
        readSingleValueOneByteAtATime(
            toBytes("[-0., 2000T, (abc)]"),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.LIST, reader.next());
                    reader.stepIn();
                    assertEquals(IonType.DECIMAL, reader.next());
                    assertEquals(Decimal.NEGATIVE_ZERO, reader.decimalValue());
                    assertEquals(IonType.TIMESTAMP, reader.next());
                    assertEquals(Timestamp.valueOf("2000T"), reader.timestampValue());
                    assertEquals(IonType.SEXP, reader.next());
                    assertNull(reader.next());
                    reader.stepOut();
                }
            }
        );
    }

    @Test
    public void sexp() throws Exception {
        readSingleValueOneByteAtATime(
            toBytes("'123'::($ion_symbol_table::{} abcdefghijklmnopqrstuvwxyz0123456789)"),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.SEXP, reader.next());
                    String[] annotations = reader.getTypeAnnotations();
                    assertEquals(1, annotations.length);
                    assertEquals("123", annotations[0]);
                    reader.stepIn();
                    assertEquals(IonType.STRUCT, reader.next());
                    annotations = reader.getTypeAnnotations();
                    assertEquals(1, annotations.length);
                    assertEquals("$ion_symbol_table", annotations[0]);
                    reader.stepIn();
                    assertNull(reader.next());
                    reader.stepOut();
                    assertEquals(IonType.SYMBOL, reader.next());
                    assertEquals("abcdefghijklmnopqrstuvwxyz0123456789", reader.stringValue());
                    assertNull(reader.next());
                    reader.stepOut();
                }
            }
        );
    }

    @Test
    public void struct() throws Exception {
        readSingleValueOneByteAtATime(
            toBytes("{'':[-1], a:b::nan, xyz:null.clob, bool:true}"),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.STRUCT, reader.next());
                    reader.stepIn();
                    assertEquals(IonType.LIST, reader.next());
                    assertEquals("", reader.getFieldName());
                    reader.stepIn();
                    assertEquals(IonType.INT, reader.next());
                    assertEquals(-1, reader.intValue());
                    assertNull(reader.next());
                    reader.stepOut();
                    assertEquals(IonType.FLOAT, reader.next());
                    assertEquals("a", reader.getFieldName());
                    String[] annotations = reader.getTypeAnnotations();
                    assertEquals(1, annotations.length);
                    assertEquals("b", annotations[0]);
                    assertEquals(Double.NaN, reader.doubleValue(), 1e-9);
                    assertEquals(IonType.CLOB, reader.next());
                    assertTrue(reader.isNullValue());
                    assertEquals(IonType.BOOL, reader.next());
                    assertEquals("bool", reader.getFieldName());
                    assertTrue(reader.booleanValue());
                    assertNull(reader.next());
                    reader.stepOut();
                }
            }
        );
    }

    @Test
    public void multipleTopLevelScalars() throws Exception {
        readValuesOneByteAtATime(
            new Value(
                toBytes("abc"),
                new IonReaderAssertion() {
                    @Override
                    public void evaluate(IonReader reader) {
                        assertEquals(IonType.SYMBOL, reader.next());
                        assertEquals("abc", reader.stringValue());
                    }
                }
            ),
            new Value(
                intZeroWithoutIvm(),
                new IonReaderAssertion() {
                    @Override
                    public void evaluate(IonReader reader) {
                        assertEquals(IonType.INT, reader.next());
                        assertEquals(0, reader.intValue());
                    }
                }
            )
        );
    }

    @Test
    public void multipleTopLevelValuesWithSystemValuesInBetween() throws Exception {
        readValuesOneByteAtATime(
            new Value(
                toBytes("0e0"),
                new IonReaderAssertion() {
                    @Override
                    public void evaluate(IonReader reader) {
                        assertEquals(IonType.FLOAT, reader.next());
                        assertEquals(0., reader.doubleValue(), 0e-9);
                    }
                }
            ),
            new Value(
                toBytes("abcdefghijklmnopqrstuvwxyz0123456789"), // Includes an IVM and symbol table.
                new IonReaderAssertion() {
                    @Override
                    public void evaluate(IonReader reader) {
                        assertEquals(IonType.SYMBOL, reader.next());
                        assertEquals("abcdefghijklmnopqrstuvwxyz0123456789", reader.stringValue());
                    }
                }
            ),
            new Value(
                toBytes("{foo:abc::2001-01T}"), // Includes an IVM and symbol table.
                new IonReaderAssertion() {
                    @Override
                    public void evaluate(IonReader reader) {
                        assertEquals(IonType.STRUCT, reader.next());
                        reader.stepIn();
                        assertEquals(IonType.TIMESTAMP, reader.next());
                        assertEquals("foo", reader.getFieldName());
                        String[] annotations = reader.getTypeAnnotations();
                        assertEquals(1, annotations.length);
                        assertEquals("abc", annotations[0]);
                        assertEquals(Timestamp.valueOf("2001-01T"), reader.timestampValue());
                        assertNull(reader.next());
                        reader.stepOut();
                    }
                }
            )
        );
    }

    @Test
    public void multipleTopLevelContainers() throws Exception {
        readValuesOneByteAtATime(
            new Value(
                toBytes("{foo:abc::2001-01T}"),
                new IonReaderAssertion() {
                    @Override
                    public void evaluate(IonReader reader) {
                        assertEquals(IonType.STRUCT, reader.next());
                        reader.stepIn();
                        assertEquals(IonType.TIMESTAMP, reader.next());
                        assertEquals("foo", reader.getFieldName());
                        String[] annotations = reader.getTypeAnnotations();
                        assertEquals(1, annotations.length);
                        assertEquals("abc", annotations[0]);
                        assertEquals(Timestamp.valueOf("2001-01T"), reader.timestampValue());
                        assertNull(reader.next());
                        reader.stepOut();
                    }
                }
            ),
            new Value(
                toBytes("[{foo:bar}, (baz zar), 123]"),
                new IonReaderAssertion() {
                    @Override
                    public void evaluate(IonReader reader) {
                        assertEquals(IonType.LIST, reader.next());
                        reader.stepIn();
                        assertEquals(IonType.STRUCT, reader.next());
                        reader.stepIn();
                        assertEquals(IonType.SYMBOL, reader.next());
                        assertEquals("foo", reader.getFieldName());
                        assertEquals("bar", reader.stringValue());
                        reader.stepOut();
                        assertEquals(IonType.SEXP, reader.next());
                        assertEquals(IonType.INT, reader.next());
                        assertEquals(123, reader.intValue());
                        assertNull(reader.next());
                        reader.stepOut();
                    }
                }
            ),
            new Value(
                toBytes("()"),
                new IonReaderAssertion() {
                    @Override
                    public void evaluate(IonReader reader) {
                        assertEquals(IonType.SEXP, reader.next());
                        reader.stepIn();
                        assertNull(reader.next());
                        reader.stepOut();
                    }
                }
            )
        );
    }

    @Test
    public void symbolTableAnnotationOnNonSymbolTable() throws Exception {
        readSingleValueOneByteAtATime(
            toBytes("$ion_symbol_table::[123]"),
            new IonReaderAssertion() {
                @Override
                public void evaluate(IonReader reader) {
                    assertEquals(IonType.LIST, reader.next());
                    String[] annotations = reader.getTypeAnnotations();
                    assertEquals(1, annotations.length);
                    assertEquals("$ion_symbol_table", annotations[0]);
                    reader.stepIn();
                    assertEquals(IonType.INT, reader.next());
                    assertEquals(123, reader.intValue());
                    reader.stepOut();
                }
            }
        );
    }

    @Test
    public void handlerWithUnlimitedMaxSizeCountsBytesProcessed() throws Exception {
        AtomicLong totalBytesProcessed = new AtomicLong();
        maximumBufferSize = Integer.MAX_VALUE;
        eventHandler = createCountingEventHandler(totalBytesProcessed);
        byte[] valueBytes = toBytes("[{foo:bar}, (baz zar), 123]");
        readValuesOneByteAtATime(
            new Value(
                valueBytes,
                new IonReaderAssertion() {
                    @Override
                    public void evaluate(IonReader reader) {
                        assertEquals(IonType.LIST, reader.next());
                        reader.stepIn();
                        assertEquals(IonType.STRUCT, reader.next());
                        reader.stepIn();
                        assertEquals(IonType.SYMBOL, reader.next());
                        assertEquals("foo", reader.getFieldName());
                        assertEquals("bar", reader.stringValue());
                        reader.stepOut();
                        assertEquals(IonType.SEXP, reader.next());
                        assertEquals(IonType.INT, reader.next());
                        assertEquals(123, reader.intValue());
                        assertNull(reader.next());
                        reader.stepOut();
                    }
                }
            )
        );
        assertEquals(valueBytes.length, totalBytesProcessed.longValue());
    }

    @Test
    public void errorOnSpecifiedMaxSizeAndNullHandler() {
        thrown.expect(IllegalArgumentException.class);
        build(
            createLookaheadWrapperBuilder()
                .withMaximumBufferSize(10)
                .withHandler(null),
            new ByteArrayInputStream(new byte[]{})
        );
    }

    @Test
    public void errorOnMaximumSizeLessThanFive() {
        BufferConfiguration.Builder<T, U, V> builder = createLookaheadWrapperBuilder();
        int minimumMaximumBufferSize = builder.getMinimumMaximumBufferSize();
        thrown.expect(IllegalArgumentException.class);
        build(
            builder.withMaximumBufferSize(minimumMaximumBufferSize - 1)
                .withHandler(createThrowingEventHandler()),
            new ByteArrayInputStream(new byte[]{})
        );
    }
}
