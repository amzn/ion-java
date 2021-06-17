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

import static com.amazon.ion.SystemSymbols.ION_SYMBOL_TABLE_SID;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import org.junit.Test;

public class BinaryTest extends IonTestCase
{
    public static byte[] hexToBytes(final String hex)
    {
        String[] hexChunks = hex.split("\\s+");
        byte[] data = new byte[hexChunks.length];
        for (int i = 0; i < hexChunks.length; i++)
        {
            final String hexChunk = hexChunks[i];
            final int ordinal = Integer.parseInt(hexChunk, 16);
            if ((ordinal & 0xFFFFFF00) != 0)
            {
                throw new IllegalArgumentException("Bad Chunk: " + hexChunk);
            }
            // this is safe because we only have the low bytes set
            data[i] = (byte) ordinal;
        }
        return data;
    }

    public static String bytesToHex(final byte[] bytes)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < bytes.length; i++)
        {
            byte octet = bytes[i];
            int ordinal = octet & 0xFF;
            builder.append(String.format("%02X", ordinal));
            if ((i + 1) < bytes.length)
            {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    public static String MAGIC_COOKIE = "E0 01 00 EA ";

    // MC + $ion_1_0::{symbols : null.struct}
    private static String EMPTY_HEADER = MAGIC_COOKIE;


// FIXME (cas) + "E5 81 82 D2 87 DF ";



    /**
     * Loads a string literal as Ion bytes no cookie required.
     * We encode the string literal as hex digits as it is convenient to read.
     *
     * @param   hex     A simple space delimited hex encoding.
     */
    private IonValue ion(final String hex)
    {
        byte[] data = hexToBytes(MAGIC_COOKIE + hex);
        if (getStreamingMode() == StreamingMode.NEW_STREAMING_INCREMENTAL) {
            IonReader reader = getStreamingMode().newIonReader(system().getCatalog(), data);
            Iterator<IonValue> iterator = system().iterate(reader);
            IonValue value = null;
            if (iterator.hasNext()) {
                value = iterator.next();
            }
            if (iterator.hasNext()) {
                throw new IonException("not a single value");
            }
            try {
                reader.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return value;
        }
        return system().singleValue(data);
    }

    /** Converts a single value to bytes using an empty datagram */
    private byte[] ionBytes(IonValue val)
    {
        IonDatagram dg = system().newDatagram(val);
        return dg.getBytes();
    }

    /** Dumps byte arrays to hex strings */
    private static String dump(byte[]... packets)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < packets.length; i++)
        {
            byte[] packet = packets[i];
            builder.append('"');
            builder.append(bytesToHex(packet));
            builder.append('"');

            if ((i + 1) < packets.length)
            {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private static void assertDoubleBits(IonValue val, long bits)
    {
        assertTrue(val instanceof IonFloat);
        IonFloat fval = (IonFloat) val;
        long test = Double.doubleToRawLongBits(fval.doubleValue());
        assertTrue(String.format("%X\n%X", test, bits), test == bits);
    }

    @Test
    public void testBinReadNull01()
    {
        // null.null
        IonValue val = ion("0F");

        assertTrue(val instanceof IonNull);
        assertTrue(val.isNullValue());
    }

    @Test
    public void testBinReadNull02()
    {
        // name::null.null
        IonValue val = ion("E3 81 84 0F");

        assertTrue(val instanceof IonNull);
        assertTrue(val.isNullValue());
    }

    @Test
    public void testBinReadFloat01()
    {
        // 1e0
        IonValue val = ion("48 3F F0 00 00 00 00 00 00");

        assertTrue(val instanceof IonFloat);
        assertTrue(((IonFloat) val).doubleValue() == 1.0);
    }

    @Test
    public void testBinReadFloat02()
    {
        // was: $ion_1_0::{} 1e0
        // now: $ion_symbol_table::{} 1e0
        String symbolTableAnnotation = Integer.toHexString((ION_SYMBOL_TABLE_SID + 0x80));
        IonValue val = ion("E3 81 " + symbolTableAnnotation +" D0 48 3F F0 00 00 00 00 00 00");

        assertTrue(val instanceof IonFloat);
        assertTrue(((IonFloat) val).doubleValue() == 1.0);
    }

    @Test
    public void testBinReadFloat03()
    {
        // approx 1.79769313486231e300
        IonValue val = ion("48 7E 45 79 8E E2 30 8C 26");
        assertDoubleBits(val, 0x7E45798EE2308C26L);
    }

    @Test
    public void testBinReadFloat04()
    {
        // approx -1.2278379192877e-276
        IonValue val = ion("48 86 A5 C3 F2 8D 5E C5 4A");
        assertDoubleBits(val, 0x86A5C3F28D5EC54AL);
    }

    @Test
    public void testBinReadFloat05()
    {
        // 1e0
        IonValue val = ion("44 3f 80 00 00");

        assertTrue(val instanceof IonFloat);
        assertTrue(1.0 == ((IonFloat) val).doubleValue());
    }

    @Test
    public void testBinReadFloat06()
    {
        // ≈ -991221.0227
        IonValue val = ion("44 C9 71 FF 50");

        assertTrue(val instanceof IonFloat);
        // since 32-bit values are upcast to 64-bit values, check
        // the double representation
        assertDoubleBits(val, 0xc12e3fea00000000L);
    }

    @Test
    public void testBinWriteFloat01()
    {
        IonFloat fval = system().newNullFloat();
        fval.setValue(-1.0);
        byte[] raw = ionBytes(fval);
        byte[] ref = hexToBytes(EMPTY_HEADER + "48 BF F0 00 00 00 00 00 00");

        assertTrue(dump(raw, ref), Arrays.equals(raw, ref));
    }

    @Test
    public void testBinWriteFloat02()
    {
        IonFloat fval = system().newNullFloat();
        // approx 1.79769313486231e300
        fval.setValue(Double.longBitsToDouble(0x7E45798EE2308C26L));
        byte[] raw = ionBytes(fval);
        byte[] ref = hexToBytes(EMPTY_HEADER + "48 7E 45 79 8E E2 30 8C 26");

        assertTrue(dump(raw, ref), Arrays.equals(raw, ref));
    }

    @Test
    public void testBinWriteFloat03()
    {
        IonFloat fval = system().newNullFloat();
        // approx -1.2278379192877e-276
        fval.setValue(Double.longBitsToDouble(0x86A5C3F28D5EC54AL));
        byte[] raw = ionBytes(fval);
        byte[] ref = hexToBytes(EMPTY_HEADER + "48 86 A5 C3 F2 8D 5E C5 4A");

        assertTrue(dump(raw, ref), Arrays.equals(raw, ref));
    }

}

