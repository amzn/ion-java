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

import static com.amazon.ion.TestUtils.GLOBAL_SKIP_LIST;
import static com.amazon.ion.TestUtils.GOOD_IONTESTS_FILES;
import static com.amazon.ion.TestUtils.testdataFiles;
import static com.amazon.ion.impl._Private_Utils.utf8;
import static com.amazon.ion.system.IonWriterBuilder.InitialIvmHandling.SUPPRESS;

import com.amazon.ion.IonBinaryWriter;
import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonLoader;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonTestCase;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.RoundTripTest;
import com.amazon.ion.impl._Private_Utils;
import com.amazon.ion.junit.Injected.Inject;
import com.amazon.ion.junit.IonAssert;
import com.amazon.ion.system.IonTextWriterBuilder;
import com.amazon.ion.util.Equivalence;
import com.amazon.ion.util.Printer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * TODO amzn/ion-java/issues/29 Refactor this test class, possible duplicate test coverage in
 * {@link RoundTripTest}.
 */
public class RoundTripStreamingTest
    extends IonTestCase
{
    static final boolean _debug_flag = true;
    static final boolean _debug_dump_datagrams = false;

    @Inject("testFile")
    public static final File[] FILES = testdataFiles(GLOBAL_SKIP_LIST, GOOD_IONTESTS_FILES);

    @Inject("copySpeed")
    public static final StreamCopySpeed[] STREAM_COPY_SPEEDS =
        StreamCopySpeed.values();

    private Printer       myPrinter;
    private StringBuilder myBuilder;
    private byte[]        myBuffer;
    private File          myTestFile;

    public void setTestFile(File file)
    {
        myTestFile = file;
    }

    @Override
    @Before
    public void setUp()
    throws Exception
    {
        super.setUp();
        myPrinter = new Printer();
        myBuilder = new StringBuilder();
        myBuffer = _Private_Utils.loadFileBytes(myTestFile);
    }

    @Override
    @After
    public void tearDown()
    throws Exception
    {
        myPrinter = null;
        myBuilder = null;
        myBuffer  = null;
        super.tearDown();
    }

    private String makeString(IonDatagram datagram)
    throws IOException
    {
        boolean is_first = true;
        // iterator used to be .systemIterator()
        Iterator it = datagram.iterator();
        while (it.hasNext()) {
            IonValue value = (IonValue) it.next();
            if (is_first) {
                is_first = false;
            }
            else {
                myBuilder.append(' ');
            }
            myPrinter.print(value, myBuilder);
            // Why is this here?  myBuilder.append('\n');
        }

        String text = myBuilder.toString();
        myBuilder.setLength(0);
        return text;
    }

    /**
     * Use IonReader to consume the buffer, and IonTextWriter to print it out.
     */
    private byte[] makeText(byte[] buffer, boolean prettyPrint)
    throws IOException
    {
        IonReader in = makeIterator(buffer);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IonTextWriterBuilder b = IonTextWriterBuilder.standard();
        if (prettyPrint)
        {
            b.withPrettyPrinting();
        }
        b.setInitialIvmHandling(SUPPRESS);

        IonWriter tw = b.build(out);

        tw.writeValues(in);
        tw.close();
        in.close();

        byte[] buf = out.toByteArray(); // this is utf-8
        return buf;
    }

    private String makeString(byte[] buffer)
    throws IOException
    {
        byte[] buf = makeText(buffer, false);
        String text = utf8(buf);
        return text;
    }


    private byte[] makeBinary(byte[] buffer)
    throws IOException
    {
        IonReader in = makeIterator(buffer);
        IonBinaryWriter bw = system().newBinaryWriter();

        bw.writeValues(in);
        byte[] buf = bw.getBytes(); // this is binary
        in.close();
        return buf;
    }

    private byte[] makeBinary(IonDatagram datagram)
    {
        return datagram.getBytes();
    }

    private IonDatagram makeTree(byte[] buffer)
    throws IOException
    {
        IonReader in = makeIterator(buffer);
        IonDatagram dg = system().newDatagram();
        IonWriter tw = system().newTreeWriter(dg);

        tw.writeValues(in);
        //IonValue v = tw.getContentAsIonValue();
        in.close();
        return dg;
    }

    private static class roundTripBufferResults
    {
        roundTripBufferResults(String t) {
            title = t;
        }
        String      name;
        String      title;
        String      string;
        byte[]      utf8_buf;
        byte[]      utf8_pretty;
        byte[]      binary;
        IonDatagram ion;

        void compareResultsPass1(roundTripBufferResults other, IonLoader loader)
        {
            String vs = this.title + " vs " + other.title;

            compareStringAsTree("string: " + vs, other, loader);
            compareBuffers("utf8: " + vs, this.utf8_buf, other.utf8_buf);

            // We don't compare raw utf8_pretty buffers, since we shouldn't
            // care whether pretty-printing is byte-for-byte identical.
            compareUTF8AsTree("utf8: " + vs, other, loader);
            comparePrettyUTF8AsTree("pretty utf8: " + vs, other, loader);

            // now we compare the binary buffers both byte by byte and as a tree
            compareBuffers("binary: " + vs, this.binary, other.binary);
            compareBinaryAsTree("binary: " + vs, other, loader);

            boolean datagrams_are_equal = Equivalence.ionEquals(this.ion, other.ion);
            if (!datagrams_are_equal) {
                // Note: we're dumping this.binary, but that isn't what's
                // actually in the datagram.  So it could be misleading.
                dump_datagrams(other);
                datagrams_are_equal = Equivalence.ionEquals(this.ion, other.ion);
            }
            assertTrue("datagram: " + vs, Equivalence.ionEquals(this.ion, other.ion));
        }

        void compareResults(roundTripBufferResults other, IonLoader loader)
        {
            String vs = this.title + " vs " + other.title;
            assertEquals("string: " + vs, this.string, other.string);
            compareBuffers("utf8: " + vs, this.utf8_buf, other.utf8_buf);
            // We don't compare raw utf8_pretty buffers, since we shouldn't
            // care whether pretty-printing is byte-for-byte identical.
            compareBuffers("binary: " + vs, this.binary, other.binary);
            compareBinaryAsTree("binary: " + vs, other, loader);
            assertTrue("datagram: " + vs, Equivalence.ionEquals(this.ion, other.ion));
        }

        static void compareBuffers(String title, byte[] buf1, byte[] buf2) {
            assertTrue(title+" buf1 should not be null", buf1 != null);
            assertTrue(title+" buf2 should not be null", buf2 != null);

            // TODO later, when the namespace construction is fixed
            //             in both the iterators and the tree impl
            // assertEquals(title + " buffer lengths", buf1.length, buf2.length);
            // for (int ii=0; ii<buf1.length; ii++) {
            //  boolean bytes_are_equal = (buf1[ii] == buf2[ii]);
            //  assertTrue(title + " byte at "+ii, bytes_are_equal);
            // }
        }

        void compareStringAsTree(String title, roundTripBufferResults other, IonLoader loader) {
            // general approach:
            //      open tree over both, compare trees - use equiv
            //      open iterators over both, compare iterator contents
            IonDatagram dg1 = loader.load(this.string);
            IonDatagram dg2 = loader.load(other.string);

            // Set breakpoint on AssertionException if desired.
            IonAssert.assertIonEquals(title, dg1, dg2);
        }

        void compareUTF8AsTree(String title, roundTripBufferResults other, IonLoader loader) {
            // general approach:
            //      open tree over both, compare trees - use equiv
            //      open iterators over both, compare iterator contents
            IonDatagram dg1 = loader.load(this.utf8_buf);
            IonDatagram dg2 = loader.load(other.utf8_buf);

            // Set breakpoint on AssertionException if desired.
            IonAssert.assertIonEquals(title, dg1, dg2);
        }

        void comparePrettyUTF8AsTree(String title, roundTripBufferResults other, IonLoader loader) {
            // general approach:
            //      open tree over both, compare trees - use equiv
            //      open iterators over both, compare iterator contents
            IonDatagram dg1 = loader.load(this.utf8_pretty);
            IonDatagram dg2 = loader.load(other.utf8_pretty);

            boolean datagrams_are_equal = Equivalence.ionEquals(dg1, dg2);
            if (!datagrams_are_equal) {
                IonAssert.assertIonEquals(title, dg1, dg2);
            }
        }

        void compareBinaryAsTree(String title, roundTripBufferResults other, IonLoader loader) {
            // general approach:
            //      open tree over both, compare trees - use equiv
            //      open iterators over both, compare iterator contents
            IonDatagram dg1 = loader.load(this.binary);
            IonDatagram dg2 = loader.load(other.binary);

            boolean datagrams_are_equal = Equivalence.ionEquals(dg1, dg2);
            if (!datagrams_are_equal) {
                dump_datagrams(other);
                IonAssert.assertIonEquals(title, dg1, dg2);
            }
        }

        void dump_datagrams(roundTripBufferResults other) {
            if (_debug_dump_datagrams) {
                System.out.println("\n------------------------------\n");
                System.out.println(this.string);
                dump_binary(this.title, this.binary);
                System.out.println("\n");
                dump_binary(other.title, other.binary);
            }
        }

        void dump_binary(String title, byte[] buf)
        {
            System.out.println("dump buffer for "+title);
            if (buf == null) {
                System.out.println(" <null>");
            }
            else {
                int len = buf.length;
                System.out.println(" length: "+len);
                for (int ii=0; ii<len; ii++) {
                    int b = (buf[ii]) & 0xff;
                    if ((ii & 0xf) == 0) {
                        System.out.println();
                        String x = "     "+ii;
                        if (x.length() > 5)  x = x.substring(x.length() - 6);
                        System.out.print(x+": ");
                    }
                    String y = "00"+Integer.toHexString(b);
                    y = y.substring(y.length() - 2);
                    System.out.print(y+" ");
                }
                System.out.println();

                for (int ii=0; ii<len; ii++) {
                    int b = (buf[ii]) & 0xff;
                    if ((ii & 0xf) == 0) {
                        System.out.println();
                        String x = "     "+ii;
                        if (x.length() > 5)  x = x.substring(x.length() - 6);
                        System.out.print(x+": ");
                    }
                    String y = "  " + (char)((b >= 32 && b < 128) ? b : ' ');
                    y = y.substring(y.length() - 2);
                    System.out.print(y+" ");
                }
                System.out.println();


            }

        }

    }

    roundTripBufferResults roundTripBuffer(String pass, byte[] testBuffer)
    throws IOException
    {
        roundTripBufferResults stream =
            new roundTripBufferResults(pass + " stream");

        roundTripBufferResults tree =
            new roundTripBufferResults(pass + " tree");

        stream.name = myTestFile.getName() + " (as stream)";
        tree.name = myTestFile.getName() + " (as IonValue)";

        // load() takes ownership of the buffer
        IonDatagram inputDatagram = loader().load(testBuffer.clone());

        stream.utf8_buf    = makeText(testBuffer, false);
        stream.utf8_pretty = makeText(testBuffer, true);
        stream.string      = makeString(testBuffer);
        stream.binary      = makeBinary(testBuffer);
        stream.ion         = makeTree(testBuffer);
        checkBinaryHeader(stream.binary);

        // Turn the DOM back into text...
        tree.string        = makeString(inputDatagram);
        tree.utf8_buf      = utf8(tree.string);
        tree.utf8_pretty   = utf8(tree.string); // FIXME hack
        tree.binary        = makeBinary(inputDatagram);
        tree.ion           = inputDatagram;
        checkBinaryHeader(tree.binary);

        tree.compareResultsPass1(stream, myLoader);

        return stream;
    }

    IonReader makeIterator(byte [] testBuffer) {
        return getStreamingMode().newIonReader(system().getCatalog(), testBuffer);
    }


    @Test
    public void test()
    throws Exception
    {
        // general round trip plan for the streaming interfaces:
        //
        //  open iterator over test file
        //  create datagram over test file
        //  get string from datagram
        //  get binary from datagram
        //  pass iterator to text writer
        //     compare output with string from datagram
        //  pass iterator to binary writer
        //     compare output with binary from datagram
        //  pass iterator to tree writer
        //     output with datagram
        //  test comparison again with the resulting binary
        //  and resulting text (1 level recurse or 2?

        roundTripBufferResults pass1 = roundTripBuffer("original buffer", myBuffer);
        roundTripBufferResults pass2bin = roundTripBuffer("binary from pass 1",pass1.binary);
        roundTripBufferResults pass2text = roundTripBuffer("utf8 from pass 1", pass1.utf8_buf);
        roundTripBufferResults pass2pretty = roundTripBuffer("utf8 pretty from pass 1", pass1.utf8_pretty);
        if (pass2bin == pass2text && pass2pretty == pass2pretty) {
            // mostly to force these to be used (pass2*)
            throw new RuntimeException("boy this is odd");
        }
    }

    private int break_point_point(int x) throws Exception
    {
        int y = 2;
        int z;
        z = x + y;
        return z;
    }
}
