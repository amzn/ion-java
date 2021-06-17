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

import static com.amazon.ion.impl._Private_Utils.EMPTY_STRING_ARRAY;
import static com.amazon.ion.junit.IonAssert.assertNoCurrentValue;
import static com.amazon.ion.junit.IonAssert.expectNextField;

import com.amazon.ion.junit.IonAssert;
import java.util.Date;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;



public abstract class ReaderSystemProcessingTestCase
    extends SystemProcessingTestCase
{
    protected IonReader myReader;
    private IonType   myValueType;


    @After @Override
    public void tearDown() throws Exception
    {
        super.tearDown();
        if (myReader != null) myReader.close();
    }


    protected abstract IonReader read()
        throws Exception;

    protected abstract IonReader systemRead()
        throws Exception;


    @Override
    protected void startIteration() throws Exception
    {
        myReader = read();
        myValueType = null;
    }

    @Override
    protected void startSystemIteration() throws Exception
    {
        myReader = systemRead();
        myValueType = null;
    }

    @Override
    protected void nextValue() throws Exception
    {
        myValueType = myReader.next();
    }

    @Override
    protected void stepIn() throws Exception
    {
        myReader.stepIn();
    }

    @Override
    protected void stepOut() throws Exception
    {

    }

    @Override
    protected IonType currentValueType() throws Exception
    {
        return myValueType;
    }

    @Override
    SymbolTable currentSymtab()
    {
        return myReader.getSymbolTable();
    }


    @Override
    Checker check()
    {
        return new ReaderChecker(myReader);
    }


    @Override
    protected void checkType(IonType expected)
    {
        assertSame(expected, myReader.getType());
    }

    @Override
    protected void checkInt(long expected) throws Exception
    {
        assertSame(IonType.INT, myReader.getType());
        assertEquals("int content", expected, myReader.longValue());
    }

    @Override
    protected void checkDecimal(double expected) throws Exception
    {
        assertSame(IonType.DECIMAL, myReader.getType());
        Decimal dec = myReader.decimalValue();

        assertEquals("decimal content", (long)expected, myReader.longValue());
        assertEquals("decimal content", expected, myReader.doubleValue());
        assertEquals("float value compared",
                     0, Float.compare((float)expected, dec.floatValue()));
        assertEquals("double value compared",
                     0, Double.compare(expected, dec.doubleValue()));
    }

    @Override
    protected void checkFloat(double expected) throws Exception
    {
        assertSame(IonType.FLOAT, myReader.getType());
        assertEquals("float content", expected, myReader.doubleValue());
    }

    @Override
    protected void checkString(String expected) throws Exception
    {
        assertSame(IonType.STRING, myReader.getType());
        assertEquals(expected, myReader.stringValue());
    }

    @Override
    protected void checkSymbol(String expected) throws Exception
    {
        assert expected != null;

        assertSame(IonType.SYMBOL, myReader.getType());

        assertFalse(myReader.isNullValue());

        assertEquals(expected, myReader.stringValue());

        SymbolToken sym = myReader.symbolValue();
        assertEquals(expected, sym.getText());
    }

    @Override
    protected final void checkSymbol(String expectedText, int expectedSid)
    {
        IonAssert.checkSymbol(myReader, expectedText, expectedSid);
    }


    @Override
    protected void checkTimestamp(Timestamp expected) throws Exception
    {
        assertSame(IonType.TIMESTAMP, myReader.getType());
        assertEquals("timestamp", expected, myReader.timestampValue());

        Date expectedDate = (expected == null ? null : expected.dateValue());
        assertEquals("date", expectedDate, myReader.dateValue());
    }

    @Override
    protected void checkEof()
    {
        IonAssert.assertEof(myReader);
    }

    protected void checkTopEof()
    {
        IonAssert.assertTopEof(myReader);
    }



    //=========================================================================


    @Test
    public void testNoAnnotations()
    throws Exception
    {
        startIteration("null");
        myReader.next();
        Assert.assertArrayEquals(EMPTY_STRING_ARRAY,
                                 myReader.getTypeAnnotations());
        Assert.assertArrayEquals(new SymbolToken[0], // Empty SymbolToken array
                                 myReader.getTypeAnnotationSymbols());
    }


    @Test
    public void testNextAtEnd()
        throws Exception
    {
        String text = "[]";
        startIteration(text);
        myReader.next();
        myReader.stepIn();
        checkEof();
        myReader.stepOut();
        checkTopEof();

        text = "[1]";
        startIteration(text);
        myReader.next();
        myReader.stepIn();
        myReader.next();
        checkEof();
        myReader.stepOut();
        checkTopEof();
    }


    @Test
    @SuppressWarnings("deprecation")
    public void testIsInStruct()
        throws Exception
    {
        String text = "{f:[]}";
        startIteration(text);
        assertFalse(myReader.isInStruct());
        assertEquals(0, myReader.getDepth());

        assertEquals(IonType.STRUCT, myReader.next());
        assertFalse(myReader.isInStruct());
        assertEquals(0, myReader.getDepth());

        myReader.stepIn();
        {
            assertTrue(myReader.isInStruct());
            assertEquals(1, myReader.getDepth());

            assertSame(IonType.LIST, myReader.next());
            assertTrue(myReader.isInStruct());  // still in struct until we stepIn()
            assertEquals(1, myReader.getDepth());
            myReader.stepIn();
            {
                assertFalse(myReader.isInStruct());
                assertEquals(2, myReader.getDepth());

                checkEof();
                assertFalse(myReader.isInStruct());
                assertEquals(2, myReader.getDepth());

                assertEquals(null, myReader.next());
                assertFalse(myReader.isInStruct());
                assertEquals(2, myReader.getDepth());
            }
            myReader.stepOut();

            checkEof();
            assertTrue(myReader.isInStruct());
            assertEquals(1, myReader.getDepth());

            assertEquals(null, myReader.next());
            assertTrue(myReader.isInStruct());
            assertEquals(1, myReader.getDepth());
        }
        myReader.stepOut();

        checkTopEof();
    }


    @Test
    @SuppressWarnings("deprecation")
    public void testHasNextLeavesCurrentData()
        throws Exception
    {
        String text = "hello 2";
        startIteration(text);

        assertEquals(IonType.SYMBOL, myReader.next());
        assertEquals(IonType.SYMBOL, myReader.getType());

        // FIXed ME text reader was broken, now fixed
        // really the binary readers were returning the
        // ion version marker which is a symbol and the
        // first (0th) value in a datagram.
        //if (!(myReader instanceof IonTextReaderImpl)) {
        //    assertEquals(IonType.SYMBOL, myReader.getType());
        //}
        assertEquals(IonType.INT, myReader.next());
    }

    @Test
    public void testDeepNesting()
        throws Exception
    {
        String text =
            "A::{data:B::{items:[C::{itemPromos:[D::{f4:['''12.5''']}]}]}}";
        startIteration(text);

        IonReader reader = myReader;
//        reader.hasNext();
        IonType ts = reader.next();
        assertEquals(IonType.STRUCT, ts);
        assertEquals("A", reader.getTypeAnnotations()[0]);
        reader.stepIn();
        {
//            reader.hasNext();
            reader.next();
            assertEquals("B", reader.getTypeAnnotations()[0]);
            reader.stepIn();
            {
                reader.next();
                reader.stepIn(); // list
                {
                    reader.next();
                    assertEquals("C", reader.getTypeAnnotations()[0]);
                    reader.stepIn();
                    {
                        reader.next();
                        reader.stepIn();
                        { // list
                            reader.next();
                            assertEquals("D", reader.getTypeAnnotations()[0]);
                            reader.stepIn();
                            {
                                expectNextField(reader, "f4");
                                reader.stepIn();
                                {
                                    reader.next();
                                    assertEquals("12.5", reader.stringValue());
                                    checkEof();
                                }
                                reader.stepOut();
                                checkEof();
                            }
                            reader.stepOut();
                            checkEof();
                        }
                        reader.stepOut();
                        checkEof();
                    }
                    reader.stepOut();
                    checkEof();
                }
                reader.stepOut();
                checkEof();
            }
            reader.stepOut();
            checkEof();
        }
        reader.stepOut();
        checkTopEof();
    }

    @Test
    public void testStepOutInMiddle()
    throws Exception
    {
        startIteration("{a:{b:1,c:2},d:false}");

        IonReader r = myReader;
        r.next();
        r.stepIn();
        expectNextField(r, "a");
        r.stepIn();
        expectNextField(r, "b");
        r.stepOut(); // skip c
        assertNoCurrentValue(r);
        expectNextField(r, "d");
    }

    @Test
    public void testSkippingFieldsNoQuote()
    throws Exception
    {
        testSkippingFields("");
    }

    @Test
    public void testSkippingFieldsSingleQuote()
    throws Exception
    {
        testSkippingFields("'");
    }

    @Test
    public void testSkippingFieldsDoubleQuote()
    throws Exception
    {
        testSkippingFields("\"");
    }

    @Test
    public void testSkippingFieldsTripleQuote()
    throws Exception
    {
        testSkippingFields("'''");
    }

    public void testSkippingFields(String quote)
    throws Exception
    {
        String text = "{X:{Y:{" + quote + "Z" + quote + ":{w:W}}}}";

        startIteration(text);
        IonReader r = myReader;
        r.next();
        r.stepIn();
        expectNextField(r, "X");
        checkEof();
        r.stepOut();
        checkTopEof();
    }
}
