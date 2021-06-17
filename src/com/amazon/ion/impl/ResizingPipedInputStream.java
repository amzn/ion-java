package com.amazon.ion.impl;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 * Manages a resizing buffer for production and consumption of data within a <strong>single thread</strong>.
 * Buffered bytes may be consumed through the InputStream interface. This provides a few benefits over using
 * a PipedOutputStream/PipedInputStream pair in a single thread:
 * <ol>
 *     <li>There is no risk of deadlock. Piped streams, which are intended for producing data in one
 *     thread and consuming it in another, will block on read when no data is available and block on write
 *     when the buffer is full. In a single-threaded context, avoiding deadlock on read requires checking
 *     that bytes are available before every read. Avoiding deadlock on write would require checking that
 *     the buffer is not full before every write, but there is no built-in, publicly-accessible way of doing
 *     this with a PipedInputStream/PipedOutputStream.</li>
 *     <li>The buffer can grow. Piped streams use a fixed-size buffer that causes blocking when full. If
 *     used in a single-thread, this serves as a hard limit on the amount of data that can be written without
 *     a matching read. This can require arbitrary limits on data size to be imposed by the application. The
 *     NonBlockingCircularInputStream imposes no such limitation, but optionally allows for a maximum buffer
 *     size to be configured to protect against unbounded growth.</li>
 * </ol>
 */
public class ResizingPipedInputStream extends InputStream {

    /**
     * Handler of notifications provided by the ResizingPipedInputStream.
     */
    interface NotificationConsumer {

        /**
         * Bytes have been shifted to the start of the buffer in order to make room for additional bytes
         * to be buffered.
         * @param leftShiftAmount the amount of the left shift (also: the pre-shift read index of the first shifted
         *                        byte).
         */
        void bytesConsolidatedToStartOfBuffer(int leftShiftAmount);
    }

    /**
     * A NotificationConsumer that does nothing.
     */
    private static final NotificationConsumer NO_OP_NOTIFICATION_CONSUMER = new NotificationConsumer() {
        @Override
        public void bytesConsolidatedToStartOfBuffer(int leftShiftAmount) {
            // Do nothing.
        }
    };

    /**
     * Mask to isolate a single byte.
     */
    private static final int SINGLE_BYTE_MASK = 0xFF;

    /**
     * The initial size of the buffer and the number of bytes by which the size of the buffer will increase
     * each time it grows.
     */
    private final int initialBufferSize;

    /**
     * The maximum size of the buffer. If the user attempts to buffer more bytes than this, an exception will be raised.
     */
    private final int maximumBufferSize;

    /**
     * Whether to use a boundary to limit the number of available bytes. This can be used to buffer
     * arbitrarily-sized chunks of bytes without making them available for consumption. When true,
     * the boundary must be manually extended (see {@link #extendBoundary(int)} to make these bytes
     * available. When false, all buffered bytes will be available to read.
     */
    private final boolean useBoundary;

    /**
     * The NotificationConsumer currently registered.
     */
    private NotificationConsumer notificationConsumer = NO_OP_NOTIFICATION_CONSUMER;

    /**
     * The raw buffer.
     */
    private byte[] buffer;

    /**
     * View to the raw buffer.
     */
    private ByteBuffer byteBuffer;

    /**
     * @see #capacity()
     */
    private int capacity;

    /**
     * The index of the next byte in the buffer that is available to be read. Always less than or equal to `writeIndex`.
     */
    private int readIndex = 0;

    /**
     * The index at which the next byte received will be written. Always greater than or equal to `readIndex`.
     */
    private int writeIndex = 0;

    /**
     * @see #available()
     */
    private int available = 0;

    /**
     * @see #size()
     */
    private int size = 0;

    /**
     * @see #getBoundary()
     */
    private int boundary = 0;

    /**
     * Constructor.
     * @param initialBufferSize the initial size of the buffer. When full, the buffer will grow by this
     *                          many bytes. The buffer always stores bytes contiguously, so growth requires
     *                          allocation of a new buffer capable of holding the new capacity and copying of the
     *                          existing bytes into the new buffer. As such, a size should be chosen carefully
     *                          such that growth is expected to occur rarely, if ever.
     */
    public ResizingPipedInputStream(final int initialBufferSize) {
        this(initialBufferSize, Integer.MAX_VALUE, false);
    }

    /**
     * Constructor.
     * @param initialBufferSize the initial size of the buffer. When full, the buffer will grow by this
     *                          many bytes. The buffer always stores bytes contiguously, so growth requires
     *                          allocation of a new buffer capable of holding the new capacity and copying of the
     *                          existing bytes into the new buffer. As such, a size should be chosen carefully
     *                          such that growth is expected to occur rarely, if ever.
     * @param maximumBufferSize the maximum size of the buffer. If a call to `receive` attempts to transfer an amount
     *                          of bytes that would cause the buffer to exceed this size, a
     *                          {@link BufferOverflowException} will be thrown. Must be greater than or equal to the
     *                          initial buffer size.
     * @param useBoundary whether to use a boundary to limit the number of available bytes. This can be used to buffer
     *                    arbitrarily-sized chunks of bytes without making them available for consumption. When true,
     *                    the boundary must be manually extended (see {@link #extendBoundary(int)} to make these bytes
     *                    available. When false, all buffered bytes will be available to read.
     */
    ResizingPipedInputStream(final int initialBufferSize, final int maximumBufferSize, final boolean useBoundary) {
        if (initialBufferSize < 1) {
            throw new IllegalArgumentException("Initial buffer size must be at least 1.");
        }
        if (maximumBufferSize < initialBufferSize) {
            throw new IllegalArgumentException("Maximum buffer size cannot be less than the initial buffer size.");
        }
        this.initialBufferSize = initialBufferSize;
        this.maximumBufferSize = maximumBufferSize;
        this.capacity = initialBufferSize;
        buffer = new byte[initialBufferSize];
        byteBuffer = ByteBuffer.wrap(buffer, 0, capacity);
        this.useBoundary = useBoundary;
    }

    /**
     * Moves all buffered (but not yet read) bytes from 'buffer' to the destination buffer. In total, 'size' bytes
     * will be moved.
     * @param destinationBuffer the destination buffer, which may be 'buffer' itself or a new buffer.
     */
    private void moveBytesToStartOfBuffer(byte[] destinationBuffer) {
        if (size > 0) {
            System.arraycopy(buffer, readIndex, destinationBuffer, 0, size);
        }
        if (readIndex > 0) {
            notificationConsumer.bytesConsolidatedToStartOfBuffer(readIndex);
        }
        readIndex = 0;
        boundary = available;
        writeIndex = size;
    }

    /**
     * @return the number of bytes that can be written at the end of the buffer.
     */
    private int freeSpaceAtEndOfBuffer() {
        return capacity - writeIndex;
    }

    /**
     * Ensures that there is at least 'minimumNumberOfBytesRequired' bytes of free space in the buffer, growing the
     * buffer if necessary. May consolidate buffered bytes, performing an in-order copy and resetting indices
     * such that the `readIndex` points to the same byte and the `writeIndex` is positioned after the last
     * byte that is available to read.
     * @param minimumNumberOfBytesRequired the minimum amount of free space that needs to be available for writing.
     */
    private void ensureSpaceInBuffer(int minimumNumberOfBytesRequired) {
        if (size < 1 || freeSpaceAtEndOfBuffer() < minimumNumberOfBytesRequired) {
            // Free up space by moving any unread bytes to the start of the buffer and resetting the indices.
            moveBytesToStartOfBuffer(buffer);
            int shortfall = minimumNumberOfBytesRequired - freeSpaceAtEndOfBuffer();
            if (shortfall > 0) {
                // There is not enough space in the buffer even though all available bytes have already been
                // moved to the start of the buffer. Growth is required.
                int amountToGrow = Math.max(initialBufferSize, shortfall);
                if (capacity + amountToGrow > maximumBufferSize) {
                    amountToGrow = shortfall;
                    if (capacity + amountToGrow > maximumBufferSize) {
                        throw new BufferOverflowException();
                    }
                }
                byte[] newBuffer = new byte[buffer.length + amountToGrow];
                moveBytesToStartOfBuffer(newBuffer);
                capacity += amountToGrow;
                buffer = newBuffer;
                byteBuffer = ByteBuffer.wrap(buffer, readIndex, capacity);
            }
        }
    }

    /**
     * Buffers a single additional byte, growing the buffer if it is already full.
     * @param b the byte to buffer.
     */
    public void receive(final int b) {
        ensureSpaceInBuffer(1);
        buffer[writeIndex] = (byte) b;
        writeIndex++;
        size++;
        if (!useBoundary) {
            extendBoundary(1);
        }
    }

    /**
     * Buffers `len` additional bytes, growing the buffer if it is already full or if it would become full
     * by writing `len` bytes.
     * @param b the bytes to buffer.
     * @param off the offset into `b` that points to the first byte to buffer.
     * @param len the number of bytes to buffer.
     */
    public void receive(final byte[] b, final int off, final int len) {
        ensureSpaceInBuffer(len);
        System.arraycopy(b, off, buffer, writeIndex, len);
        writeIndex += len;
        size += len;
        if (!useBoundary) {
            extendBoundary(len);
        }
    }

    /**
     * Buffers `b.length` additional bytes.
     * @see #receive(byte[], int, int)
     * @param b the bytes to buffer.
     */
    public void receive(final byte[] b) {
        receive(b, 0, b.length);
    }

    /**
     * Buffers up to `len` additional bytes, growing the buffer if it is already full or if it would become full
     * by writing `len` bytes. This method will block if and only if the given `InputStream`'s
     * {@link InputStream#read(byte[], int, int)} blocks when trying to read `len` bytes. If this is not desired,
     * the caller should ensure that the given `InputStream` has at least `len` bytes available before calling
     * this method or provide an InputStream implementation that does not block.
     * @param input the source of the bytes.
     * @param len the number of bytes to attempt to write.
     * @return the number of bytes actually written, which will only be less than `len` if
     * {@link InputStream#read(byte[], int, int)} returns less than `len`.
     * @throws IOException if thrown by the given `InputStream` during read, except for {@link EOFException}. If an
     *   EOFException is thrown by the `InputStream`, it will be caught and this method will return the number of bytes
     *   that were received before the exception was thrown.
     */
    public int receive(final InputStream input, final int len) throws IOException  {
        ensureSpaceInBuffer(len);
        int numberOfBytesRead;
        try {
            numberOfBytesRead = input.read(buffer, writeIndex, len);
        } catch (EOFException e) {
            // Some InputStream implementations (such as GZIPInputStream) will throw EOFException instead of
            // returning -1.
            numberOfBytesRead = -1;
        }
        if (numberOfBytesRead > 0) {
            writeIndex += numberOfBytesRead;
            size += numberOfBytesRead;
        } else {
            numberOfBytesRead = 0;
        }
        if (!useBoundary) {
            extendBoundary(numberOfBytesRead);
        }
        return numberOfBytesRead;
    }

    /**
     * {@inheritDoc}
     * <p>
     * NOTE: This method adheres to the documented behavior of {@link InputStream#read(byte[], int, int)}
     * <strong>except</strong> that it never blocks. If a read is attempted before the first write,
     * this method will return -1.
     */
    @Override
    public int read(final byte[] b, final int off, final int len) {
        if (b.length == 0 || len == 0) {
            return 0;
        }
        if (available < 1) {
            return -1;
        }
        int bytesToRead = Math.min(available, len);
        System.arraycopy(buffer, readIndex, b, off, bytesToRead);
        readIndex += bytesToRead;
        available -= bytesToRead;
        size -= bytesToRead;
        return bytesToRead;
    }

    /**
     * Copies all of the available bytes in the buffer without changing the number of bytes available to subsequent
     * reads.
     * @param outputStream stream to which the bytes will be copied.
     * @throws IOException if thrown by {@link OutputStream#write(byte[], int, int)}.
     */
    public void copyTo(final OutputStream outputStream) throws IOException {
        outputStream.write(buffer, readIndex, available);
    }

    /**
     * Seeks the read index to the given position.
     * @param index the index to which to seek.
     */
    void seekTo(int index) {
        int amount = index - readIndex;
        available -= amount;
        size -= amount;
        readIndex = index;
    }

    /**
     * @return the current read index.
     */
    int getReadIndex() {
        return readIndex;
    }

    /**
     * @return the current write index.
     */
    int getWriteIndex() {
        return writeIndex;
    }

    /**
     * Rewinds the buffer to the given read index and sets 'available' to the given value. Subsequent
     * behavior is undefined unless the values resulted from calling {@link #getReadIndex()} and
     * {@link #available()} in immediate sequence, without any calls to 'receive' since.
     * @param previousReadIndex the read index value to be set.
     * @param previousAvailable the available value to be set.
     */
    void rewind(final int previousReadIndex, final int previousAvailable) {
        readIndex = previousReadIndex;
        available = previousAvailable;
        boundary = previousReadIndex + previousAvailable;
        size = writeIndex - readIndex;
    }

    /**
     * Truncates the buffer to the given write index and sets both 'available' and 'size' to the to the given value.
     * Subsequent behavior is undefined unless the values resulted from calling {@link #getWriteIndex()} and
     * {@link #available()} in immediate sequence, without any calls to 'read' or 'skip' since. It is the caller's
     * responsibility to ensure that calling this method will not result in loss of important data beyond the boundary.
     * @param previousWriteIndex the write index value to be set.
     * @param previousAvailable the available value to be set.
     */
    void truncate(final int previousWriteIndex, final int previousAvailable) {
        writeIndex = previousWriteIndex;
        available = previousAvailable;
        boundary = writeIndex;
        size = previousAvailable;
    }

    /**
     * Skips up to `n` buffered bytes. Less than `n` bytes will be skipped if less than `n` bytes are
     * available in the buffer.
     * @param n the number of bytes to skip.
     * @return the number of bytes actually skipped.
     */
    @Override
    public long skip(final long n) {
        if (n < 1 || available < 1) {
            return 0;
        }
        int bytesSkipped = (int) Math.min(available, n);
        readIndex += bytesSkipped;
        available -= bytesSkipped;
        size -= bytesSkipped;
        return bytesSkipped;
    }

    /**
     * {@inheritDoc}
     * <p>
     * NOTE: This method adheres to the documented behavior of {@link InputStream#available()}
     * <strong>except</strong> that it always returns the exact number of bytes that are available in the
     * buffer.
     * @return the exact number of bytes available in the buffer.
     */
    @Override
    public int available() {
        return available;
    }

    /**
     * @return the number of bytes actually buffered, which will be greater than or equal to 'available' if a boundary
     *   has been set, or equal to `available` if no boundary has been set.
     */
    int size() {
        return size;
    }

    /**
     * @return the number of bytes buffered beyond the boundary. This is equivalent to subtracting {@link #available()}
     * from {@link #size()}.
     */
    int availableBeyondBoundary() {
        return size - available;
    }

    /**
     * @return the index of the boundary, which is used to mark the last buffered byte that is available for reading.
     *   The boundary must always fall within [readIndex, writeIndex].
     */
    int getBoundary() {
        return boundary;
    }

    /**
     * Extends the boundary by the given number of bytes. It is the caller's responsibility to ensure that the
     * resulting boundary includes only bytes that have been buffered (i.e. that it does not exceed `writeIndex`).
     * @param numberOfBytes the number of bytes by which the boundary should be extended.
     */
    void extendBoundary(int numberOfBytes) {
        boundary += numberOfBytes;
        available += numberOfBytes;
    }

    /**
     * {@inheritDoc}
     * <p>
     * NOTE: This method adheres to the documented behavior of {@link InputStream#read(byte[], int, int)}
     * <strong>except</strong> that it never blocks. If a read is attempted before the first write,
     * this method will return -1.
     */
    @Override
    public int read() {
        if (available < 1) {
            return -1;
        }
        int b = buffer[readIndex];
        readIndex++;
        available--;
        size--;
        return b & SINGLE_BYTE_MASK;
    }

    /**
     * Peeks the byte at the given index without modifying any internal indexes. It is the caller's responsibility to
     * ensure that the given index points to an available byte.
     * @param index the index of the byte to peek.
     * @return the byte value.
     */
    int peek(int index) {
        return buffer[index] & SINGLE_BYTE_MASK;
    }

    /**
     * @return the capacity of the buffer, which is always `initialBufferSize` multiplied by the number of
     * times the buffer has grown.
     */
    public int capacity() {
        return capacity;
    }

    /**
     * @return the initial capacity of the buffer.
     */
    int getInitialBufferSize() {
        return initialBufferSize;
    }

    /**
     * Clears the buffer.
     */
    void clear() {
        readIndex = 0;
        writeIndex = 0;
        available = 0;
        boundary = 0;
        size = 0;
    }

    /**
     * Returns a ByteBuffer view of the underlying buffer.
     * @param position the start position of the ByteBuffer.
     * @param limit the limit of the ByteBuffer.
     * @return a ByteBuffer.
     */
    ByteBuffer getByteBuffer(int position, int limit) {
        // Setting the limit to the capacity first is required because setting the position will fail if the new
        // position is outside the limit.
        byteBuffer.limit(capacity);
        byteBuffer.position(position);
        byteBuffer.limit(limit);
        return byteBuffer;
    }

    /**
     * Copies bytes from the underlying buffer. It is the caller's responsibility to ensure the requested bytes
     * are available.
     * @param position the start position from which to read.
     * @param destination the buffer to copy into.
     * @param destinationOffset the offset of the buffer to copy into.
     * @param length the number of bytes to copy.
     */
    void get(int position, byte[] destination, int destinationOffset, int length) {
        System.arraycopy(buffer, position, destination, destinationOffset, length);
    }

    /**
     * Moves all bytes starting at 'fromPosition' to 'toPosition', overwriting the bytes in-between. It is the caller's
     * responsibility to ensure that the overwritten bytes are not needed.
     * @param fromPosition the position to move bytes from. Must be less than or equal to 'writeIndex' and 'boundary'.
     * @param toPosition the position to move bytes to. Must be greater than or equal to 'readIndex'.
     */
    void consolidate(int fromPosition, int toPosition) {
        if (fromPosition > writeIndex || fromPosition > boundary || toPosition < readIndex) {
            throw new IllegalArgumentException("Tried to consolidate using an index that violates the constraints.");
        }
        int indexShift = fromPosition - toPosition;
        System.arraycopy(buffer, fromPosition, buffer, toPosition, writeIndex - fromPosition);
        size -= indexShift;
        available -= indexShift;
        writeIndex -= indexShift;
        boundary -= indexShift;
        // readIndex does not need to change, because none of the consolidated bytes have been read yet.
    }

    /**
     * Registers the given NotificationConsumer.
     * @param consumer the NotificationConsumer to register.
     */
    void registerNotificationConsumer(NotificationConsumer consumer) {
        notificationConsumer = consumer;
    }
}
