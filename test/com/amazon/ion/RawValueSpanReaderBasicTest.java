/*
 * Copyright 2007-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.ion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.amazon.ion.system.IonReaderBuilder;
import java.io.ByteArrayInputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests basic behavior of the {@link RawValueSpanProvider} reader facet, which
 * provides access to the reader's underlying byte buffer and vends OffsetSpans
 * that provide positions of the current value in that buffer.
 */
@SuppressWarnings({"deprecation", "javadoc"})
public class RawValueSpanReaderBasicTest
{

    private byte[] dummyData;

    @Before
    public void setup()
    {
        // Trivial binary Ion. IVM followed by a list with a single element (int 0).
        dummyData = new byte[]{(byte)0xE0, (byte)0x01, (byte)0x00, (byte)0xEA, (byte)0xB1, (byte)0x20};
    }

    @Test
    public void testTextReaderReturnsNullFacet()
    {
        // Only the binary reader currently provides this facet.
        IonReader reader = IonReaderBuilder.standard().build("text");
        assertNull(reader.asFacet(RawValueSpanProvider.class));
    }

    @Test
    public void incrementalBinaryReaderReturnsNullFacet()
    {
        IonReader reader = IonReaderBuilder.standard().withIncrementalReadingEnabled(true).build(dummyData);
        assertNull(reader.asFacet(RawValueSpanProvider.class));
        assertNull(reader.asFacet(SeekableReader.class));
    }

    @Test
    public void testNonByteBackedReaderNotSupported()
    {
        IonReader reader = IonReaderBuilder.standard().build(new ByteArrayInputStream(dummyData));
        assertNull(reader.asFacet(RawValueSpanProvider.class));
        assertNull(reader.asFacet(SeekableReader.class));
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testSeekToNullSpanFails()
    {
        IonReader reader = IonReaderBuilder.standard().build(dummyData);
        SeekableReader seekable = reader.asFacet(SeekableReader.class);
        thrown.expect(IllegalArgumentException.class);
        seekable.hoist(null);
    }

    @Test
    public void testSeekToIncompatibleSpanFails()
    {
        IonReader reader = IonReaderBuilder.standard().build(dummyData);
        SeekableReader seekable = reader.asFacet(SeekableReader.class);
        thrown.expect(IllegalArgumentException.class);
        seekable.hoist(new Span(){

            public <T> T asFacet(Class<T> facetType)
            {
                return null;
            }

        });
    }

    @Test
    public void testSeekToValueSpanFails()
    {
        IonReader reader = IonReaderBuilder.standard().build(dummyData);
        RawValueSpanProvider valueSpanProvider = reader.asFacet(RawValueSpanProvider.class);
        reader.next(); // position reader on list
        Span valueSpan = valueSpanProvider.valueSpan();
        reader.next(); // advance past list
        SeekableReader seekable = reader.asFacet(SeekableReader.class);
        thrown.expect(IllegalArgumentException.class);
        // seeking to a value span is not allowed because it's positioned
        // past the TID and length bytes.
        seekable.hoist(valueSpan);
    }

    @Test
    public void testGetBufferReturnsSame()
    {
        IonReader reader = IonReaderBuilder.standard().build(dummyData);
        assertSame(dummyData, reader.asFacet(RawValueSpanProvider.class).buffer());
    }

    @Test
    public void testGetSpanNotAtTopLevelValueFails()
    {
        IonReader reader = IonReaderBuilder.standard().build(dummyData);
        RawValueSpanProvider spanProvider = reader.asFacet(RawValueSpanProvider.class);
        thrown.expect(IllegalStateException.class);
        spanProvider.valueSpan();
    }

    @Test
    public void testGetSpanNotAtContainerValueFails()
    {
        IonReader reader = IonReaderBuilder.standard().build(dummyData);
        RawValueSpanProvider spanProvider = reader.asFacet(RawValueSpanProvider.class);
        reader.next();
        reader.stepIn(); // inside list
        thrown.expect(IllegalStateException.class);
        spanProvider.valueSpan();
    }

    @Test
    public void testSingleOctetValueSpan()
    {
        IonReader reader = IonReaderBuilder.standard().build(dummyData);
        SpanProvider seekableSpanProvider = reader.asFacet(SpanProvider.class);
        RawValueSpanProvider valueSpanProvider = reader.asFacet(RawValueSpanProvider.class);
        reader.next();
        reader.stepIn(); // inside list
        assertEquals(IonType.INT, reader.next());
        Span seekableSpan = seekableSpanProvider.currentSpan();
        OffsetSpan valueSpan = (OffsetSpan)valueSpanProvider.valueSpan();
        // int 0 is fully represented by the type ID octet.
        assertEquals(valueSpan.getStartOffset(), valueSpan.getFinishOffset());
        reader.stepOut(); // go logically past the value
        SeekableReader seekable = reader.asFacet(SeekableReader.class);
        seekable.hoist(seekableSpan); // seek back
        reader.next(); //required after any seek
        assertEquals(0, reader.intValue());
    }

}
