package com.amazon.ion.impl;

import com.amazon.ion.Decimal;
import com.amazon.ion.IntegerSize;
import com.amazon.ion.IonBufferConfiguration;
import com.amazon.ion.IonBufferEventHandler;
import com.amazon.ion.IonException;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.SymbolTable;
import com.amazon.ion.SymbolToken;
import com.amazon.ion.Timestamp;
import com.amazon.ion.impl.bin._Private_IonManagedBinaryWriterBuilder;
import com.amazon.ion.impl.bin._Private_IonManagedWriter;
import com.amazon.ion.impl.bin._Private_IonRawWriter;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.SimpleCatalog;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.amazon.ion.BitUtils.bytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IonReaderBinaryIncrementalTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void readInts() throws Exception {
        int numberOfValues = 1000000;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        for (int i = -(numberOfValues / 2); i < numberOfValues / 2; i++) {
            writer.writeInt(i);
        }
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        for (int i = -(numberOfValues / 2); i < numberOfValues / 2; i++) {
            assertEquals(IonType.INT, reader.next());
            assertEquals(i, reader.intValue());
        }
        reader.close();
    }

    @Test
    public void emptyContainers() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.stepIn(IonType.LIST);
        writer.stepIn(IonType.STRUCT);
        writer.stepOut();
        writer.stepIn(IonType.LIST);
        writer.stepOut();
        writer.stepIn(IonType.SEXP);
        writer.stepOut();
        writer.stepOut();
        writer.writeInt(123);
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.LIST, reader.next());
        reader.stepIn();
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertTrue(reader.isInStruct());
        reader.stepOut();
        assertFalse(reader.isInStruct());
        assertEquals(IonType.LIST, reader.next());
        reader.stepIn();
        assertFalse(reader.isInStruct());
        assertNull(reader.next());
        assertNull(reader.next());
        reader.stepOut();
        assertEquals(IonType.SEXP, reader.next());
        reader.stepIn();
        assertNull(reader.next());
        reader.stepOut();
        reader.stepOut();
        assertEquals(IonType.INT, reader.next());
        assertEquals(123, reader.intValue());
        assertNull(reader.next());
        reader.close();
    }

    private static void drainAnnotations(IonReader reader, List<String> annotationsSink) {
        Iterator<String> iterator = reader.iterateTypeAnnotations();
        while (iterator.hasNext()) {
            annotationsSink.add(iterator.next());
        }
    }

    @Test
    public void annotatedTopLevelIterator() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.setTypeAnnotations("foo", "bar");
        writer.writeInt(123);
        writer.setTypeAnnotations("baz");
        writer.writeInt(456);
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.INT, reader.next());
        List<String> annotations = new ArrayList<String>();
        drainAnnotations(reader, annotations);
        assertEquals(Arrays.asList("foo", "bar"), annotations);
        annotations.clear();
        assertEquals(123, reader.intValue());
        assertEquals(IonType.INT, reader.next());
        drainAnnotations(reader, annotations);
        assertEquals(Collections.singletonList("baz"), annotations);
        assertEquals(456, reader.intValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void annotatedTopLevelAsStrings() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.setTypeAnnotations("foo", "bar");
        writer.writeInt(123);
        writer.setTypeAnnotations("baz");
        writer.writeInt(456);
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.INT, reader.next());
        assertEquals(Arrays.asList("foo", "bar"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals(123, reader.intValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(Collections.singletonList("baz"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals(456, reader.intValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void annotatedInContainer() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.stepIn(IonType.LIST);
        writer.setTypeAnnotations("foo", "bar");
        writer.writeInt(123);
        writer.setTypeAnnotations("baz");
        writer.writeInt(456);
        writer.stepOut();
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.LIST, reader.next());
        reader.stepIn();
        assertEquals(IonType.INT, reader.next());
        assertEquals(Arrays.asList("foo", "bar"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals(123, reader.intValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(Collections.singletonList("baz"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals(456, reader.intValue());
        assertNull(reader.next());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void annotatedInContainerIterator() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.stepIn(IonType.LIST);
        writer.setTypeAnnotations("foo", "bar");
        writer.writeInt(123);
        writer.setTypeAnnotations("baz");
        writer.writeInt(456);
        writer.stepOut();
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.LIST, reader.next());
        reader.stepIn();
        assertEquals(IonType.INT, reader.next());
        List<String> annotations = new ArrayList<String>();
        drainAnnotations(reader, annotations);
        assertEquals(Arrays.asList("foo", "bar"), annotations);
        annotations.clear();
        assertEquals(123, reader.intValue());
        assertEquals(IonType.INT, reader.next());
        drainAnnotations(reader, annotations);
        assertEquals(Collections.singletonList("baz"), annotations);
        assertEquals(456, reader.intValue());
        assertNull(reader.next());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void nestedContainers() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.stepIn(IonType.STRUCT);
        writer.setTypeAnnotations("foo", "bar");
        writer.setFieldName("abc");
        writer.stepIn(IonType.SEXP);
        writer.writeInt(123);
        writer.stepOut();
        writer.setTypeAnnotations("baz");
        writer.setFieldName("def");
        writer.stepIn(IonType.LIST);
        writer.writeInt(456);
        writer.stepIn(IonType.STRUCT);
        writer.stepOut();
        writer.stepOut();
        writer.stepOut();
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertNull(reader.getType());
        assertEquals(0, reader.getDepth());
        assertEquals(IonType.STRUCT, reader.next());
        assertEquals(IonType.STRUCT, reader.getType());
        reader.stepIn();
        assertTrue(reader.isInStruct());
        assertNull(reader.getType());
        assertEquals(1, reader.getDepth());
        assertEquals(IonType.SEXP, reader.next());
        assertEquals(IonType.SEXP, reader.getType());
        assertEquals(Arrays.asList("foo", "bar"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals(12, reader.getFieldId());
        reader.stepIn();
        assertFalse(reader.isInStruct());
        assertEquals(2, reader.getDepth());
        assertNull(reader.getType());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IonType.INT, reader.getType());
        assertEquals(123, reader.intValue());
        assertNull(reader.next());
        assertEquals(2, reader.getDepth());
        assertNull(reader.getType());
        reader.stepOut();
        assertEquals(1, reader.getDepth());
        assertNull(reader.getType());
        assertEquals(IonType.LIST, reader.next());
        assertEquals(IonType.LIST, reader.getType());
        assertEquals(14, reader.getFieldId());
        assertEquals(Collections.singletonList("baz"), Arrays.asList(reader.getTypeAnnotations()));
        reader.stepIn();
        assertEquals(2, reader.getDepth());
        assertNull(reader.getType());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IonType.INT, reader.getType());
        assertEquals(456, reader.intValue());
        assertEquals(IonType.STRUCT, reader.next());
        assertEquals(IonType.STRUCT, reader.getType());
        assertEquals(2, reader.getDepth());
        assertNull(reader.next());
        assertEquals(2, reader.getDepth());
        assertNull(reader.getType());
        reader.stepOut();
        assertEquals(1, reader.getDepth());
        assertNull(reader.getType());
        assertNull(reader.next());
        assertNull(reader.getType());
        assertEquals(1, reader.getDepth());
        reader.stepOut();
        assertEquals(0, reader.getDepth());
        assertNull(reader.getType());
        assertNull(reader.next());
        assertNull(reader.getType());
        reader.close();
    }

    @Test
    public void skipContainers() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.stepIn(IonType.LIST);
        writer.writeInt(123);
        writer.stepOut();

        writer.writeInt(456);

        writer.stepIn(IonType.STRUCT);
        writer.setTypeAnnotations("foo", "bar");
        writer.setFieldName("abc");
        writer.writeInt(123);
        writer.setTypeAnnotations("baz");
        writer.setFieldName("def");
        writer.writeInt(456);
        writer.stepOut();

        writer.stepIn(IonType.LIST);
        writer.writeInt(123);
        writer.stepOut();

        writer.writeInt(789);

        writer.stepIn(IonType.LIST);
        writer.setTypeAnnotations("foo", "bar");
        writer.writeInt(123);
        writer.setTypeAnnotations("baz");
        writer.writeInt(456);
        writer.stepOut();

        writer.stepIn(IonType.LIST);
        writer.writeInt(123);
        writer.stepOut();
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.LIST, reader.next());
        assertEquals(IonType.LIST, reader.getType());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IonType.INT, reader.getType());
        assertEquals(IonType.STRUCT, reader.next());
        assertEquals(IonType.STRUCT, reader.getType());
        assertEquals(IonType.LIST, reader.next());
        assertEquals(IonType.LIST, reader.getType());
        reader.stepIn();
        assertNull(reader.getType());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IonType.INT, reader.getType());
        assertEquals(123, reader.intValue());
        reader.stepOut();
        assertNull(reader.getType());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IonType.INT, reader.getType());
        assertEquals(789, reader.intValue());
        assertEquals(IonType.LIST, reader.next());
        assertEquals(IonType.LIST, reader.getType());
        assertEquals(IonType.LIST, reader.next());
        assertEquals(IonType.LIST, reader.getType());
        assertNull(reader.next());
        assertNull(reader.getType());
        reader.close();
    }

    @Test
    public void skipContainerAfterSteppingIn() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.stepIn(IonType.STRUCT);
        writer.setTypeAnnotations("foo", "bar");
        writer.setFieldName("abc");
        writer.writeInt(123);
        writer.setTypeAnnotations("baz");
        writer.setFieldName("def");
        writer.writeInt(456);
        writer.stepOut();
        writer.writeInt(789);
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.STRUCT, reader.next());
        assertEquals(IonType.STRUCT, reader.getType());
        reader.stepIn();
        assertNull(reader.getType());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IonType.INT, reader.getType());
        assertEquals(Arrays.asList("foo", "bar"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals(12, reader.getFieldId());
        assertEquals(123, reader.intValue());
        assertEquals(IonType.INT, reader.getType());
        // Step out before completing the value.
        reader.stepOut();
        assertNull(reader.getType());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IonType.INT, reader.getType());
        assertEquals(789, reader.intValue());
        assertNull(reader.next());
        assertNull(reader.getType());
        reader.close();
    }

    @Test
    public void skipValueInContainer() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("foo");
        writer.writeString("bar");
        writer.setFieldName("abc");
        writer.writeInt(123);
        writer.setFieldName("baz");
        writer.writeSymbol("a");
        writer.stepOut();
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.STRING, reader.next());
        assertEquals(IonType.INT, reader.next());
        assertEquals("abc", reader.getFieldName());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("baz", reader.getFieldName());
        assertNull(reader.next());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void fieldNameLength14() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("multipleValues");
        writer.stepIn(IonType.LIST);
        writer.writeString("first");
        writer.writeString("second");
        writer.stepOut();
        writer.stepOut();
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.LIST, reader.next());
        assertEquals("multipleValues", reader.getFieldName());
        reader.stepIn();
        assertEquals(IonType.STRING, reader.next());
        assertEquals("first", reader.stringValue());
        assertEquals(IonType.STRING, reader.next());
        assertEquals("second", reader.stringValue());
        assertNull(reader.next());
        reader.stepOut();
        assertNull(reader.next());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void annotatedStringLength14() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.setTypeAnnotations("value_type");
        writer.writeString("StringValueLong");
        writer.writeInt(123);
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.STRING, reader.next());
        assertEquals("StringValueLong", reader.stringValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(123, reader.intValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void annotatedContainerLength14() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.setTypeAnnotations("value_type");
        writer.stepIn(IonType.LIST);
        writer.writeString("StringValueLong");
        writer.stepOut();
        writer.writeInt(123);
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.LIST, reader.next());
        reader.stepIn();
        assertEquals(IonType.STRING, reader.next());
        assertEquals("StringValueLong", reader.stringValue());
        assertNull(reader.next());
        reader.stepOut();
        assertEquals(IonType.INT, reader.next());
        assertEquals(123, reader.intValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void symbolsAsStrings() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("foo");
        writer.addTypeAnnotation("uvw");
        writer.writeSymbol("abc");
        writer.setFieldName("bar");
        writer.setTypeAnnotations("qrs", "xyz");
        writer.writeSymbol("def");
        writer.stepOut();
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("foo", reader.getFieldName());
        assertEquals(Collections.singletonList("uvw"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals("abc", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("bar", reader.getFieldName());
        assertEquals(Arrays.asList("qrs", "xyz"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals("def", reader.stringValue());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void lstAppend() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().withLocalSymbolTableAppendEnabled().build(out);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("foo");
        writer.addTypeAnnotation("uvw");
        writer.writeSymbol("abc");
        writer.setFieldName("bar");
        writer.setTypeAnnotations("qrs", "xyz");
        writer.writeSymbol("def");
        writer.stepOut();
        writer.flush();
        writer.writeSymbol("orange");
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("foo", reader.getFieldName());
        assertEquals(Collections.singletonList("uvw"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals("abc", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("bar", reader.getFieldName());
        assertEquals(Arrays.asList("qrs", "xyz"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals("def", reader.stringValue());
        reader.stepOut();
        SymbolTable preAppend = reader.getSymbolTable();
        assertEquals(IonType.SYMBOL, reader.next());
        SymbolTable postAppend = reader.getSymbolTable();
        assertEquals("orange", reader.stringValue());
        assertNull(preAppend.find("orange"));
        assertNotNull(postAppend.find("orange"));
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void lstNonAppend() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().withLocalSymbolTableAppendDisabled().build(out);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("foo");
        writer.addTypeAnnotation("uvw");
        writer.writeSymbol("abc");
        writer.setFieldName("bar");
        writer.setTypeAnnotations("qrs", "xyz");
        writer.writeSymbol("def");
        writer.stepOut();
        writer.setTypeAnnotations("$ion_symbol_table");
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("symbols");
        writer.stepIn(IonType.LIST);
        writer.writeString("orange");
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbol("orange");
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("foo", reader.getFieldName());
        assertEquals(Collections.singletonList("uvw"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals("abc", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("bar", reader.getFieldName());
        assertEquals(Arrays.asList("qrs", "xyz"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals("def", reader.stringValue());
        reader.stepOut();
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("orange", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void ivmBetweenValues() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().withLocalSymbolTableAppendDisabled().build(out);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("foo");
        writer.addTypeAnnotation("uvw");
        writer.writeSymbol("abc");
        writer.setFieldName("bar");
        writer.setTypeAnnotations("qrs", "xyz");
        writer.writeSymbol("def");
        writer.stepOut();
        writer.finish();
        writer.writeSymbol("orange");
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("foo", reader.getFieldName());
        assertEquals(Collections.singletonList("uvw"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals("abc", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("bar", reader.getFieldName());
        assertEquals(Arrays.asList("qrs", "xyz"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals("def", reader.stringValue());
        reader.stepOut();
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("orange", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void multipleSymbolTablesBetweenValues() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.setTypeAnnotations("$ion_symbol_table");
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("symbols");
        writer.stepIn(IonType.LIST);
        writer.writeString("abc");
        writer.stepOut();
        writer.stepOut();
        writer.setTypeAnnotations("$ion_symbol_table");
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("symbols");
        writer.stepIn(IonType.LIST);
        writer.writeString("def");
        writer.stepOut();
        writer.setFieldName("imports");
        writer.writeSymbol("$ion_symbol_table");
        writer.stepOut();
        writer.writeSymbol("abc");
        writer.writeSymbol("def");
        writer.setTypeAnnotations("$ion_symbol_table");
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("symbols");
        writer.stepIn(IonType.LIST);
        writer.writeString("orange");
        writer.stepOut();
        writer.stepOut();
        writer.setTypeAnnotations("$ion_symbol_table");
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("symbols");
        writer.stepIn(IonType.LIST);
        writer.writeString("purple");
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbol("purple");
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("abc", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("def", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("purple", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void multipleIvmsBetweenValues() throws Exception  {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _Private_IonRawWriter writer = IonBinaryWriterBuilder.standard().build(out)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.setTypeAnnotationSymbols(3);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(7);
        writer.stepIn(IonType.LIST);
        writer.writeString("abc");
        writer.stepOut();
        writer.stepOut();
        writer.finish();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.setTypeAnnotationSymbols(3);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(7);
        writer.stepIn(IonType.LIST);
        writer.writeString("def");
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbolToken(10);
        writer.finish();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.writeSymbolToken(4);
        writer.close();
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("def", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("name", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void invalidVersion() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes(0xE0, 0x01, 0x74, 0xEA, 0x20));
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        thrown.expect(IonException.class);
        reader.next();
    }

    @Test
    public void invalidVersionMarker() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes(0xE0, 0x01, 0x00, 0xEB, 0x20));
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        thrown.expect(IonException.class);
        reader.next();
    }

    @Test
    public void incrementalRead() throws Exception {
        ResizingPipedInputStream pipe = new ResizingPipedInputStream(128);
        ByteArrayOutputStream firstValue = new ByteArrayOutputStream();
        _Private_IonRawWriter firstValueWriter = IonBinaryWriterBuilder.standard().build(firstValue)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        firstValue.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        firstValueWriter.setTypeAnnotationSymbols(3);
        firstValueWriter.stepIn(IonType.STRUCT);
        firstValueWriter.setFieldNameSymbol(7);
        firstValueWriter.stepIn(IonType.LIST);
        firstValueWriter.writeString("abc");
        firstValueWriter.writeString("def");
        firstValueWriter.stepOut();
        firstValueWriter.stepOut();
        firstValueWriter.stepIn(IonType.STRUCT);
        firstValueWriter.setTypeAnnotationSymbols(11);
        firstValueWriter.setFieldNameSymbol(10);
        firstValueWriter.writeString("foo");
        firstValueWriter.stepOut();
        firstValueWriter.close();
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(pipe);
        assertNull(reader.getType());
        assertNull(reader.next());
        assertNull(reader.getType());
        byte[] firstValueBytes = firstValue.toByteArray();
        pipe.receive(firstValueBytes, 0, firstValueBytes.length / 2);
        assertNull(reader.next());
        assertNull(reader.getType());
        pipe.receive(
            firstValueBytes,
            firstValueBytes.length / 2,
            firstValueBytes.length - firstValueBytes.length / 2
        );
        assertEquals(IonType.STRUCT, reader.next());
        assertEquals(IonType.STRUCT, reader.getType());
        reader.stepIn();
        assertNull(reader.getType());
        assertEquals(IonType.STRING, reader.next());
        assertEquals("abc", reader.getFieldName());
        assertEquals(Collections.singletonList("def"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals("foo", reader.stringValue());
        assertEquals(IonType.STRING, reader.getType());
        assertNull(reader.next());
        assertNull(reader.getType());
        reader.stepOut();
        assertNull(reader.getType());
        assertNull(reader.next());
        assertNull(reader.getType());
        pipe.receive(0x71);
        assertNull(reader.next());
        assertNull(reader.getType());
        pipe.receive(0x0B);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals(IonType.SYMBOL, reader.getType());
        assertEquals("def", reader.stringValue());
        assertNull(reader.next());
        assertNull(reader.getType());
        reader.close();
    }

    @Test
    public void incrementalValue() throws Exception {
        ResizingPipedInputStream pipe = new ResizingPipedInputStream(128);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeString("StringValueLong");
        writer.close();
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(pipe);
        byte[] bytes = out.toByteArray();
        for (byte b : bytes) {
            assertNull(reader.next());
            pipe.receive(b);
        }
        assertEquals(IonType.STRING, reader.next());
        assertEquals("StringValueLong", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void incrementalMultipleValues() throws Exception {
        ResizingPipedInputStream pipe = new ResizingPipedInputStream(128);
        ByteArrayOutputStream firstValue = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(firstValue);
        writer.setTypeAnnotations("value_type");
        writer.writeString("StringValueLong");
        writer.close();
        ByteArrayOutputStream secondValue = new ByteArrayOutputStream();
        writer = IonBinaryWriterBuilder.standard().build(secondValue);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("foobar");
        writer.writeString("StringValueLong");
        writer.stepOut();
        writer.close();
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(pipe);
        byte[] bytes = firstValue.toByteArray();
        for (byte b : bytes) {
            assertNull(reader.next());
            pipe.receive(b);
        }
        assertEquals(IonType.STRING, reader.next());
        assertEquals("StringValueLong", reader.stringValue());
        assertEquals(Collections.singletonList("value_type"), Arrays.asList(reader.getTypeAnnotations()));
        bytes = secondValue.toByteArray();
        for (byte b : bytes) {
            assertNull(reader.next());
            pipe.receive(b);
        }
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.STRING, reader.next());
        assertEquals("foobar", reader.getFieldName());
        assertEquals("StringValueLong", reader.stringValue());
        assertNull(reader.next());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void incrementalSymbolTables() throws Exception {
        ResizingPipedInputStream pipe = new ResizingPipedInputStream(128);
        ByteArrayOutputStream firstValue = new ByteArrayOutputStream();
        _Private_IonRawWriter writer = IonBinaryWriterBuilder.standard().build(firstValue)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        firstValue.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.setTypeAnnotationSymbols(3);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(7);
        writer.stepIn(IonType.LIST);
        writer.writeString("abcdefghijklmnopqrstuvwxyz");
        writer.writeString("def");
        writer.stepOut();
        writer.stepOut();
        writer.stepIn(IonType.STRUCT);
        writer.setTypeAnnotationSymbols(11);
        writer.setFieldNameSymbol(10);
        writer.writeString("foo");
        writer.stepOut();
        writer.close();

        ByteArrayOutputStream secondValue = new ByteArrayOutputStream();
        writer = IonBinaryWriterBuilder.standard().build(secondValue)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        writer.setTypeAnnotationSymbols(3);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(6);
        writer.writeSymbolToken(3);
        writer.setFieldNameSymbol(7);
        writer.stepIn(IonType.LIST);
        writer.writeString("foo");
        writer.writeString("bar");
        writer.stepOut();
        writer.stepOut();
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(10);
        writer.setTypeAnnotationSymbols(12, 13);
        writer.writeString("fairlyLongString");
        writer.stepOut();
        writer.close();

        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(pipe);
        byte[] bytes = firstValue.toByteArray();
        for (byte b : bytes) {
            assertNull(reader.next());
            pipe.receive(b);
        }
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.STRING, reader.next());
        assertEquals(Collections.singletonList("def"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals("abcdefghijklmnopqrstuvwxyz", reader.getFieldName());
        assertEquals("foo", reader.stringValue());
        assertNull(reader.next());
        reader.stepOut();
        bytes = secondValue.toByteArray();
        for (byte b : bytes) {
            assertNull(reader.next());
            pipe.receive(b);
        }
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.STRING, reader.next());
        assertEquals("fairlyLongString", reader.stringValue());
        assertEquals("abcdefghijklmnopqrstuvwxyz", reader.getFieldName());
        assertEquals(Arrays.asList("foo", "bar"), Arrays.asList(reader.getTypeAnnotations()));
        assertNull(reader.next());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void floats() throws Exception {
        double acceptableDelta = 1e-9;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeFloat(0.);
        writer.writeFloat(Double.MAX_VALUE);
        writer.writeFloat(Double.MIN_VALUE);
        writer.writeFloat(Float.MAX_VALUE);
        writer.writeFloat(Float.MIN_VALUE);
        writer.writeFloat(1.23e4);
        writer.writeFloat(1.23e-4);
        writer.writeFloat(-1.23e4);
        writer.writeFloat(-1.23e-4);
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertNull(reader.getType());
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(0., reader.doubleValue(), acceptableDelta);
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(Double.MAX_VALUE, reader.doubleValue(), acceptableDelta);
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(Double.MIN_VALUE, reader.doubleValue(), acceptableDelta);
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(Float.MAX_VALUE, reader.doubleValue(), acceptableDelta);
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(IonType.FLOAT, reader.getType());
        assertEquals(Float.MIN_VALUE, reader.doubleValue(), acceptableDelta);
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(1.23e4, reader.doubleValue(), acceptableDelta);
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(1.23e-4, reader.doubleValue(), acceptableDelta);
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(-1.23e4, reader.doubleValue(), acceptableDelta);
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(-1.23e-4, reader.doubleValue(), acceptableDelta);
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void timestamps() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        Timestamp timestampA = Timestamp.valueOf("2000T");
        Timestamp timestampB = Timestamp.valueOf("2000-01T");
        Timestamp timestampC = Timestamp.valueOf("2000-01-02T");
        Timestamp timestampD = Timestamp.valueOf("2000-01-02T03:04Z");
        Timestamp timestampE = Timestamp.valueOf("2000-01-02T03:04:05Z");
        Timestamp timestampF = Timestamp.valueOf("2000-01-02T03:04:05.6Z");
        Timestamp timestampG = Timestamp.valueOf("2000-01-02T03:04:05.06Z");
        Timestamp timestampH = Timestamp.valueOf("2000-01-02T03:04:05.006Z");
        Timestamp timestampI = Timestamp.valueOf("2000-01-02T03:04:05.600Z");
        Timestamp timestampJ = Timestamp.valueOf("2000-01-02T03:04:05.060Z");
        Timestamp timestampK = Timestamp.valueOf("2000-01-02T03:04:05.060-07:00");
        Timestamp timestampL = Timestamp.valueOf("2000-01-02T03:04:05.060+07:00");
        Timestamp timestampM = Timestamp.valueOf("2000-01-02T03:04:05+07:00");
        Timestamp timestampN = Timestamp.valueOf("2000-01-02T03:04+07:00");
        Timestamp timestampO = Timestamp.valueOf("2000-01-02T03:04:05.9999999Z");

        writer.writeTimestamp(timestampA);
        writer.writeTimestamp(timestampB);
        writer.writeTimestamp(timestampC);
        writer.writeTimestamp(timestampD);
        writer.writeTimestamp(timestampE);
        writer.writeTimestamp(timestampF);
        writer.writeTimestamp(timestampG);
        writer.writeTimestamp(timestampH);
        writer.writeTimestamp(timestampI);
        writer.writeTimestamp(timestampJ);
        writer.writeTimestamp(timestampK);
        writer.writeTimestamp(timestampL);
        writer.writeTimestamp(timestampM);
        writer.writeTimestamp(timestampN);
        writer.writeTimestamp(timestampO);
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampA, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampB, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampC, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampD, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampE, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampF, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampG, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampH, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampI, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampJ, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampK, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.getType());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampL, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampM, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampN, reader.timestampValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertEquals(timestampO, reader.timestampValue());
        assertNull(reader.next());
        assertNull(reader.getType());
        reader.close();
    }

    @Test
    public void nullValues() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeNull();
        writer.writeNull(IonType.BOOL);
        writer.writeNull(IonType.INT);
        writer.writeNull(IonType.FLOAT);
        writer.writeNull(IonType.DECIMAL);
        writer.writeNull(IonType.TIMESTAMP);
        writer.writeNull(IonType.SYMBOL);
        writer.writeNull(IonType.STRING);
        writer.writeNull(IonType.BLOB);
        writer.writeNull(IonType.CLOB);
        writer.writeNull(IonType.LIST);
        writer.writeNull(IonType.SEXP);
        writer.setTypeAnnotations("foo");
        writer.writeNull(IonType.STRUCT);
        writer.stepIn(IonType.LIST);
        writer.writeNull();
        writer.writeNull(IonType.BOOL);
        writer.writeNull(IonType.INT);
        writer.stepIn(IonType.STRUCT);
        writer.setTypeAnnotations("bar", "baz");
        writer.setFieldName("zar");
        writer.writeNull(IonType.FLOAT);
        writer.setFieldName("abc");
        writer.writeNull(IonType.DECIMAL);
        writer.stepOut();
        writer.stepOut();
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.NULL, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.BOOL, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.INT, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.FLOAT, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.DECIMAL, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.TIMESTAMP, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.STRING, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.BLOB, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.CLOB, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.LIST, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.SEXP, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.STRUCT, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(Collections.singletonList("foo"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals(IonType.LIST, reader.next());
        assertFalse(reader.isNullValue());
        reader.stepIn();
        assertFalse(reader.isNullValue());
        assertEquals(IonType.NULL, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.BOOL, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.INT, reader.next());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertFalse(reader.isNullValue());
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(Arrays.asList("bar", "baz"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals("zar", reader.getFieldName());
        assertTrue(reader.isNullValue());
        assertEquals(IonType.DECIMAL, reader.next());
        assertEquals("abc", reader.getFieldName());
        assertTrue(reader.isNullValue());
        assertNull(reader.next());
        assertFalse(reader.isNullValue());
        reader.stepOut();
        assertNull(reader.next());
        assertFalse(reader.isNullValue());
        reader.stepOut();
        assertNull(reader.next());
        assertFalse(reader.isNullValue());
        reader.close();
    }

    @Test
    public void booleans() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeBool(true);
        writer.writeBool(false);
        writer.setTypeAnnotations("foo");
        writer.writeBool(true);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("bar");
        writer.writeBool(true);
        writer.setFieldName("baz");
        writer.setTypeAnnotations("zar");
        writer.writeBool(false);
        writer.stepOut();
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.BOOL, reader.next());
        assertTrue(reader.booleanValue());
        assertEquals(IonType.BOOL, reader.next());
        assertFalse(reader.booleanValue());
        assertEquals(IonType.BOOL, reader.next());
        assertEquals(Collections.singletonList("foo"), Arrays.asList(reader.getTypeAnnotations()));
        assertTrue(reader.booleanValue());
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.BOOL, reader.next());
        assertEquals("bar", reader.getFieldName());
        assertTrue(reader.booleanValue());
        assertEquals(IonType.BOOL, reader.next());
        assertEquals("baz", reader.getFieldName());
        assertEquals(Collections.singletonList("zar"), Arrays.asList(reader.getTypeAnnotations()));
        assertFalse(reader.booleanValue());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void lobsNewBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        byte[] blobBytes = "abcdef".getBytes("UTF-8");
        byte[] clobBytes = "ghijklmnopqrstuv".getBytes("UTF-8");

        writer.writeBlob(blobBytes);
        writer.writeClob(clobBytes);
        writer.setTypeAnnotations("foo");
        writer.writeBlob(blobBytes);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("bar");
        writer.writeClob(clobBytes);
        writer.stepOut();
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.BLOB, reader.next());
        assertArrayEquals(blobBytes, reader.newBytes());
        assertEquals(IonType.CLOB, reader.next());
        assertArrayEquals(clobBytes, reader.newBytes());
        assertEquals(IonType.BLOB, reader.next());
        assertEquals(Collections.singletonList("foo"), Arrays.asList(reader.getTypeAnnotations()));
        assertArrayEquals(blobBytes, reader.newBytes());
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.CLOB, reader.next());
        assertEquals("bar", reader.getFieldName());
        assertArrayEquals(clobBytes, reader.newBytes());
        assertNull(reader.next());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void lobsGetBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        byte[] blobBytes = "abcdef".getBytes("UTF-8");
        byte[] clobBytes = "ghijklmnopqrstuv".getBytes("UTF-8");
        writer.writeBlob(blobBytes);
        writer.writeClob(clobBytes);
        writer.setTypeAnnotations("foo");
        writer.writeBlob(blobBytes);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("bar");
        writer.writeClob(clobBytes);
        writer.stepOut();
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.BLOB, reader.next());
        byte[] fullBlob = new byte[blobBytes.length];
        assertEquals(fullBlob.length, reader.getBytes(fullBlob, 0, fullBlob.length));
        assertArrayEquals(blobBytes, fullBlob);
        assertEquals(IonType.CLOB, reader.next());
        byte[] partialClob = new byte[clobBytes.length];
        assertEquals(3, reader.getBytes(partialClob, 0, 3));
        assertEquals(clobBytes.length - 3, reader.getBytes(partialClob, 3, clobBytes.length - 3));
        assertArrayEquals(clobBytes, partialClob);
        Arrays.fill(fullBlob, (byte) 0);
        assertEquals(IonType.BLOB, reader.next());
        assertEquals(fullBlob.length, reader.getBytes(fullBlob, 0, 100000));
        assertEquals(Collections.singletonList("foo"), Arrays.asList(reader.getTypeAnnotations()));
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        Arrays.fill(partialClob, (byte) 0);
        assertEquals(IonType.CLOB, reader.next());
        assertEquals(5, reader.getBytes(partialClob, 0, 5));
        assertEquals(clobBytes.length - 5, reader.getBytes(partialClob, 5, 100000));
        assertArrayEquals(clobBytes, partialClob);
        assertNull(reader.next());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void nopPad() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        // One byte no-op pad.
        out.write(0x00);
        // Two byte no-op pad.
        out.write(0x01);
        out.write(0xFF);
        // Int 0.
        out.write(0x20);
        // Three byte no-op pad.
        out.write(0x02);
        out.write(0x99);
        out.write(0x42);
        // 16-byte no-op pad.
        out.write(0x0E);
        out.write(0x8E);
        out.write(new byte[14]);
        // Int 1.
        out.write(0x21);
        out.write(0x01);
        // Struct with no-op pad at the start.
        out.write(0xD9);
        // Field SID 0.
        out.write(0x80);
        // Five byte no-op pad.
        out.write(0x04);
        out.write(new byte[4]);
        // Field SID 4 ("name").
        out.write(0x84);
        // Int -1.
        out.write(0x31);
        out.write(0x01);
        // Struct (empty) with no-op pad at the end.
        out.write(0xD8);
        // Field SID 0.
        out.write(0x80);
        // Seven byte no-op pad.
        out.write(0x06);
        out.write(new byte[6]);
        // List (empty) with long no-op pad.
        out.write(0xBE);
        // Length 16.
        out.write(0x90);
        // 16-byte no-op pad.
        out.write(0x0E);
        out.write(0x8E);
        out.write(new byte[14]);

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.INT, reader.next());
        assertEquals(0, reader.intValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.INT, reader.next());
        assertEquals(-1, reader.intValue());
        assertNull(reader.next());
        reader.stepOut();
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertNull(reader.next());
        reader.stepOut();
        assertEquals(IonType.LIST, reader.next());
        reader.stepIn();
        assertNull(reader.next());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void decimals() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeDecimal(BigDecimal.ZERO);
        writer.writeDecimal(Decimal.NEGATIVE_ZERO);
        writer.writeDecimal(Decimal.valueOf("1.23e4"));
        writer.writeDecimal(Decimal.valueOf("1.23e-4"));
        writer.writeDecimal(Decimal.valueOf("-1.23e4"));
        writer.writeDecimal(Decimal.valueOf("-1.23e-4"));
        writer.writeDecimal(Decimal.valueOf(Long.MAX_VALUE).add(BigDecimal.ONE));
        writer.writeDecimal(Decimal.valueOf(Long.MIN_VALUE).subtract(BigDecimal.ONE));
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("foo");
        writer.setTypeAnnotations("bar");
        writer.writeDecimal(Decimal.valueOf(Long.MAX_VALUE).add(BigDecimal.ONE).scaleByPowerOfTen(-7));
        writer.stepOut();
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.DECIMAL, reader.next());
        assertEquals(BigDecimal.ZERO, reader.decimalValue());
        assertEquals(IonType.DECIMAL, reader.next());
        assertTrue(reader.decimalValue().isNegativeZero());
        assertEquals(IonType.DECIMAL, reader.next());
        assertEquals(new BigDecimal("1.23e4"), reader.bigDecimalValue());
        assertEquals(IonType.DECIMAL, reader.next());
        assertEquals(Decimal.valueOf("1.23e-4"), reader.decimalValue());
        assertEquals(IonType.DECIMAL, reader.next());
        assertEquals(new BigDecimal("-1.23e4"), reader.bigDecimalValue());
        assertEquals(IonType.DECIMAL, reader.next());
        assertEquals(Decimal.valueOf("-1.23e-4"), reader.decimalValue());
        assertEquals(IonType.DECIMAL, reader.next());
        assertEquals(BigDecimal.valueOf(Long.MAX_VALUE).add(BigDecimal.ONE), reader.bigDecimalValue());
        assertEquals(IonType.DECIMAL, reader.next());
        assertEquals(Decimal.valueOf(Long.MIN_VALUE).subtract(BigDecimal.ONE), reader.decimalValue());
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.DECIMAL, reader.next());
        assertEquals(
            BigDecimal.valueOf(Long.MAX_VALUE).add(BigDecimal.ONE).scaleByPowerOfTen(-7),
            reader.bigDecimalValue()
        );
        assertNull(reader.next());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void bigInts() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeInt(0);
        writer.writeInt(-1);
        writer.writeInt(1);
        writer.writeInt((long) Integer.MAX_VALUE + 1);
        writer.writeInt((long) Integer.MIN_VALUE - 1);
        writer.writeInt(Long.MIN_VALUE + 1); // Just shy of the boundary.
        writer.writeInt(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        writer.writeInt(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE));
        writer.close();
        out.write(bytes(0x38, 0x81, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00));
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.INT, reader.next());
        assertEquals(IntegerSize.INT, reader.getIntegerSize());
        assertEquals(BigInteger.ZERO, reader.bigIntegerValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IntegerSize.INT, reader.getIntegerSize());
        assertEquals(BigInteger.ONE.negate(), reader.bigIntegerValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IntegerSize.INT, reader.getIntegerSize());
        assertEquals(BigInteger.ONE, reader.bigIntegerValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IntegerSize.LONG, reader.getIntegerSize());
        assertEquals(BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE), reader.bigIntegerValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IntegerSize.LONG, reader.getIntegerSize());
        assertEquals(BigInteger.valueOf(Integer.MIN_VALUE).subtract(BigInteger.ONE), reader.bigIntegerValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IntegerSize.LONG, reader.getIntegerSize());
        assertEquals(Long.MIN_VALUE + 1, reader.longValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IntegerSize.BIG_INTEGER, reader.getIntegerSize());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), reader.bigIntegerValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IntegerSize.BIG_INTEGER, reader.getIntegerSize());
        assertEquals(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE), reader.bigIntegerValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(IntegerSize.BIG_INTEGER, reader.getIntegerSize());
        assertEquals(
            new BigInteger(-1, bytes(0x81, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
            reader.bigIntegerValue()
        );
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void symbolTableWithImportsThenSymbols() throws Exception {
        IonSystem system = IonSystemBuilder.standard().build();
        SimpleCatalog catalog = new SimpleCatalog();
        catalog.putTable(system.newSharedSymbolTable("foo", 1, Arrays.asList("abc", "def").iterator()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().withCatalog(catalog).build(out);
        writer.setTypeAnnotations("$ion_symbol_table");
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("imports");
        writer.stepIn(IonType.LIST);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("name");
        writer.writeString("foo");
        writer.setFieldName("version");
        writer.writeInt(1);
        writer.setFieldName("max_id");
        writer.writeInt(2);
        writer.stepOut();
        writer.stepOut();
        writer.setFieldName("symbols");
        writer.stepIn(IonType.LIST);
        writer.writeString("ghi");
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbol("abc");
        writer.writeSymbol("def");
        writer.writeSymbol("ghi");
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input, catalog, null);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("abc", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("def", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("ghi", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void symbolTableWithSymbolsThenImports() throws Exception {
        IonSystem system = IonSystemBuilder.standard().build();
        SimpleCatalog catalog = new SimpleCatalog();
        catalog.putTable(system.newSharedSymbolTable("foo", 1, Arrays.asList("abc", "def").iterator()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _Private_IonRawWriter writer = IonBinaryWriterBuilder.standard().build(out)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        SymbolTable systemTable = SharedSymbolTable.getSystemSymbolTable(1);
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.addTypeAnnotationSymbol(systemTable.findSymbol("$ion_symbol_table"));
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("symbols"));
        writer.stepIn(IonType.LIST);
        writer.writeString("ghi");
        writer.stepOut();
        writer.setFieldNameSymbol(systemTable.findSymbol("imports"));
        writer.stepIn(IonType.LIST);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("name"));
        writer.writeString("foo");
        writer.setFieldNameSymbol(systemTable.findSymbol("version"));
        writer.writeInt(1);
        writer.setFieldNameSymbol(systemTable.findSymbol("max_id"));
        writer.writeInt(2);
        writer.stepOut();
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbolToken(10);
        writer.writeSymbolToken(11);
        writer.writeSymbolToken(12);
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input, catalog, null);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("abc", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("def", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("ghi", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void symbolTableWithManySymbolsThenImports() throws Exception {
        IonSystem system = IonSystemBuilder.standard().build();
        SimpleCatalog catalog = new SimpleCatalog();
        catalog.putTable(system.newSharedSymbolTable("foo", 1, Arrays.asList("abc", "def").iterator()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _Private_IonRawWriter writer = IonBinaryWriterBuilder.standard().build(out)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        SymbolTable systemTable = SharedSymbolTable.getSystemSymbolTable(1);
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.addTypeAnnotationSymbol(systemTable.findSymbol("$ion_symbol_table"));
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("symbols"));
        writer.stepIn(IonType.LIST);
        writer.writeString("ghi");
        writer.writeString("jkl");
        writer.writeString("mno");
        writer.writeString("pqr");
        writer.stepOut();
        writer.setFieldNameSymbol(systemTable.findSymbol("imports"));
        writer.stepIn(IonType.LIST);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("name"));
        writer.writeString("foo");
        writer.setFieldNameSymbol(systemTable.findSymbol("version"));
        writer.writeInt(1);
        writer.setFieldNameSymbol(systemTable.findSymbol("max_id"));
        writer.writeInt(2);
        writer.stepOut();
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbolToken(10);
        writer.writeSymbolToken(11);
        writer.writeSymbolToken(12);
        writer.writeSymbolToken(13);
        writer.writeSymbolToken(14);
        writer.writeSymbolToken(15);
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input, catalog, null);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("abc", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("def", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("ghi", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("jkl", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("mno", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("pqr", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void multipleSymbolTablesWithSymbolsThenImports() throws Exception {
        IonSystem system = IonSystemBuilder.standard().build();
        SimpleCatalog catalog = new SimpleCatalog();
        catalog.putTable(system.newSharedSymbolTable("foo", 1, Arrays.asList("abc", "def").iterator()));
        catalog.putTable(system.newSharedSymbolTable("bar", 1, Collections.singletonList("baz").iterator()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _Private_IonRawWriter writer = IonBinaryWriterBuilder.standard().build(out)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        SymbolTable systemTable = SharedSymbolTable.getSystemSymbolTable(1);
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.addTypeAnnotationSymbol(systemTable.findSymbol("$ion_symbol_table"));
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("symbols"));
        writer.stepIn(IonType.LIST);
        writer.writeString("ghi");
        writer.stepOut();
        writer.setFieldNameSymbol(systemTable.findSymbol("imports"));
        writer.stepIn(IonType.LIST);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("name"));
        writer.writeString("foo");
        writer.setFieldNameSymbol(systemTable.findSymbol("version"));
        writer.writeInt(1);
        writer.setFieldNameSymbol(systemTable.findSymbol("max_id"));
        writer.writeInt(2);
        writer.stepOut();
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbolToken(10);
        writer.writeSymbolToken(11);
        writer.writeSymbolToken(12);
        writer.addTypeAnnotationSymbol(systemTable.findSymbol("$ion_symbol_table"));
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("symbols"));
        writer.stepIn(IonType.LIST);
        writer.writeString("xyz");
        writer.writeString("uvw");
        writer.writeString("rst");
        writer.stepOut();
        writer.setFieldNameSymbol(systemTable.findSymbol("imports"));
        writer.stepIn(IonType.LIST);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("name"));
        writer.writeString("bar");
        writer.setFieldNameSymbol(systemTable.findSymbol("version"));
        writer.writeInt(1);
        writer.setFieldNameSymbol(systemTable.findSymbol("max_id"));
        writer.writeInt(1);
        writer.stepOut();
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbolToken(10);
        writer.writeSymbolToken(11);
        writer.writeSymbolToken(12);
        writer.writeSymbolToken(13);
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input, catalog, null);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("abc", reader.stringValue());
        SymbolTable[] imports = reader.getSymbolTable().getImportedTables();
        assertEquals(1, imports.length);
        assertEquals("foo", imports[0].getName());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("def", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("ghi", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("baz", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        imports = reader.getSymbolTable().getImportedTables();
        assertEquals(1, imports.length);
        assertEquals("bar", imports[0].getName());
        assertEquals("xyz", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("uvw", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("rst", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void ivmResetsImports() throws Exception {
        IonSystem system = IonSystemBuilder.standard().build();
        SimpleCatalog catalog = new SimpleCatalog();
        catalog.putTable(system.newSharedSymbolTable("foo", 1, Arrays.asList("abc", "def").iterator()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _Private_IonRawWriter writer = IonBinaryWriterBuilder.standard().build(out)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        SymbolTable systemTable = SharedSymbolTable.getSystemSymbolTable(1);
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.addTypeAnnotationSymbol(systemTable.findSymbol("$ion_symbol_table"));
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("symbols"));
        writer.stepIn(IonType.LIST);
        writer.writeString("ghi");
        writer.stepOut();
        writer.setFieldNameSymbol(systemTable.findSymbol("imports"));
        writer.stepIn(IonType.LIST);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("name"));
        writer.writeString("foo");
        writer.setFieldNameSymbol(systemTable.findSymbol("version"));
        writer.writeInt(1);
        writer.setFieldNameSymbol(systemTable.findSymbol("max_id"));
        writer.writeInt(2);
        writer.stepOut();
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbolToken(10);
        writer.writeSymbolToken(11);
        writer.writeSymbolToken(12);
        writer.close();

        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0x20);
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input, catalog, null);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("abc", reader.stringValue());
        SymbolTable[] imports = reader.getSymbolTable().getImportedTables();
        assertEquals(1, imports.length);
        assertEquals("foo", imports[0].getName());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("def", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("ghi", reader.stringValue());
        assertEquals(IonType.INT, reader.next());
        assertTrue(reader.getSymbolTable().isSystemTable());
        assertEquals(0, reader.intValue());
        assertNull(reader.next());
        reader.close();
    }

    private static void assertSymbolEquals(
        String expectedText,
        int expectedSid,
        IonReaderBinaryIncremental.ImportLocation expectedImportLocation,
        SymbolToken actual
    ) {
        assertEquals(expectedText, actual.getText());
        assertEquals(expectedSid, actual.getSid());
        IonReaderBinaryIncremental.SymbolTokenImpl impl = (IonReaderBinaryIncremental.SymbolTokenImpl) actual;
        assertEquals(expectedImportLocation, impl.getImportLocation());
    }

    @Test
    public void symbolsAsTokens() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("foo");
        writer.addTypeAnnotation("uvw");
        writer.writeSymbol("abc");
        writer.setFieldName("bar");
        writer.setTypeAnnotations("qrs", "xyz");
        writer.writeSymbol("def");
        writer.stepOut();
        writer.close();

        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.SYMBOL, reader.next());
        assertSymbolEquals("foo", 10, null, reader.getFieldNameSymbol());
        SymbolToken[] annotations = reader.getTypeAnnotationSymbols();
        assertEquals(1, annotations.length);
        assertSymbolEquals("uvw", 11, null, annotations[0]);
        assertSymbolEquals("abc", 12, null, reader.symbolValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertSymbolEquals("bar", 13, null, reader.getFieldNameSymbol());
        annotations = reader.getTypeAnnotationSymbols();
        assertEquals(2, annotations.length);
        assertSymbolEquals("qrs", 14, null, annotations[0]);
        assertSymbolEquals("xyz", 15, null, annotations[1]);
        assertSymbolEquals("def", 16, null, reader.symbolValue());
        reader.stepOut();
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void nullContainers() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        try {
            writer.writeNull(IonType.STRUCT);
            writer.writeNull(IonType.LIST);
            writer.writeNull(IonType.SEXP);
        } finally {
            writer.close();
        }
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertNull(reader.next());
        reader.stepOut();
        assertEquals(IonType.LIST, reader.next());
        reader.stepIn();
        assertNull(reader.next());
        reader.stepOut();
        assertEquals(IonType.SEXP, reader.next());
        reader.stepIn();
        assertNull(reader.next());
        reader.stepOut();
        reader.close();
    }

    @Test
    public void intNegativeZeroFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0x31);
        out.write(0x00);
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        reader.next();
        thrown.expect(IonException.class);
        reader.longValue();
    }

    @Test
    public void bigIntNegativeZeroFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0x31);
        out.write(0x00);
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        reader.next();
        thrown.expect(IonException.class);
        reader.bigIntegerValue();
    }

    @Test
    public void listWithLengthTooShortFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xB1);
        out.write(0x21);
        out.write(0x01);
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.LIST, reader.next());
        reader.stepIn();
        thrown.expect(IonException.class);
        reader.next();
    }

    @Test
    public void noOpPadTooShort1() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(bytes(0x37, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01));
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertEquals(IonType.INT, reader.next());
        assertNull(reader.next());
        thrown.expect(IonException.class);
        reader.close();
    }

    @Test
    public void noOpPadTooShort2() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(bytes(
            0x0e, 0x90, 0x00, 0xde, 0xad, 0xbe, 0xef, 0xca, 0xfe, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        ));
        InputStream input = new ByteArrayInputStream(out.toByteArray());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(input);
        assertNull(reader.next());
        thrown.expect(IonException.class);
        reader.close();
    }

    @Test
    public void nopPadOneByte() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0);
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void localSidOutOfRangeStringValue() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0x71);
        out.write(0x0A); // SID 10
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(IonType.SYMBOL, reader.next());
        thrown.expect(IonException.class);
        reader.stringValue();
    }

    @Test
    public void localSidOutOfRangeSymbolValue() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0x71);
        out.write(0x0A); // SID 10
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(IonType.SYMBOL, reader.next());
        thrown.expect(IonException.class);
        reader.symbolValue();
    }

    @Test
    public void localSidOutOfRangeFieldName() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xD2);
        out.write(0x8A); // SID 10
        out.write(0x20); // int 0
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.INT, reader.next());
        thrown.expect(IonException.class);
        reader.getFieldName();
    }

    @Test
    public void localSidOutOfRangeFieldNameSymbol() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xD2);
        out.write(0x8A); // SID 10
        out.write(0x20); // int 0
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.INT, reader.next());
        thrown.expect(IonException.class);
        reader.getFieldNameSymbol();
    }

    @Test
    public void localSidOutOfRangeAnnotation() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xE3);
        out.write(0x81);
        out.write(0x8A); // SID 10
        out.write(0x20); // int 0
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(IonType.INT, reader.next());
        thrown.expect(IonException.class);
        reader.getTypeAnnotations();
    }

    @Test
    public void localSidOutOfRangeAnnotationSymbol() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xE3);
        out.write(0x81);
        out.write(0x8A); // SID 10
        out.write(0x20); // int 0
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(IonType.INT, reader.next());
        thrown.expect(IonException.class);
        reader.getTypeAnnotationSymbols();
    }

    @Test
    public void localSidOutOfRangeIterateAnnotations() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xE3);
        out.write(0x81);
        out.write(0x8A); // SID 10
        out.write(0x20); // int 0
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(IonType.INT, reader.next());
        Iterator<String> annotationIterator = reader.iterateTypeAnnotations();
        thrown.expect(IonException.class);
        annotationIterator.next();
    }

    @Test
    public void stepInOnScalarFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0x20);
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(IonType.INT, reader.next());
        thrown.expect(IonException.class);
        reader.stepIn();
    }

    @Test
    public void stepInBeforeNextFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xD2);
        out.write(0x84); // "name"
        out.write(0xD0);
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        reader.next();
        reader.stepIn();
        thrown.expect(IonException.class);
        reader.stepIn();
    }

    @Test
    public void stepOutAtDepthZeroFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0x20);
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        reader.next();
        thrown.expect(IllegalStateException.class);
        reader.stepOut();
    }

    @Test
    public void byteSizeNotOnLobFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0x20);
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        reader.next();
        thrown.expect(IonException.class);
        reader.byteSize();
    }

    @Test
    public void doubleValueOnIntFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0x20);
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        reader.next();
        thrown.expect(IllegalStateException.class);
        reader.doubleValue();
    }

    @Test
    public void floatWithInvalidLengthFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0x43);
        out.write(0x01);
        out.write(0x02);
        out.write(0x03);
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        thrown.expect(IonException.class);
        reader.next();
    }

    @Test
    public void invalidTypeIdFFailsAtTopLevel() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xF0);
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        thrown.expect(IonException.class);
        reader.next();
    }

    @Test
    public void invalidTypeIdFFailsBelowTopLevel() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xB1); // list
        out.write(0xF0);
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        reader.next();
        reader.stepIn();
        thrown.expect(IonException.class);
        reader.next();
    }

    @Test
    public void reallyLargeString() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8192; i++) {
            sb.append('a');
        }
        String string = sb.toString();
        writer.writeString(string);
        writer.close();
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(IonType.STRING, reader.next());
        assertEquals(string, reader.stringValue());
        reader.close();
    }

    @Test
    public void nopPadInAnnotationWrapperFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xB5); // list
        out.write(0xE4); // annotation wrapper
        out.write(0x81); // 1 byte of annotations
        out.write(0x84); // annotation: "name"
        out.write(0x01); // 2-byte no-op pad
        out.write(0x00);
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        reader.next();
        reader.stepIn();
        thrown.expect(IonException.class);
        reader.next();
    }

    @Test
    public void nestedAnnotationWrapperFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xB5); // list
        out.write(0xE4); // annotation wrapper
        out.write(0x81); // 1 byte of annotations
        out.write(0x84); // annotation: "name"
        out.write(0xE3); // annotation wrapper
        out.write(0x81); // 1 byte of annotations
        out.write(0x84); // annotation: "name"
        out.write(0x20); // int 0
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        reader.next();
        reader.stepIn();
        thrown.expect(IonException.class);
        reader.next();
    }

    @Test
    public void annotationWrapperLengthMismatchFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(0xB5); // list
        out.write(0xE4); // annotation wrapper
        out.write(0x81); // 1 byte of annotations
        out.write(0x84); // annotation: "name"
        out.write(0x20); // int 0
        out.write(0x20); // next value
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        reader.next();
        reader.stepIn();
        thrown.expect(IonException.class);
        reader.next();
    }

    @Test
    public void multipleSymbolTableImportsFieldsFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _Private_IonRawWriter writer = IonBinaryWriterBuilder.standard().build(out)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        SymbolTable systemTable = SharedSymbolTable.getSystemSymbolTable(1);
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.addTypeAnnotationSymbol(systemTable.findSymbol("$ion_symbol_table"));
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("imports"));
        writer.stepIn(IonType.LIST);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("name"));
        writer.writeString("bar");
        writer.setFieldNameSymbol(systemTable.findSymbol("version"));
        writer.writeInt(1);
        writer.setFieldNameSymbol(systemTable.findSymbol("max_id"));
        writer.writeInt(1);
        writer.stepOut();
        writer.stepOut();
        writer.setFieldNameSymbol(systemTable.findSymbol("symbols"));
        writer.stepIn(IonType.LIST);
        writer.writeString("ghi");
        writer.stepOut();
        writer.setFieldNameSymbol(systemTable.findSymbol("imports"));
        writer.stepIn(IonType.LIST);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("name"));
        writer.writeString("foo");
        writer.setFieldNameSymbol(systemTable.findSymbol("version"));
        writer.writeInt(1);
        writer.setFieldNameSymbol(systemTable.findSymbol("max_id"));
        writer.writeInt(2);
        writer.stepOut();
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbolToken(10);
        writer.close();

        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        thrown.expect(IonException.class);
        reader.next();
    }

    @Test
    public void multipleSymbolTableSymbolsFieldsFails() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _Private_IonRawWriter writer = IonBinaryWriterBuilder.standard().build(out)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        SymbolTable systemTable = SharedSymbolTable.getSystemSymbolTable(1);
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.addTypeAnnotationSymbol(systemTable.findSymbol("$ion_symbol_table"));
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("imports"));
        writer.stepIn(IonType.LIST);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("name"));
        writer.writeString("bar");
        writer.setFieldNameSymbol(systemTable.findSymbol("version"));
        writer.writeInt(1);
        writer.setFieldNameSymbol(systemTable.findSymbol("max_id"));
        writer.writeInt(1);
        writer.stepOut();
        writer.stepOut();
        writer.setFieldNameSymbol(systemTable.findSymbol("symbols"));
        writer.stepIn(IonType.LIST);
        writer.writeString("ghi");
        writer.stepOut();
        writer.setFieldNameSymbol(systemTable.findSymbol("symbols"));
        writer.stepIn(IonType.LIST);
        writer.writeString("abc");
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbolToken(10);
        writer.close();

        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        thrown.expect(IonException.class);
        reader.next();
    }

    @Test
    public void nonStringInSymbolsListCreatesNullSlot() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _Private_IonRawWriter writer = IonBinaryWriterBuilder.standard().build(out)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        SymbolTable systemTable = SharedSymbolTable.getSystemSymbolTable(1);
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.addTypeAnnotationSymbol(systemTable.findSymbol("$ion_symbol_table"));
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("symbols"));
        writer.stepIn(IonType.LIST);
        writer.writeString(null);
        writer.writeString("abc");
        writer.writeInt(123);
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbolToken(10);
        writer.writeSymbolToken(11);
        writer.writeSymbolToken(12);
        writer.close();

        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(IonType.SYMBOL, reader.next());
        SymbolToken symbolValue = reader.symbolValue();
        assertNull(symbolValue.getText());
        assertEquals(0, symbolValue.getSid());
        assertEquals(IonType.SYMBOL, reader.next());
        symbolValue = reader.symbolValue();
        assertEquals("abc", symbolValue.getText());
        assertEquals(IonType.SYMBOL, reader.next());
        symbolValue = reader.symbolValue();
        assertNull(symbolValue.getText());
        assertEquals(0, symbolValue.getSid());
        reader.close();
    }

    @Test
    public void symbolTableWithMultipleImportsCorrectlyAssignsImportLocations() throws Exception {
        IonSystem system = IonSystemBuilder.standard().build();
        SimpleCatalog catalog = new SimpleCatalog();
        catalog.putTable(system.newSharedSymbolTable("foo", 1, Arrays.asList("abc", "def").iterator()));
        catalog.putTable(system.newSharedSymbolTable("bar", 1, Arrays.asList("123", "456").iterator()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        _Private_IonRawWriter writer = IonBinaryWriterBuilder.standard().build(out)
            .asFacet(_Private_IonManagedWriter.class)
            .getRawWriter();
        SymbolTable systemTable = SharedSymbolTable.getSystemSymbolTable(1);
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        writer.addTypeAnnotationSymbol(systemTable.findSymbol("$ion_symbol_table"));
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("imports"));
        writer.stepIn(IonType.LIST);
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("name"));
        writer.writeString("foo");
        writer.setFieldNameSymbol(systemTable.findSymbol("version"));
        writer.writeInt(1);
        writer.setFieldNameSymbol(systemTable.findSymbol("max_id"));
        writer.writeInt(4); // The matching shared symbol table in the catalog only declares two symbols.
        writer.stepOut();
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("name"));
        writer.writeString("bar");
        writer.setFieldNameSymbol(systemTable.findSymbol("version"));
        writer.writeInt(1);
        writer.setFieldNameSymbol(systemTable.findSymbol("max_id"));
        // The matching shared symbol table in the catalog declares two symbols, but only one is used.
        writer.writeInt(1);
        writer.stepOut();
        writer.stepIn(IonType.STRUCT);
        writer.setFieldNameSymbol(systemTable.findSymbol("name"));
        writer.writeString("baz"); // There is no match in the catalog; all symbols have unknown text.
        writer.setFieldNameSymbol(systemTable.findSymbol("version"));
        writer.writeInt(1);
        writer.setFieldNameSymbol(systemTable.findSymbol("max_id"));
        writer.writeInt(2);
        writer.stepOut();
        writer.stepOut();
        writer.stepOut();
        writer.writeSymbolToken(10); // abc
        writer.writeSymbolToken(11); // def
        writer.writeSymbolToken(12); // unknown text, import SID 3 (from foo)
        writer.writeSymbolToken(13); // unknown text, import SID 4 (from foo)
        writer.writeSymbolToken(14); // 123
        writer.writeSymbolToken(15); // unknown text, import SID 1 (from baz)
        writer.writeSymbolToken(16); // unknown text, import SID 2 (from baz)
        writer.close();

        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            new ByteArrayInputStream(out.toByteArray()),
            catalog,
            null
        );
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("abc", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("def", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        IonReaderBinaryIncremental.SymbolTokenImpl symbolValue =
            (IonReaderBinaryIncremental.SymbolTokenImpl) reader.symbolValue();
        assertNull(symbolValue.getText());
        assertEquals(new IonReaderBinaryIncremental.ImportLocation("foo", 3), symbolValue.getImportLocation());
        assertEquals(IonType.SYMBOL, reader.next());
        symbolValue = (IonReaderBinaryIncremental.SymbolTokenImpl) reader.symbolValue();
        assertNull(symbolValue.getText());
        assertEquals(new IonReaderBinaryIncremental.ImportLocation("foo", 4), symbolValue.getImportLocation());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("123", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        symbolValue = (IonReaderBinaryIncremental.SymbolTokenImpl) reader.symbolValue();
        assertEquals(
            new IonReaderBinaryIncremental.SymbolTokenImpl(
                null,
                15,
                new IonReaderBinaryIncremental.ImportLocation("baz", 1)
            ),
            symbolValue
        );
        assertEquals(IonType.SYMBOL, reader.next());
        symbolValue = (IonReaderBinaryIncremental.SymbolTokenImpl) reader.symbolValue();
        assertEquals(
            new IonReaderBinaryIncremental.SymbolTokenImpl(
                null,
                16,
                new IonReaderBinaryIncremental.ImportLocation("baz", 2)
            ),
            symbolValue
        );
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void symbolTableSnapshotImplementsBasicMethods() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeSymbol("abc");
        writer.close();

        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(new ByteArrayInputStream(out.toByteArray()));
        reader.next();
        SymbolTable symbolTable = reader.getSymbolTable();
        assertNull(symbolTable.getName());
        assertEquals(0, symbolTable.getVersion());
        assertTrue(symbolTable.isLocalTable());
        assertTrue(symbolTable.isReadOnly());
        assertFalse(symbolTable.isSharedTable());
        assertFalse(symbolTable.isSystemTable());
        assertFalse(symbolTable.isSubstitute());
        symbolTable.makeReadOnly();
        assertEquals(10, symbolTable.getMaxId());
        assertEquals("abc", symbolTable.findKnownSymbol(10));
        assertNull(symbolTable.findKnownSymbol(symbolTable.getMaxId() + 1));
        thrown.expect(IllegalArgumentException.class);
        symbolTable.findKnownSymbol(-1);
    }

    @Test
    public void singleValueExceedsBufferSize() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeString("abcdefghijklmnopqrstuvwxyz");
        writer.close();

        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            new ByteArrayInputStream(out.toByteArray()),
            null,
            IonBufferConfiguration.Builder.standard().withInitialBufferSize(8).build()
        );
        assertEquals(IonType.STRING, reader.next());
        assertEquals("abcdefghijklmnopqrstuvwxyz", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void maximumBufferSizeTooSmallFails() {
        IonBufferConfiguration.Builder builder = IonBufferConfiguration.Builder.standard();
        builder
            .withMaximumBufferSize(builder.getMinimumMaximumBufferSize() - 1)
            .withInitialBufferSize(builder.getMinimumMaximumBufferSize() - 1)
            .withHandler(builder.getNoOpBufferEventHandler());
        thrown.expect(IllegalArgumentException.class);
        builder.build();
    }

    @Test
    public void maximumBufferSizeWithoutHandlerFails() {
        IonBufferConfiguration.Builder builder = IonBufferConfiguration.Builder.standard();
        builder
            .withMaximumBufferSize(9)
            .withInitialBufferSize(9);
        thrown.expect(IllegalArgumentException.class);
        builder.build();
    }

    @Test
    public void oversizeValueDetectedDuringMultiByteRead() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeString("abcdefghijklmnopqrstuvwxyz"); // Requires 32 bytes (4 IVM, 1 TID, 1 length, 26 chars)
        writer.writeString("abc");
        writer.writeString("abcdefghijklmnopqrstuvwxyz");
        writer.writeString("def");
        writer.close();

        final AtomicInteger oversizedCounter = new AtomicInteger();
        final AtomicInteger byteCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onOversizedValue() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onData(int numberOfBytes) {
                byteCounter.addAndGet(numberOfBytes);
            }
        };
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            new ByteArrayInputStream(out.toByteArray()),
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(8)
                .withMaximumBufferSize(16)
                .withHandler(handler)
                .build()
        );
        assertEquals(IonType.STRING, reader.next());
        assertEquals("abc", reader.stringValue());
        assertEquals(1, oversizedCounter.get());
        assertEquals(IonType.STRING, reader.next());
        assertEquals("def", reader.stringValue());
        assertNull(reader.next());
        reader.close();
        assertEquals(2, oversizedCounter.get());
        assertEquals(out.size(), byteCounter.get());
    }

    @Test
    public void oversizeValueDetectedDuringMultiByteReadIncremental() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeString("abcdefghijklmnopqrstuvwxyz"); // Requires 32 bytes (4 IVM, 1 TID, 1 length, 26 chars)
        writer.writeString("abc");
        writer.writeString("abcdefghijklmnopqrstuvwxyz");
        writer.writeString("def");
        writer.close();

        final AtomicInteger oversizedCounter = new AtomicInteger();
        final AtomicInteger byteCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onOversizedValue() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onData(int numberOfBytes) {
                byteCounter.addAndGet(numberOfBytes);
            }
        };
        ResizingPipedInputStream pipe = new ResizingPipedInputStream(out.size());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            pipe,
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(8)
                .withMaximumBufferSize(16)
                .withHandler(handler)
                .build()
        );
        byte[] bytes = out.toByteArray();
        int valueCounter = 0;
        for (byte b : bytes) {
            pipe.receive(b);
            IonType type = reader.next();
            if (type != null) {
                valueCounter++;
                assertTrue(valueCounter < 3);
                if (valueCounter == 1) {
                    assertEquals(IonType.STRING, type);
                    assertEquals("abc", reader.stringValue());
                    assertEquals(1, oversizedCounter.get());
                } else {
                    assertEquals(2, valueCounter);
                    assertEquals(IonType.STRING, type);
                    assertEquals("def", reader.stringValue());
                    assertEquals(2, oversizedCounter.get());
                }
            }
        }
        assertEquals(2, valueCounter);
        assertNull(reader.next());
        reader.close();
        assertEquals(2, oversizedCounter.get());
        assertEquals(out.size(), byteCounter.get());
    }

    @Test
    public void oversizeValueDetectedDuringSingleByteRead() throws Exception {
        // Unlike the previous test, where excessive size is detected when trying to skip past the value portion,
        // this test verifies that excessive size can be detected while reading a value header, which happens
        // byte-by-byte.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeString("abcdefghijklmnopqrstuvwxyz"); // Requires a 2-byte header.
        writer.close();
        final AtomicInteger oversizedCounter = new AtomicInteger();
        final AtomicInteger byteCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onOversizedValue() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onData(int numberOfBytes) {
                byteCounter.addAndGet(numberOfBytes);
            }
        };
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            new ByteArrayInputStream(out.toByteArray()),
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(5)
                .withMaximumBufferSize(5)
                .withHandler(handler)
                .build()
        );
        // The maximum buffer size is 5, which will be exceeded after the IVM (4 bytes), the type ID (1 byte), and
        // the length byte (1 byte).
        assertNull(reader.next());
        reader.close();
        assertEquals(1, oversizedCounter.get());
        assertEquals(out.size(), byteCounter.get());
    }

    @Test
    public void oversizeValueDetectedDuringSingleByteReadIncremental() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeString("abcdefghijklmnopqrstuvwxyz"); // Requires a 2-byte header.
        writer.close();
        final AtomicInteger oversizedCounter = new AtomicInteger();
        final AtomicInteger byteCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onOversizedValue() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onData(int numberOfBytes) {
                byteCounter.addAndGet(numberOfBytes);
            }
        };
        ResizingPipedInputStream pipe = new ResizingPipedInputStream(out.size());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            pipe,
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(5)
                .withMaximumBufferSize(5)
                .withHandler(handler)
                .build()
        );
        byte[] bytes = out.toByteArray();
        for (byte b : bytes) {
            assertNull(reader.next());
            pipe.receive(b);
        }
        // The maximum buffer size is 5, which will be exceeded after the IVM (4 bytes), the type ID (1 byte), and
        // the length byte (1 byte).
        assertNull(reader.next());
        reader.close();
        assertEquals(1, oversizedCounter.get());
        assertEquals(out.size(), byteCounter.get());
    }

    @Test
    public void oversizeValueDetectedDuringReadOfTypeIdOfSymbolTableAnnotatedValue() throws Exception {
        // This value is not a symbol table, but follows most of the code path that symbol tables follow. Ensure that
        // `onOversizedValue` (NOT `onOversizedSymbolTable`) is called, and that the stream continues to be read.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.addTypeAnnotation("$ion_symbol_table");
        writer.writeInt(123);
        writer.writeString("a");
        writer.close();
        final AtomicInteger oversizedCounter = new AtomicInteger();
        final AtomicInteger byteCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onOversizedValue() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onData(int numberOfBytes) {
                byteCounter.addAndGet(numberOfBytes);
            }
        };
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            new ByteArrayInputStream(out.toByteArray()),
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(7)
                .withMaximumBufferSize(7)
                .withHandler(handler)
                .build()
        );
        // The maximum buffer size is 7, which will be exceeded after the IVM (4 bytes), the annotation wrapper type ID
        // (1 byte), the annotations length (1 byte), and the annotation SID 3 (1 byte). The next byte is the wrapped
        // value type ID byte.
        assertEquals(IonType.STRING, reader.next());
        assertEquals("a", reader.stringValue());
        assertEquals(1, oversizedCounter.get());
        assertNull(reader.next());
        reader.close();
        assertEquals(out.size(), byteCounter.get());
    }

    @Test
    public void oversizeValueDetectedDuringReadOfTypeIdOfSymbolTableAnnotatedValueIncremental() throws Exception {
        // This value is not a symbol table, but follows most of the code path that symbol tables follow. Ensure that
        // `onOversizedValue` (NOT `onOversizedSymbolTable`) is called, and that the stream continues to be read.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.addTypeAnnotation("$ion_symbol_table");
        writer.writeInt(123);
        writer.writeString("a");
        writer.close();
        final AtomicInteger oversizedCounter = new AtomicInteger();
        final AtomicInteger byteCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onOversizedValue() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onData(int numberOfBytes) {
                byteCounter.addAndGet(numberOfBytes);
            }
        };
        ResizingPipedInputStream pipe = new ResizingPipedInputStream(out.size());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            pipe,
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(7)
                .withMaximumBufferSize(7)
                .withHandler(handler)
                .build()
        );
        byte[] bytes = out.toByteArray();

        // The maximum buffer size is 7, which will be exceeded after the IVM (4 bytes), the annotation wrapper type ID
        // (1 byte), the annotations length (1 byte), and the annotation SID 3 (1 byte). The next byte is the wrapped
        // value type ID byte.
        boolean foundValue = false;
        for (byte b : bytes) {
            pipe.receive(b);
            IonType type = reader.next();
            if (type != null) {
                assertFalse(foundValue);
                assertEquals(IonType.STRING, type);
                assertEquals("a", reader.stringValue());
                assertEquals(1, oversizedCounter.get());
                foundValue = true;
            }
        }
        assertTrue(foundValue);
        assertEquals(1, oversizedCounter.get());
        assertNull(reader.next());
        reader.close();
        assertEquals(out.size(), byteCounter.get());
    }

    @Test
    public void oversizeValueWithSymbolTable() throws Exception {
        // The first value is oversized because it cannot be buffered at the same time as the preceding symbol table
        // without exceeding the maximum buffer size. The next value fits.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeString("12345678");
        writer.writeSymbol("abcdefghijklmnopqrstuvwxyz"); // Requires 32 bytes (4 IVM, 1 TID, 1 length, 26 chars)
        writer.close();
        // The system values require ~40 bytes (4 IVM, 5 symtab struct header, 1 'symbols' sid, 2 list header, 2 + 26
        // for symbol 10.
        // The string "12345678" requires 9 bytes, bringing the total to ~49, above the max of 48.
        final AtomicInteger oversizedCounter = new AtomicInteger();
        final AtomicInteger byteCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onOversizedValue() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onData(int numberOfBytes) {
                byteCounter.addAndGet(numberOfBytes);
            }
        };
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            new ByteArrayInputStream(out.toByteArray()),
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(8)
                .withMaximumBufferSize(48)
                .withHandler(handler)
                .build()
        );
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals(1, oversizedCounter.get());
        assertEquals("abcdefghijklmnopqrstuvwxyz", reader.stringValue());;
        assertNull(reader.next());
        reader.close();
        assertEquals(1, oversizedCounter.get());
        assertEquals(out.size(), byteCounter.get());
    }

    @Test
    public void oversizeValueWithSymbolTableIncremental() throws Exception {
        // The first value is oversized because it cannot be buffered at the same time as the preceding symbol table
        // without exceeding the maximum buffer size. The next value fits.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeString("12345678");
        writer.writeSymbol("abcdefghijklmnopqrstuvwxyz"); // Requires 32 bytes (4 IVM, 1 TID, 1 length, 26 chars)
        writer.close();
        // The system values require ~40 bytes (4 IVM, 5 symtab struct header, 1 'symbols' sid, 2 list header, 2 + 26
        // for symbol 10.
        // The string "12345678" requires 9 bytes, bringing the total to ~49, above the max of 48.
        final AtomicInteger oversizedCounter = new AtomicInteger();
        final AtomicInteger byteCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onOversizedValue() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onData(int numberOfBytes) {
                byteCounter.addAndGet(numberOfBytes);
            }
        };
        ResizingPipedInputStream pipe = new ResizingPipedInputStream(out.size());
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            pipe,
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(8)
                .withMaximumBufferSize(48)
                .withHandler(handler)
                .build()
        );
        byte[] bytes = out.toByteArray();
        boolean foundValue = false;
        for (byte b : bytes) {
            pipe.receive(b);
            IonType type = reader.next();
            if (type != null) {
                assertFalse(foundValue);
                assertEquals(IonType.SYMBOL, type);
                assertEquals("abcdefghijklmnopqrstuvwxyz", reader.stringValue());
                assertEquals(1, oversizedCounter.get());
                foundValue = true;
            }
        }
        assertTrue(foundValue);
        assertNull(reader.next());
        reader.close();
        assertEquals(1, oversizedCounter.get());
        assertEquals(out.size(), byteCounter.get());
    }

    private void oversizeSymbolTableDetectedInHeader(int maximumBufferSize) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeSymbol("abcdefghijklmnopqrstuvwxyz");
        writer.close();
        // The system values require ~40 bytes (4 IVM, 5 symtab struct header, 1 'symbols' sid, 2 list header, 2 + 26
        // for symbol 10.
        final AtomicInteger oversizedCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onOversizedValue() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onData(int numberOfBytes) {

            }
        };
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            new ByteArrayInputStream(out.toByteArray()),
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(maximumBufferSize)
                .withMaximumBufferSize(maximumBufferSize)
                .withHandler(handler)
                .build()
        );
        assertNull(reader.next());
        assertEquals(1, oversizedCounter.get());
        reader.close();
    }

    @Test
    public void oversizeSymbolTableDetectedInHeader() throws Exception {
        // The symbol table is determined to be oversized when reading the length of the annotation wrapper.
        oversizeSymbolTableDetectedInHeader(5);
        // The symbol table is determined to be oversized when reading the annotations length.
        oversizeSymbolTableDetectedInHeader(6);
        // The symbol table is determined to be oversized when reading SID 3.
        oversizeSymbolTableDetectedInHeader(7);
        // The symbol table is determined to be oversized when reading the type ID of the wrapped struct.
        oversizeSymbolTableDetectedInHeader(8);
        // The symbol table is determined to be oversized when reading the length of the wrapped struct.
        oversizeSymbolTableDetectedInHeader(9);
    }

    private void oversizeSymbolTableDetectedInHeaderIncremental(int maximumBufferSize) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeSymbol("abcdefghijklmnopqrstuvwxyz");
        writer.close();
        // The system values require ~40 bytes (4 IVM, 5 symtab struct header, 1 'symbols' sid, 2 list header, 2 + 26
        // for symbol 10.
        final AtomicInteger oversizedCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onOversizedValue() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onData(int numberOfBytes) {

            }
        };
        ResizingPipedInputStream pipe = new ResizingPipedInputStream(out.size());
        byte[] bytes = out.toByteArray();
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            pipe,
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(maximumBufferSize)
                .withMaximumBufferSize(maximumBufferSize)
                .withHandler(handler)
                .build()
        );
        for (byte b : bytes) {
            assertNull(reader.next());
            pipe.receive(b);
        }
        assertNull(reader.next());
        assertEquals(1, oversizedCounter.get());
        reader.close();
    }

    @Test
    public void oversizeSymbolTableDetectedInHeaderIncremental() throws Exception {
        // The symbol table is determined to be oversized when reading the length of the annotation wrapper.
        oversizeSymbolTableDetectedInHeaderIncremental(5);
        // The symbol table is determined to be oversized when reading the annotations length.
        oversizeSymbolTableDetectedInHeaderIncremental(6);
        // The symbol table is determined to be oversized when reading SID 3.
        oversizeSymbolTableDetectedInHeaderIncremental(7);
        // The symbol table is determined to be oversized when reading the type ID of the wrapped struct.
        oversizeSymbolTableDetectedInHeaderIncremental(8);
        // The symbol table is determined to be oversized when reading the length of the wrapped struct.
        oversizeSymbolTableDetectedInHeaderIncremental(9);
    }

    @Test
    public void oversizeSymbolTableDetectedInTheMiddle() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeString("12345678");
        writer.finish();
        writer.writeSymbol("abcdefghijklmnopqrstuvwxyz"); // Requires 32 bytes (4 IVM, 1 TID, 1 length, 26 chars)
        writer.close();
        // The system values require ~40 bytes (4 IVM, 5 symtab struct header, 1 'symbols' sid, 2 list header, 2 + 26
        // for symbol 10.
        final AtomicInteger oversizedCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onOversizedValue() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onData(int numberOfBytes) {

            }
        };
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            new ByteArrayInputStream(out.toByteArray()),
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(8)
                .withMaximumBufferSize(32)
                .withHandler(handler)
                .build()
        );
        assertEquals(IonType.STRING, reader.next());
        assertEquals("12345678", reader.stringValue());
        assertNull(reader.next());
        assertEquals(1, oversizedCounter.get());
        reader.close();
    }

    @Test
    public void oversizeSymbolTableDetectedInTheMiddleIncremental() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = IonBinaryWriterBuilder.standard().build(out);
        writer.writeString("12345678");
        writer.finish();
        writer.writeSymbol("abcdefghijklmnopqrstuvwxyz"); // Requires 32 bytes (4 IVM, 1 TID, 1 length, 26 chars)
        writer.close();
        // The system values require ~40 bytes (4 IVM, 5 symtab struct header, 1 'symbols' sid, 2 list header, 2 + 26
        // for symbol 10.
        final AtomicInteger oversizedCounter = new AtomicInteger();
        IonBufferEventHandler handler = new IonBufferEventHandler() {
            @Override
            public void onOversizedSymbolTable() {
                oversizedCounter.incrementAndGet();
            }

            @Override
            public void onOversizedValue() {
                throw new IllegalStateException("not expected");
            }

            @Override
            public void onData(int numberOfBytes) {

            }
        };
        ResizingPipedInputStream pipe = new ResizingPipedInputStream(out.size());
        byte[] bytes = out.toByteArray();
        IonReaderBinaryIncremental reader = new IonReaderBinaryIncremental(
            pipe,
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(8)
                .withMaximumBufferSize(32)
                .withHandler(handler)
                .build()
        );
        boolean foundValue = false;
        for (byte b : bytes) {
            IonType type = reader.next();
            if (type != null) {
                assertFalse(foundValue);
                assertEquals(IonType.STRING, type);
                assertEquals("12345678", reader.stringValue());
                foundValue = true;
            }
            pipe.receive(b);
        }
        assertTrue(foundValue);
        assertNull(reader.next());
        assertEquals(1, oversizedCounter.get());
        reader.close();
    }

    private static void writeFirstStruct(IonWriter writer) throws IOException {
        //{
        //    foo: bar,
        //    abc: [123, 456]
        //}
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("foo");
        writer.writeSymbol("bar");
        writer.setFieldName("abc");
        writer.stepIn(IonType.LIST);
        writer.writeInt(123);
        writer.writeInt(456);
        writer.stepOut();
        writer.stepOut();
    }

    private static void assertFirstStruct(IonReader reader) {
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("foo", reader.getFieldName());
        assertEquals("bar", reader.stringValue());
        assertEquals(IonType.LIST, reader.next());
        assertEquals("abc", reader.getFieldName());
        reader.stepIn();
        assertEquals(IonType.INT, reader.next());
        assertEquals(123, reader.intValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(456, reader.intValue());
        reader.stepOut();
        reader.stepOut();
    }

    private static void writeSecondStruct(IonWriter writer) throws IOException {
        //{
        //    foo: baz,
        //    abc: [42.0, 43e0]
        //}
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName("foo");
        writer.writeSymbol("baz");
        writer.setFieldName("abc");
        writer.stepIn(IonType.LIST);
        writer.writeDecimal(new BigDecimal("42.0"));
        writer.writeFloat(43.);
        writer.stepOut();
        writer.stepOut();
    }

    private static void assertSecondStruct(IonReader reader) {
        assertEquals(IonType.STRUCT, reader.next());
        reader.stepIn();
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("foo", reader.getFieldName());
        assertEquals("baz", reader.stringValue());
        assertEquals(IonType.LIST, reader.next());
        assertEquals("abc", reader.getFieldName());
        reader.stepIn();
        assertEquals(IonType.DECIMAL, reader.next());
        assertEquals(new BigDecimal("42.0"), reader.decimalValue());
        assertEquals(IonType.FLOAT, reader.next());
        assertEquals(43., reader.doubleValue(), 1e-9);
        reader.stepOut();
        reader.stepOut();
        assertNull(reader.next());
    }

    IonReader newBoundedIncrementalReader(byte[] bytes, int maximumBufferSize) {
        return new IonReaderBinaryIncremental(
            new ByteArrayInputStream(bytes),
            null,
            IonBufferConfiguration.Builder.standard()
                .withInitialBufferSize(maximumBufferSize)
                .withMaximumBufferSize(maximumBufferSize)
                .withHandler(new IonBufferEventHandler() {
                    @Override
                    public void onOversizedSymbolTable() {
                        Assert.fail("Oversized symbol table not expected.");
                    }

                    @Override
                    public void onOversizedValue() {
                        Assert.fail("Oversized value not expected.");
                    }

                    @Override
                    public void onData(int numberOfBytes) {
                        // Do nothing.
                    }
                })
                .build()
        );
    }

    @Test
    public void flushBetweenStructs() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = _Private_IonManagedBinaryWriterBuilder
            .create(_Private_IonManagedBinaryWriterBuilder.AllocatorMode.BASIC)
                .withLocalSymbolTableAppendEnabled()
                .newWriter(out);
        writeFirstStruct(writer);
        writer.flush();
        writeSecondStruct(writer);
        writer.close();

        IonReader reader = newBoundedIncrementalReader(out.toByteArray(), 64);
        assertFirstStruct(reader);
        assertSecondStruct(reader);
        reader.close();
    }

    @Test
    public void structsWithFloat32AndPreallocatedLength() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonWriter writer = _Private_IonManagedBinaryWriterBuilder
            .create(_Private_IonManagedBinaryWriterBuilder.AllocatorMode.BASIC)
                .withPaddedLengthPreallocation(2)
                .withFloatBinary32Enabled()
                .newWriter(out);
        writeFirstStruct(writer);
        writeSecondStruct(writer);
        writer.close();

        IonReader reader = newBoundedIncrementalReader(out.toByteArray(), 64);
        assertFirstStruct(reader);
        assertSecondStruct(reader);
    }

    @Test
    public void nopPadThatFillsBufferFollowedByValueNotOversized() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(bytes(0x03, 0x00, 0x00, 0x00)); // 4 byte NOP pad.
        out.write(0x20); // Int 0.
        // The IVM is 4 bytes and the NOP pad is 4 bytes. The first value is the 9th byte and should not be considered
        // oversize because the NOP pad can be discarded.
        IonReader reader = newBoundedIncrementalReader(out.toByteArray(), 8);
        assertEquals(IonType.INT, reader.next());
        assertEquals(0, reader.intValue());
        reader.close();
    }

    @Test
    public void nopPadFollowedByValueThatOverflowsBufferNotOversized() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        out.write(bytes(0x03, 0x00, 0x00, 0x00)); // 4 byte NOP pad.
        out.write(bytes(0x21, 0x01)); // Int 1.
        // The IVM is 4 bytes and the NOP pad is 4 bytes. The first byte of the value is the 9th byte and fits in the
        // buffer. Even though there is a 10th byte, the value should not be considered oversize because the NOP pad
        // can be discarded.
        IonReader reader = newBoundedIncrementalReader(out.toByteArray(), 9);
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());
        reader.close();
    }

    @Test
    public void symbolTableFollowedByNopPadFollowedByValueThatOverflowsBufferNotOversized() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        // Symbol table with the symbol 'hello'.
        out.write(bytes(0xEB, 0x81, 0x83, 0xD8, 0x87, 0xB6, 0x85, 'h', 'e', 'l', 'l', 'o'));
        out.write(bytes(0x00)); // 1-byte NOP pad.
        out.write(bytes(0x71, 0x0A)); // SID 10 (hello).
        // The IVM is 4 bytes, the symbol table is 12 bytes, and the symbol value is 2 bytes (total 18). The 1-byte NOP
        // pad needs to be reclaimed to make space for the value. Once that is done, the value will fit.
        IonReader reader = newBoundedIncrementalReader(out.toByteArray(), 18);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("hello", reader.stringValue());
        reader.close();
    }

    @Test
    public void multipleNopPadsFollowedByValueThatOverflowsBufferNotOversized() throws Exception {
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
        // Int 1
        out.write(bytes(0x21, 0x01));
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        // The following no-op pads exceed the maximum buffer size, but should not cause an error to be raised.
        // 16-byte no-op pad.
        out.write(0x0E);
        out.write(0x8E);
        out.write(new byte[14]);
        // Three byte no-op pad.
        out.write(0x02);
        out.write(0x99);
        out.write(0x42);
        // Int 2.
        out.write(0x21);
        out.write(0x02);
        // 16-byte no-op pad.
        out.write(0x0E);
        out.write(0x8E);
        out.write(new byte[14]);

        IonReader reader = newBoundedIncrementalReader(out.toByteArray(), 11);
        assertEquals(IonType.INT, reader.next());
        assertEquals(1, reader.intValue());
        assertEquals(IonType.INT, reader.next());
        assertEquals(2, reader.intValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void nopPadsInterspersedWithSystemValuesDoNotCauseOversizedErrors() throws Exception {
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
        IonReader reader = newBoundedIncrementalReader(out.toByteArray(), 22);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("hello", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void nopPadsInterspersedWithSystemValuesDoNotCauseOversizedErrors2() throws Exception {
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
        // 16-byte no-op pad.
        out.write(0x0E);
        out.write(0x8E);
        out.write(new byte[14]);
        // Symbol table with the symbol 'hello'.
        out.write(bytes(0xEB, 0x81, 0x83, 0xD8, 0x87, 0xB6, 0x85, 'h', 'e', 'l', 'l', 'o'));
        // Three byte no-op pad.
        out.write(0x02);
        out.write(0x99);
        out.write(0x42);
        // Symbol 10 (hello)
        out.write(bytes(0x71, 0x0A));

        // Set the maximum size at 2 IVMs (8 bytes) + the symbol table (12 bytes) + the value (2 bytes).
        IonReader reader = newBoundedIncrementalReader(out.toByteArray(), 22);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("hello", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void nopPadsInterspersedWithSystemValuesDoNotCauseOversizedErrors3() throws Exception {
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
        // 14-byte no-op pad.
        out.write(0x0E);
        out.write(0x8C);
        out.write(new byte[12]);
        // Symbol table with the symbol 'hello'.
        out.write(bytes(0xEB, 0x81, 0x83, 0xD8, 0x87, 0xB6, 0x85, 'h', 'e', 'l', 'l', 'o'));
        // Three byte no-op pad.
        out.write(0x02);
        out.write(0x99);
        out.write(0x42);
        // Symbol 10 (hello)
        out.write(bytes(0x71, 0x0A));

        // Set the maximum size at 2 IVMs (8 bytes) + the symbol table (12 bytes) + the value (2 bytes).
        IonReader reader = newBoundedIncrementalReader(out.toByteArray(), 22);
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("hello", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }

    @Test
    public void nopPadSurroundingSymbolTableThatFitsInBuffer() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(_Private_IonConstants.BINARY_VERSION_MARKER_1_0);
        // 14-byte no-op pad.
        out.write(0x0E);
        out.write(0x8C);
        out.write(new byte[12]);
        // Symbol table with the symbol 'hello'.
        out.write(bytes(0xEB, 0x81, 0x83, 0xD8, 0x87, 0xB6, 0x85, 'h', 'e', 'l', 'l', 'o'));
        // 14-byte no-op pad.
        out.write(0x0E);
        out.write(0x8C);
        out.write(new byte[12]);
        // String abcdefg
        out.write(bytes(0x87, 'a', 'b', 'c', 'd', 'e', 'f', 'g'));
        // Symbol 10 (hello)
        out.write(bytes(0x71, 0x0A));

        // Set the maximum size at IVM (4 bytes) + 14-byte NOP pad + the symbol table (12 bytes) + 2 value bytes.
        IonReader reader = newBoundedIncrementalReader(out.toByteArray(), 32);
        assertEquals(IonType.STRING, reader.next());
        assertEquals("abcdefg", reader.stringValue());
        assertEquals(IonType.SYMBOL, reader.next());
        assertEquals("hello", reader.stringValue());
        assertNull(reader.next());
        reader.close();
    }
}
