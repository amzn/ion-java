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

package com.amazon.ion.streaming;

import static com.amazon.ion.impl.Symtabs.printLocalSymtab;
import static com.amazon.ion.junit.IonAssert.checkNullSymbol;

import com.amazon.ion.BinaryTest;
import com.amazon.ion.Decimal;
import com.amazon.ion.IonType;
import com.amazon.ion.ReaderMaker;
import com.amazon.ion.SymbolTable;
import com.amazon.ion.SymbolToken;
import com.amazon.ion.junit.Injected.Inject;
import com.amazon.ion.junit.IonAssert;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import org.junit.Ignore;
import org.junit.Test;


public class ReaderTest
    extends ReaderTestCase
{
    @Inject("readerMaker")
    public static final ReaderMaker[] READER_MAKERS = ReaderMaker.values();


    @Test
    public void testNullInt()
    {
        {
            read("null.int");
            assertEquals(IonType.INT, in.next());
            assertTrue(in.isNullValue());
            assertEquals(null, in.bigIntegerValue());
        }
        {
            for (final String hex : Arrays.asList("E0 01 00 EA 2F", "E0 01 00 EA 3F")) {
                read(BinaryTest.hexToBytes(hex));
                assertEquals(IonType.INT, in.next());
                assertTrue(in.isNullValue());
                assertEquals(null, in.bigIntegerValue());
            }
        }
    }

    @Test
    public void testStepInOnNull() throws IOException
    {
        read("null.list null.sexp null.struct");

        assertEquals(IonType.LIST, in.next());
        assertTrue(in.isNullValue());
        in.stepIn();
        expectEof();
        in.stepOut();

        assertEquals(IonType.SEXP, in.next());
        assertTrue(in.isNullValue());
        in.stepIn();
        expectEof();
        in.stepOut();

        assertEquals(IonType.STRUCT, in.next());
        assertTrue(in.isNullValue());
        in.stepIn();
        expectEof();
        in.stepOut();
        expectEof();
    }

    @Test
    public void testStepOut() throws IOException
    {
        read("{a:{b:1,c:2},d:false}");

        in.next();
        in.stepIn();
        expectNextField("a");
        in.stepIn();
        expectNextField("b");
        in.stepOut(); // skip c
        expectNoCurrentValue();
        expectNextField("d");
        expectEof();
    }


    @Test
    public void testIterateTypeAnnotationIds()
    throws Exception
    {
        String ionText =
            printLocalSymtab("ann", "ben")
            + "ann::ben::null";

        read(ionText);

        in.next();
        SymbolToken[] tokens = in.getTypeAnnotationSymbols();
        assertEquals(2, tokens.length);
        assertEquals(10, tokens[0].getSid());
        assertEquals(11, tokens[1].getSid());

        expectEof();
    }

    @Test
    public void testStringValueOnNull()
        throws Exception
    {
        read("null.string null.symbol");

        in.next();
        expectString(null);
        in.next();
        checkNullSymbol(in);
    }

    @Test
    public void testStringValueOnNonText()
        throws Exception
    {
        // All non-text types
        read("null true 1 1e2 1d2 2011-12-01T {{\"\"}} {{}} [] () {}");

        while (in.next() != null)
        {
            try {
                in.stringValue();
                fail("expected exception on " + in.getType());
            }
            catch (IllegalStateException e) { }
        }
    }

    @Test
    public void testSymbolValue()
        throws Exception
    {
        read("null.symbol sym");
        in.next();
        checkNullSymbol(in);
        in.next();
        IonAssert.checkSymbol("sym", in);
    }

    @Test
    public void testSymbolValueOnNonSymbol()
        throws Exception
    {
        // All non-symbol types
        read("null true 1 1e2 1d2 2011-12-01T \"\" {{\"\"}} {{}} [] () {}");

        while (in.next() != null)
        {
            try {
                in.symbolValue();
                fail("expected exception on " + in.getType());
            }
            catch (IllegalStateException e) { }
        }
    }

    @Test
    public void testReadingDecimalAsInteger()
    {
        // decimal value <space> int conversion
        read(
            "0. 0 " +
            "-2147483648. -2147483648 " + // Min int
            "2147483647. 2147483647 " // Max int
        );
        while (in.next() != null)
        {
            int actual = in.intValue();

            in.next();
            int expected = in.intValue();

            assertEquals(expected, actual);
        }
    }

    @Test
    public void testReadingDecimalAsLong()
    {
        // decimal value <space> int conversion
        read(
            "0. 0 " +
            "2147483647. 2147483647 " + // Max int
            "9223372036854775807. 9223372036854775807 " // Max long
        );
        while (in.next() != null)
        {
            long actual = in.longValue();

            in.next();
            long expected = in.longValue();

            assertEquals(expected, actual);
        }
    }

    @Test
    public void testReadingDecimalAsBigInteger()
    {
        //    decimal value         int conversion
        read("null.decimal          null.int                  " +
             "0.                                            0 " +
             "9223372036854775807.        9223372036854775807 " + // Max long
             "2d24                  2000000000000000000000000 "
             );

        while (in.next() != null)
        {
            BigInteger actual = in.bigIntegerValue();
            assertEquals(in.isNullValue(), actual == null);

            in.next();
            BigInteger expected = in.bigIntegerValue();

            assertEquals(expected, actual);
        }
    }

    @Test
    public void testReadingDecimalAsDouble()
    {
        // decimal value <space> float conversion
        read(
            "0. 0e0 " +
            "1.23 123e-2 " +
            "-1.23 -1.23e0 " +
            "2d24 2e24 "
        );

        while (in.next() != null)
        {
            double actual = in.doubleValue();

            in.next();
            double expected = in.doubleValue();

            assertEquals(expected, actual, 1e-9);
        }
    }

    @Test
    public void testReadingFloatAsInteger()
    {
        // float value <space> int conversion
        read(
            "-0e0 0 " +
            " 0e0 0 " +
            "-2147483648e0 -2147483648 " + // Min int
            "2147483647e0 2147483647 " // Max int
        );

        while (in.next() != null)
        {
            int actual = in.intValue();

            in.next();
            int expected = in.intValue();

            assertEquals(expected, actual);
        }
    }

    @Test
    public void testReadingFloatAsLong()
    {
        // float value <space> int conversion
        read(
            "-0e0 0 " +
            " 0e0 0 " +
            "-2147483648e0 -2147483648 " + // Min int
            "2147483647e0 2147483647 " // Max int
        );

        while (in.next() != null)
        {
            long actual = in.longValue();

            in.next();
            long expected = in.longValue();

            assertEquals(expected, actual);
        }
    }

    @Test
    public void testReadingFloatAsBigInteger()
    {
        // Note that one can't represent Long.MAX_VALUE as a double, there's
        // not enough bits in the mantissa!

        //    float value            int conversion
        read("null.float             null.int                  " +
             "-0e0                                           0 " +
             " 0e0                                           0 " +
             "9223372036854776000e0        9223372036854776000 " +
             "2e24                   2000000000000000000000000 " +
             ""
             );

        while (in.next() != null)
        {
            BigInteger actual = in.bigIntegerValue();
            assertEquals(in.isNullValue(), actual == null);

            in.next();
            BigInteger expected = in.bigIntegerValue();

            assertEquals(expected, actual);
        }
    }


    /**
     * This isn't allowed by the documentation, but it's allowed by the text
     * and binary readers.  Not allowed by the tree reader.
     *
     * We need to specify the conversion too, its non-trivial: does 0e0
     * convert to 0d0 or 0.0?  The latter is happening at this writing, but
     * it's not what one would expect.
     */
    @Test @Ignore
    public void testReadingFloatAsBigDecimal() // TODO amzn/ion-java/issues/56
    {
        // Note that one can't represent Long.MAX_VALUE as a double, there's
        // not enough bits in the mantissa!

        //    float value             decimal conversion
        read("null.float              null.decimal                " +
             "-0e0                                          -0.   " +
             " 0e0                                           0.   " +
             "9223372036854776000e0        9223372036854776000.   " +
             "9223372036854776000e12       9223372036854776000d12 " +
             "2e24                    2000000000000000000000000.  " +
             ""
             );

        while (in.next() != null)
        {
            BigDecimal actualBd  = in.bigDecimalValue();
            Decimal    actualDec = in.decimalValue();
            assertEquals(in.isNullValue(), actualBd  == null);
            assertEquals(in.isNullValue(), actualDec == null);

            in.next();
            BigDecimal expectedBd  = in.bigDecimalValue();
            Decimal    expectedDec = in.decimalValue();

            assertEquals(expectedBd,  actualBd);
            assertEquals(expectedDec, actualDec);
        }
    }


    @Test
    public void testIntValueOnNonNumber()
    {
        // All non-numeric types
        read("null true 2011-12-01T \"\" sym {{\"\"}} {{}} [] () {}");

        while (in.next() != null)
        {
            IonType type = in.getType();

            try {
                in.intValue();
                fail("expected exception from intValue on" + type);
            }
            catch (IllegalStateException e) { }

            try {
                in.longValue();
                fail("expected exception from longValue on " + type);
            }
            catch (IllegalStateException e) { }

            try {
                in.bigIntegerValue();
                fail("expected exception from bigIntegerValue on " + type);
            }
            catch (IllegalStateException e) { }
        }
    }


    private void skipThroughTopLevelContainer(String data)
    {
        read(data);

        in.next();
        in.stepIn();
        {
            while (in.next() != null)
            {
                // Just skip the children
            }
            expectEof();
        }
        in.stepOut();
        expectTopEof();
    }


    private String[] LOB_DATA = {
          "{{\"\"}}",
          "{{\"clob\"}}",

          "{{''''''}}",
          "{{''' '''}}",
          "{{'''clob'''}}",
          "{{'''c}ob'''}}",
          "{{'''c}}b'''}}",
          "{{'''c\\'''ob'''}}",

          "{{}}",
          "{{Zm9v}}",
    };

    private void testSkippingLob(String containerPrefix,
                                 String containerSuffix)
    {
        for (String lob : LOB_DATA)
        {
            String data = containerPrefix + lob + containerSuffix;
            skipThroughTopLevelContainer(data);

            data = containerPrefix + " " + lob + containerSuffix;
            skipThroughTopLevelContainer(data);

            data = containerPrefix + " " + lob + " " + containerSuffix;
            skipThroughTopLevelContainer(data);

            data = containerPrefix + lob + " " + containerSuffix;
            skipThroughTopLevelContainer(data);
        }
    }


    @Test
    public void testSkippingLobInList()
    {
        testSkippingLob("[1, { c:", " } ]");
        testSkippingLob("[1, { c:", "}]");
    }


    @Test
    public void testSkippingLobInStruct()
    {
        testSkippingLob("{a:1, b:{ c:", " } }");
        testSkippingLob("{a:1, b:{ c:", " }}");
        testSkippingLob("{a:1, b:{ c:", "}}");
    }

    @Test
    public void testReadLobUsingGetBytes() throws Exception
    {
        if (!myReaderMaker.sourceIsBinary()) {
            // TODO text implements getBytes() differently: https://github.com/amzn/ion-java/issues/319
            return;
        }
        String data = "{{\"abcdefghijklmnopqrstuvwxyz\"}}";
        byte[] partialBuffer = new byte[10];
        read(data);
        assertEquals(IonType.CLOB, in.next());
        int remaining = in.byteSize();
        assertEquals(26, remaining);
        StringBuilder value = new StringBuilder();
        while (remaining > 0) {
            int bytesRead = in.getBytes(partialBuffer, 0, partialBuffer.length);
            remaining -= bytesRead;
            value.append(new String(partialBuffer, 0, bytesRead, "UTF-8"));
        }
        assertEquals("abcdefghijklmnopqrstuvwxyz", value.toString());
    }

    @Test
    public void testGetSymbolTableBeforeFirstValue()
    {
        read("123");
        SymbolTable symtab = in.getSymbolTable();
        expectNoCurrentValue();
        assertNotNull(symtab);
        assertTrue(symtab.isSystemTable());
    }

}
