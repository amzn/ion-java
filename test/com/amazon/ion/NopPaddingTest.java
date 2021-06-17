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

import org.junit.Test;

/**
 * Testing support for NOP padding for empty Ion, see <a href="https://amzn.github.io/ion-docs/binary.html#nop-pad">spec</a> for more details
 */
public class NopPaddingTest
    extends IonTestCase
{
    @Test
    public void testEmptyWithSingleByteNopPadding()
        throws Exception
    {
        IonDatagram datagram = loadTestFile("good/nopPadOneByte.10n");

        assertEquals(0, datagram.size());
    }

    @Test
    public void testEmptyWithFixedBytesNopPadding()
        throws Exception
    {
        IonDatagram datagram = loadTestFile("good/emptyThreeByteNopPad.10n");

        assertEquals(0, datagram.size());
    }

    @Test
    public void testEmptyWithVariableBytesNopPadding()
        throws Exception
    {
        IonDatagram datagram = loadTestFile("good/nopPad16Bytes.10n");

        assertEquals(0, datagram.size());
    }

    @Test
    public void testValueFollowedByNopPadding()
        throws Exception
    {
        IonDatagram datagram = loadTestFile("good/valueFollowedByNopPad.10n");

        assertNopPadWithValue(datagram);
    }

    @Test
    public void testValuePrecededByNopPadding()
        throws Exception
    {
        IonDatagram datagram = loadTestFile("good/valuePrecededByNopPad.10n");

        assertNopPadWithValue(datagram);
    }

    @Test
    public void testValueBetweenByNopPadding()
        throws Exception
    {
        IonDatagram datagram = loadTestFile("good/valueBetweenNopPads.10n");

        assertNopPadWithValue(datagram);
    }

    @Test
    public void testNopPaddingInsideEmptyStructWithZeroSymbolId()
        throws Exception
    {
        IonDatagram datagram = loadTestFile("good/nopPadInsideEmptyStructZeroSymbolId.10n");

        assertEmptyStruct(datagram);
    }

    @Test
    public void testNopPaddingInsideEmptyStructWithNonZeroSymbolId()
        throws Exception
    {
        IonDatagram datagram = loadTestFile("good/nopPadInsideEmptyStructNonZeroSymbolId.10n");

        assertEmptyStruct(datagram);
    }

    @Test
    public void testNopPaddingInsideStructWithNopPaddingThenValueNonZeroSymbolId()
        throws Exception
    {
        IonDatagram datagram = loadTestFile("good/nopPadInsideStructWithNopPadThenValueNonZeroSymbolId.10n");

        assertSingleValueStruct(datagram);
    }

    @Test
    public void testNopPaddingInsideStructWithNopPaddingThenValueZeroSymbolId()
        throws Exception
    {
        IonDatagram datagram = loadTestFile("good/nopPadInsideStructWithNopPadThenValueZeroSymbolId.10n");

        assertSingleValueStruct(datagram);
    }

    @Test
    public void testNopPaddingInsideStructWithValueThenNopPadding()
        throws Exception
    {
        IonDatagram datagram = loadTestFile("good/nopPadInsideStructWithValueThenNopPad.10n");

        assertSingleValueStruct(datagram);
    }

    @Test
    public void testNopPaddingWithAnnotation()
        throws Exception
    {
        myExpectedException.expect(IonException.class);
        loadTestFile("bad/nopPadWithAnnotations.10n");
    }

    private void assertSingleValueStruct(final IonDatagram datagram)
    {
        assertEquals(1, datagram.size());

        final IonStruct struct = (IonStruct) datagram.get(0);
        assertEquals(1, struct.size());

        // files use true as example value
        final IonBool ionBool = (IonBool) struct.iterator().next();
        assertEquals(true, ionBool.booleanValue());
    }

    private void assertNopPadWithValue(final IonDatagram datagram)
    {
        assertEquals(1, datagram.size());

        IonValue ionValue = datagram.get(0);

        // files use null.null as the value next to the NOP pad
        assertTrue(ionValue.isNullValue());
    }

    private void assertEmptyStruct(final IonDatagram datagram)
    {
        assertEquals(1, datagram.size());

        final IonStruct struct = (IonStruct) datagram.get(0);
        assertTrue(struct.isEmpty());
    }
}
