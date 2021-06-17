package com.amazon.ion.impl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.amazon.ion.BitUtils.bytes;
import static org.junit.Assert.assertTrue;
import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ResizingPipedInputStreamTest {

    private enum ReceiveMethod {
        BYTE_ARRAY {
            @Override
            void receive(ResizingPipedInputStream input, byte[] bytes, int off, int len) {
                if (off == 0 && len == bytes.length) {
                    // Not necessary, but provides coverage of this method.
                    input.receive(bytes);
                } else {
                    input.receive(bytes, off, len);
                }
            }
        },
        INDIVIDUAL_BYTES {
            @Override
            void receive(ResizingPipedInputStream input, byte[] bytes, int off, int len) {
                for (int i = off; i < off + len; i++) {
                    input.receive(bytes[i]);
                }
            }
        },
        INPUT_STREAM {
            @Override
            void receive(ResizingPipedInputStream input, byte[] bytes, int off, int len) throws IOException {
                byte[] subset = bytes;
                if (off > 0) {
                    int subsetLen = bytes.length - off;
                    subset = new byte[subsetLen];
                    System.arraycopy(bytes, off, subset, 0, subsetLen);
                }
                ByteArrayInputStream inputStream = new ByteArrayInputStream(subset);
                assertEquals(len, input.receive(inputStream, len));
            }
        }
        ;

        abstract void receive(ResizingPipedInputStream input, byte[] bytes, int off, int len) throws IOException;
    }

    @Parameters(name = "initialSize={0}, receive={1}, boundary={2}")
    public static Iterable<Object[]> bufferSizes() {
        List<Object[]> parameters = new ArrayList<Object[]>();
        for (int initialSize : Arrays.asList(1, 2, 3, 4, 5, 6, 7)) {
            for (ReceiveMethod receiveMethod : ReceiveMethod.values()) {
                parameters.add(new Object[]{initialSize, receiveMethod, true});
                parameters.add(new Object[]{initialSize, receiveMethod, false});
            }
        }
        return parameters;
    }

    @Parameter
    public int bufferSize;

    @Parameter(1)
    public ReceiveMethod receiveMethod;

    @Parameter(2)
    public boolean useBoundary;

    public ResizingPipedInputStream input;
    public int knownCapacity;

    @Before
    public void setup() {
        input = new ResizingPipedInputStream(bufferSize, Integer.MAX_VALUE, useBoundary);
        knownCapacity = input.capacity();
    }

    private void handleBoundary(int len) {
        if (useBoundary) {
            assertTrue(input.available() <= input.size());
            assertEquals(len, input.availableBeyondBoundary());
            assertTrue(input.getBoundary() <= input.getWriteIndex());
            input.extendBoundary(len);
        }
        assertEquals(input.available(), input.size());
        assertEquals(input.getBoundary(), input.getWriteIndex());
        assertEquals(0, input.availableBeyondBoundary());
    }

    private void receive(int b) {
        input.receive(b);
        handleBoundary(1);
    }

    private void receive(byte[] bytes, int off, int len) throws IOException {
        receiveMethod.receive(input, bytes, off, len);
        handleBoundary(len);
    }

    private void receive(byte[] bytes) throws IOException {
        receive(bytes, 0, bytes.length);
    }

    private int receive(InputStream stream, int len) throws IOException {
        int received = input.receive(stream, len);
        handleBoundary(received);
        return received;
    }

    // Asserts that growth (since the start of the test or the last time this method was called) only occurs
    // if the initial buffer size less than the given threshold.
    private void assertGrowthThreshold(int growthThreshold) {
        if (bufferSize < growthThreshold) {
            assertTrue("Growth was expected but did not occur.", knownCapacity < input.capacity());
        } else {
            assertEquals("Growth occurred but was not expected.", knownCapacity, input.capacity());
        }
        knownCapacity = input.capacity();
    }

    // Asserts that growth (since the start of the test or the last time this method was called) did not occur.
    private void assertNoGrowth() {
        assertGrowthThreshold(0);
    }

    @Test
    public void basicWriteRead() throws Exception {
        assertEquals(0, input.available());
        receive(1);
        assertNoGrowth();
        assertEquals(1, input.available());
        assertEquals(1, input.read());
        assertEquals(0, input.available());
        receive(bytes(2, 3), 0, 2);
        assertGrowthThreshold(2);
        assertEquals(2, input.available());
        byte[] readTwo = new byte[2];
        assertEquals(2, input.read(readTwo));
        assertArrayEquals(bytes(2, 3), readTwo);
        assertEquals(0, input.available());
    }

    @Test
    public void alternatingWriteRead() throws Exception {
        receive((byte) 1);
        assertNoGrowth();
        assertEquals(1, input.read());
        receive(bytes(2, 3));
        assertGrowthThreshold(2);
        assertEquals(2, input.read(new byte[2]));
        receive(bytes(4, 5, 6));
        assertGrowthThreshold(3);
        assertEquals(3, input.read(new byte[3]));
        receive(bytes(14, 15));
        assertNoGrowth();
        assertEquals(2, input.read(new byte[2]));
        receive(bytes(16));
        assertNoGrowth();
        assertEquals(16, input.read());
    }

    @Test
    public void consecutiveReads() throws Exception {
        assertEquals(0, input.available());
        receive(bytes(1, 2, 3), 0, 3);
        assertGrowthThreshold(3);
        assertEquals(3, input.available());
        assertEquals(1, input.read());
        assertEquals(2, input.available());
        assertEquals(2, input.read());
        assertEquals(1, input.available());
        assertEquals(3, input.read());
        assertEquals(0, input.available());
    }

    @Test
    public void consecutiveSkips() throws Exception {
        assertEquals(0, input.available());
        receive(bytes(1, 2, 3), 0, 3);
        assertGrowthThreshold(3);
        assertEquals(3, input.available());
        assertEquals(1, input.skip(1));
        assertEquals(2, input.available());
        assertEquals(1, input.skip(1));
        assertEquals(1, input.available());
        assertEquals(1, input.skip(1));
        assertEquals(0, input.available());
    }

    @Test
    public void growsMultipleTimesOnOneWrite() throws Exception {
        // Grows multiple times when the buffer size is less than 3.
        receive(bytes(1, 2, 3, 4, 5), 0, 5);
        assertGrowthThreshold(5);
        assertEquals(5, input.available());
        byte[] readFive = new byte[5];
        assertEquals(5, input.read(readFive));
        assertArrayEquals(bytes(1, 2, 3, 4, 5), readFive);
        assertEquals(0, input.available());
    }

    @Test
    public void returnsNegativeOneFromRead() throws Exception {
        assertEquals(0, input.available());
        assertEquals(-1, input.read());
        assertEquals(0, input.available());
        assertEquals(-1, input.read(new byte[5]));
        assertEquals(0, input.available());
        assertNoGrowth();
    }

    @Test
    public void readsLessBytesThanRequested() throws Exception {
        receive(bytes(1, 2, 3, 4, 5));
        assertGrowthThreshold(5);
        assertEquals(5, input.available());
        byte[] tryReadSix = bytes(0, 0, 0, 0, 0, 42);
        assertEquals(5, input.read(tryReadSix));
        assertArrayEquals(bytes(1, 2, 3, 4, 5, 42), tryReadSix);
        assertEquals(0, input.available());
    }

    @Test
    public void skipsLessBytesThanRequested() throws Exception {
        receive(bytes(1));
        assertEquals(1, input.skip(10));
        assertEquals(-1, input.read());
        assertEquals(0, input.available());
        assertEquals(0, input.skip(1));
        assertNoGrowth();
    }

    @Test
    public void skipsLessBytesThanRequestedLongMaxValue() throws Exception {
        receive(bytes(1, 2, 3, 4, 5));
        assertGrowthThreshold(5);
        assertEquals(5, input.skip(Long.MAX_VALUE));
        assertEquals(-1, input.read());
        assertEquals(0, input.available());
        assertEquals(0, input.skip(1));
        assertNoGrowth();
    }

    @Test
    public void skipsLessBytesThanRequestedWhenEmpty() throws Exception {
        assertEquals(0, input.skip(10));
        receive( bytes(1));
        assertEquals(1, input.read());
        assertNoGrowth();
    }

    @Test
    public void receiveSubsetOfBufferAfterStart() throws Exception {
        receive(bytes(1, 2, 3, 4), 1, 3);
        assertGrowthThreshold(3);
        assertEquals(3, input.available());
        byte[] readThree = new byte[3];
        assertEquals(3, input.read(readThree));
        assertArrayEquals(bytes(2, 3, 4), readThree);
        assertEquals(0, input.available());
    }

    @Test
    public void receiveSubsetOfBufferBeforeEnd() throws Exception {
        receive(bytes(1, 2, 3, 4), 1, 2);
        assertGrowthThreshold(2);
        assertEquals(2, input.available());
        byte[] readTwo = new byte[2];
        assertEquals(2, input.read(readTwo));
        assertArrayEquals(bytes(2, 3), readTwo);
        assertEquals(0, input.available());
    }

    @Test
    public void receiveLessBytesThanRequestedFromInputStream() throws Exception {
        receive(bytes(1, 2));
        assertGrowthThreshold(2);
        ByteArrayInputStream source = new ByteArrayInputStream(new byte[]{3, 4});
        assertEquals(2, receive(source, 3));
        assertEquals(4, input.available());
        byte[] readFour = new byte[4];
        assertEquals(4, input.read(readFour));
        assertArrayEquals(bytes(1, 2, 3, 4), readFour);
        assertEquals(0, input.available());
    }

    @Test
    public void readZeroReturnsZero() throws Exception {
        receive( bytes(42));
        assertEquals(1, input.available());
        assertEquals(0, input.read(new byte[0]));
        assertEquals(0, input.read(new byte[5], 0, 0));
        assertEquals(1, input.available());
        assertNoGrowth();
    }

    @Test
    public void skipZeroReturnsZero() throws Exception {
        receive(bytes(42));
        assertEquals(1, input.available());
        assertEquals(0, input.skip(0));
        assertEquals(1, input.available());
        assertNoGrowth();
    }

    @Test
    public void readReturnsToStart() throws Exception {
        receive(bytes(1, 2));
        assertGrowthThreshold(2);
        assertEquals(1, input.read());
        assertEquals(2, input.read());
        assertEquals(0, input.available());
        assertEquals(-1, input.read());
        // Because there are no un-read bytes, the following should write at the beginning of the buffer and
        // set the write and read indexes appropriately.
        receive(bytes(3));
        assertNoGrowth();
        assertEquals(1, input.available());
        assertEquals(3, input.read());
    }

    @Test
    public void skipReturnsToStart() throws Exception {
        receive(bytes(1, 2));
        assertGrowthThreshold(2);
        assertEquals(1, input.skip(1));
        assertEquals(1, input.skip(1));
        assertEquals(0, input.available());
        assertEquals(-1, input.read());
        // Because there are no un-read bytes, the following should write at the beginning of the buffer and
        // set the write and read indexes appropriately.
        receive(bytes(3));
        assertNoGrowth();
        assertEquals(1, input.available());
        assertEquals(1, input.skip(1));
    }

    @Test
    public void skipsAndReads() throws Exception {
        receive(bytes(1, 42, 2, 42, 42, 3, 4, 42, 42, 42, 5, 6, 7));
        assertEquals(1, input.read());
        assertEquals(1, input.skip(1));
        assertEquals(2, input.read());
        assertEquals(2, input.skip(2));
        byte[] readTwo = new byte[2];
        assertEquals(2, input.read(readTwo));
        assertArrayEquals(bytes(3, 4), readTwo);
        assertEquals(3, input.skip(3));
        byte[] readThree = new byte[3];
        assertEquals(3, input.read(readThree));
        assertArrayEquals(bytes(5, 6, 7), readThree);
        assertEquals(0, input.skip(1));
        assertEquals(-1, input.read());
        assertEquals(0, input.available());
    }

    @Test
    public void rewind() throws Exception {
        receive(bytes(1, 2, 3));
        assertEquals(1, input.read());
        int available = input.available();
        int readIndex = input.getReadIndex();
        int size = input.size();
        assertEquals(2, input.read());
        assertEquals(3, input.read());
        input.rewind(readIndex, available);
        assertEquals(available, input.available());
        assertEquals(readIndex, input.getReadIndex());
        assertEquals(size, input.size());
        assertEquals(2, input.read());
        assertEquals(3, input.read());
        assertEquals(-1, input.read());
        assertEquals(0, input.available());
        input.rewind(0, 3);
        assertEquals(3, input.available());
        assertEquals(3, input.size());
        assertEquals(0, input.availableBeyondBoundary());
        assertEquals(0, input.getReadIndex());
        byte[] readThree = new byte[3];
        assertEquals(3, input.read(readThree));
        assertArrayEquals(bytes(1, 2, 3), readThree);

    }

    private void assertCopyEquals(byte[] expected) throws Exception {
        final int startAvailable = input.available();
        final int startReadIndex = input.getReadIndex();
        final int startCapacity = input.capacity();
        ByteArrayOutputStream copiedBytes = new ByteArrayOutputStream();
        input.copyTo(copiedBytes);
        assertArrayEquals(expected, copiedBytes.toByteArray());
        // copyTo never mutates internal state.
        assertEquals(startAvailable, input.available());
        assertEquals(startReadIndex, input.getReadIndex());
        assertEquals(startCapacity, input.capacity());
    }

    @Test
    public void copyTo() throws Exception {
        byte[] data = bytes(1, 2, 3, 4, 5);
        receive(data);
        assertCopyEquals(data);
        assertEquals(1, input.read());
        assertCopyEquals(bytes(2, 3, 4, 5));
        assertEquals(2, input.read());
        assertCopyEquals(bytes(3, 4, 5));
        assertEquals(3, input.read());
        assertCopyEquals(bytes(4, 5));
        assertEquals(4, input.read());
        assertCopyEquals(bytes(5));
        assertEquals(5, input.read());
        assertCopyEquals(bytes());
        receive(bytes(6));
        assertCopyEquals(bytes(6));
        receive(bytes(7, 8));
        assertEquals(6, input.read());
        assertCopyEquals(bytes(7, 8));
        assertCopyEquals(bytes(7, 8));
        assertEquals(7, input.read());
        assertCopyEquals(bytes(8));
        assertEquals(8, input.read());
        assertCopyEquals(bytes());
    }

    @Test
    public void truncate() throws Exception {
        int markedAvailable = input.available();
        int markedWriteIndex = input.getWriteIndex();
        receive(bytes(9, 9));
        input.truncate(markedWriteIndex, markedAvailable);
        assertEquals(0, input.available());
        receive(bytes(1, 2));
        markedAvailable = input.available();
        markedWriteIndex = input.getWriteIndex();
        receive(bytes(9, 9, 9, 9, 9));
        input.truncate(markedWriteIndex, markedAvailable);
        assertEquals(2, input.available());
        receive(bytes(3, 4, 5));
        markedAvailable = input.available();
        markedWriteIndex = input.getWriteIndex();
        input.truncate(markedWriteIndex, markedAvailable);
        assertEquals(5, input.available());
        byte[] readFive = new byte[5];
        assertEquals(5, input.read(readFive));
        assertArrayEquals(bytes(1, 2, 3, 4, 5), readFive);
        assertEquals(0, input.available());
    }

    @Test(expected = IllegalArgumentException.class)
    public void errorsOnInvalidInitialBufferSize() {
        new ResizingPipedInputStream(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void errorsOnInvalidMaximumBufferSize() {
        new ResizingPipedInputStream(10, 9, useBoundary);
    }

    @Test
    public void initialBufferSizeAndCapacity() {
        assertEquals(bufferSize, input.getInitialBufferSize());
        assertEquals(bufferSize, input.capacity());
    }

    private static class ThrowingInputStream extends FilterInputStream {

        ThrowingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            throw new EOFException();
        }
    }

    @Test
    public void receiveOfThrowingInputStreamDoesNotThrow() throws Exception {
        // Tests that InputStream implementations that throw EOFException when too many bytes are requested do not
        // cause ResizingPipedInputStream.receive to throw.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("abc".getBytes("UTF-8"));
        InputStream in = new ThrowingInputStream(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(0, receive(in, 100));
        in.close();
    }

    @Test
    public void receiveOfPartialGzipStreamDoesNotThrow() throws Exception {
        // Similar to the previous test, but specifically tests with GZIPInputStream since it is probably the most
        // commonly-used InputStream implementation that has been known to exhibit this behavior.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStream gzipOut = new GZIPOutputStream(out);
        gzipOut.write("abc".getBytes("UTF-8"));
        gzipOut.close();
        byte[] completeGzip = out.toByteArray();
        // Cutting off 7 bytes removes part of the GZIP trailer.
        byte[] truncatedGzip = new byte[completeGzip.length - 7];
        System.arraycopy(completeGzip, 0, truncatedGzip, 0, truncatedGzip.length);
        InputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(truncatedGzip));
        // Ask for more bytes than are available. The GZIPInputStream will throw EOFException because the trailer
        // is incomplete. The ResizingPipedInputStream should handle this exception in the same way as a
        // graceful EOF conveyed by any other InputStream implementation. Since only the trailer was missing, all
        // three data bytes will be read.
        assertEquals(3, receive(gzipIn, 100));
        assertEquals(0, receive(gzipIn, 100));
        byte[] bytesRead = new byte[3];
        assertEquals(3, input.read(bytesRead));
        assertEquals("abc", new String(bytesRead, "UTF-8"));
        gzipIn.close();
    }

    @Test
    public void seekTo() throws Exception {
        receive(bytes(1, 2, 3));
        assertEquals(3, input.available());
        assertEquals(1, input.read());
        assertEquals(2, input.available());
        input.seekTo(0);
        assertEquals(3, input.available());
        input.seekTo(2);
        assertEquals(1, input.available());
        assertEquals(3, input.read());
        assertEquals(0, input.available());
        input.seekTo(1);
        assertEquals(2, input.available());
        assertEquals(2, input.read());
        input.seekTo(3);
        assertEquals(0, input.available());
        assertEquals(-1, input.read());
        input.seekTo(0);
        assertEquals(3, input.available());
        byte[] readThree = new byte[3];
        assertEquals(3, input.read(readThree));
        assertArrayEquals(bytes(1, 2, 3), readThree);
        assertEquals(0, input.available());
        input.seekTo(3);
        assertEquals(0, input.available());
    }

    @Test
    public void clear() throws Exception {
        receive(bytes(1, 2, 3));
        assertEquals(1, input.read());
        input.clear();
        assertEquals(-1, input.read());
        assertEquals(0, input.available());
        receive(bytes(4, 5, 6));
        assertEquals(3, input.available());
        byte[] readThree = new byte[3];
        assertEquals(3, input.read(readThree));
        assertArrayEquals(bytes(4, 5, 6), readThree);
        assertEquals(0, input.available());
        input.clear();
        assertEquals(-1, input.read());
        assertEquals(0, input.available());
    }

    @Test
    public void getByteBuffer() throws Exception {
        receive((byte) 1);
        java.nio.ByteBuffer byteBuffer = input.getByteBuffer(0, 1);
        assertEquals(1, byteBuffer.get());
        receive(bytes(2, 3));
        byteBuffer = input.getByteBuffer(0, 3);
        byte[] bytes = new byte[3];
        byteBuffer.get(bytes);
        assertArrayEquals(new byte[]{1, 2, 3}, bytes);
        receive(bytes(4, 5, 6));
        bytes = new byte[3];
        byteBuffer = input.getByteBuffer(3, 6);
        byteBuffer.get(bytes);
        assertArrayEquals(new byte[]{4, 5, 6}, bytes);
    }

    @Test
    public void boundary() throws Exception {
        if (!useBoundary) {
            return;
        }
        input.receive(bytes(1, 2, 3));
        assertEquals(1, input.peek(0));
        assertEquals(2, input.peek(1));
        assertEquals(3, input.peek(2));
        byte[] twoThree = new byte[2];
        input.get(1, twoThree, 0, 2);
        assertArrayEquals(bytes(2, 3), twoThree);
        // The boundary has not been extended, so bytes are available through the InputStream interface.
        assertEquals(0, input.available());
        assertEquals(0, input.getBoundary());
        assertEquals(-1, input.read());
        // size() indicates the total number of bytes.
        assertEquals(3, input.size());
        assertEquals(3, input.availableBeyondBoundary());
        // Now extend the boundary and verify bytes become available.
        input.extendBoundary(1);
        assertEquals(1, input.getBoundary());
        assertEquals(1, input.available());
        assertEquals(2, input.availableBeyondBoundary());
        assertEquals(3, input.size());
        assertEquals(1, input.read());
        input.extendBoundary(2);
        assertEquals(3, input.getBoundary());
        assertEquals(2, input.available());
        assertEquals(0, input.availableBeyondBoundary());
        assertEquals(2, input.size());
        twoThree = new byte[2];
        assertEquals(2, input.read(twoThree));
        assertArrayEquals(bytes(2, 3), twoThree);
        assertEquals(0, input.available());
        assertEquals(0, input.availableBeyondBoundary());
        assertEquals(0, input.size());
        input.receive(4);
        assertEquals(0, input.available());
        assertEquals(1, input.availableBeyondBoundary());
        assertEquals(1, input.size());
        byte[] fourFive = new byte[3];
        assertEquals(-1, input.read(fourFive));
        assertArrayEquals(bytes(0, 0, 0), fourFive);
        input.receive(5);
        assertEquals(0, input.available());
        assertEquals(2, input.availableBeyondBoundary());
        assertEquals(2, input.size());
        input.extendBoundary(1);
        assertEquals(1, input.available());
        assertEquals(1, input.availableBeyondBoundary());
        assertEquals(2, input.size());
        assertEquals(1, input.read(fourFive));
        assertArrayEquals(bytes(4, 0, 0), fourFive);
        assertEquals(0, input.available());
        assertEquals(1, input.availableBeyondBoundary());
        assertEquals(1, input.size());
        input.extendBoundary(1);
        assertEquals(1, input.available());
        assertEquals(0, input.availableBeyondBoundary());
        assertEquals(1, input.size());
        assertEquals(1, input.read(fourFive, 1, 2));
        assertArrayEquals(bytes(4, 5, 0), fourFive);
        assertEquals(0, input.available());
        assertEquals(0, input.availableBeyondBoundary());
        assertEquals(0, input.size());
    }

    @Test
    public void rewindWithBoundary() throws Exception {
        if (!useBoundary) {
            return;
        }
        input.receive(bytes(1, 2, 3));
        input.extendBoundary(2);
        assertEquals(1, input.read());
        int available = input.available();
        int readIndex = input.getReadIndex();
        int size = input.size();
        int availableBeyondBoundary = input.availableBeyondBoundary();
        assertEquals(2, input.read());
        assertEquals(1, input.availableBeyondBoundary());
        input.rewind(readIndex, available);
        assertEquals(available, input.available());
        assertEquals(readIndex, input.getReadIndex());
        assertEquals(size, input.size());
        assertEquals(availableBeyondBoundary, input.availableBeyondBoundary());
        assertEquals(2, input.read());
        assertEquals(0, input.available());
        assertEquals(1, input.availableBeyondBoundary());
        input.extendBoundary(1);
        assertEquals(3, input.read());
        assertEquals(0, input.available());
        assertEquals(0, input.availableBeyondBoundary());
        assertEquals(-1, input.read());
        input.rewind(0, 3);
        assertEquals(3, input.available());
        assertEquals(0, input.availableBeyondBoundary());
        assertEquals(0, input.getReadIndex());
        assertEquals(3, input.getBoundary());
        byte[] readThree = new byte[3];
        assertEquals(3, input.read(readThree));
        assertArrayEquals(bytes(1, 2, 3), readThree);
    }

    @Test
    public void consolidate() throws Exception {
        receive(bytes(1, 2, 98, 99, 3));
        assertEquals(5, input.available());
        assertEquals(5, input.size());
        input.consolidate(4, 2);
        assertEquals(3, input.available());
        assertEquals(3, input.size());
        byte[] readThree = new byte[3];
        assertEquals(3, input.read(readThree));
        assertArrayEquals(bytes(1, 2, 3), readThree);
        receive(bytes(4, 5));
        assertEquals(2, input.available());
        assertEquals(2, input.size());
        input.consolidate(input.getWriteIndex() - 1, input.getReadIndex());
        assertEquals(1, input.available());
        assertEquals(1, input.size());
        assertEquals(5, input.read());
        receive(bytes(6));
        assertEquals(1, input.available());
        assertEquals(1, input.size());
        input.consolidate(input.getWriteIndex(), input.getReadIndex());
        assertEquals(0, input.available());
        assertEquals(0, input.size());
        assertEquals(-1, input.read());
    }

    @Test
    public void consolidateWithBadIndicesFails() throws Exception {
        receive(bytes(1, 2, 3));
        try {
            input.consolidate(4, 1);
            Assert.fail("Expected exception because fromIndex 4 is beyond the writeIndex.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }
        assertEquals(1, input.read());
        try {
            input.consolidate(2, 0);
            Assert.fail("Expected exception because toIndex 0 is before the readIndex.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }
        input.receive(4);
        if (useBoundary) {
            // The boundary has not been extended beyond the last received byte.
            try {
                input.consolidate(input.getBoundary() + 1, input.getReadIndex());
                Assert.fail("Expected exception because fromIndex 4 is beyond the boundary.");
            } catch (IllegalArgumentException e) {
                // Expected.
            }
        }
    }

    @Test(expected = BufferOverflowException.class)
    public void errorsWhenMaximumSizeExceeded() {
        input = new ResizingPipedInputStream(bufferSize, bufferSize, useBoundary);
        input.receive(new byte[bufferSize + 1]);
    }

    private static class RecordingNotificationConsumer implements ResizingPipedInputStream.NotificationConsumer {

        int leftShiftAmount = 0;

        @Override
        public void bytesConsolidatedToStartOfBuffer(int leftShiftAmount) {
            this.leftShiftAmount = leftShiftAmount;
        }
    }

    @Test
    public void registerNotificationConsumer() throws Exception {
        RecordingNotificationConsumer notificationConsumer = new RecordingNotificationConsumer();
        input.registerNotificationConsumer(notificationConsumer);
        receive(bytes(1, 2));
        assertEquals(1, input.read());
        assertEquals(2, input.read());
        assertEquals(2, input.getReadIndex());
        // Receive one more byte than is available at the end of the buffer, requiring the bytes currently
        // buffered to be moved to the start of the buffer.
        receive(new byte[input.capacity() - input.getWriteIndex() + 1]);
        // The bytes will have been shifted left by 2 since the readIndex was 2 before the shift.
        assertEquals(2, notificationConsumer.leftShiftAmount);
    }
}
