package com.amazon.ion;

/**
 * Provides logic common to all BufferConfiguration implementations.
 * @param <T> the {@link BufferEventHandler} implementation applicable to the concrete
 *            ReaderLookaheadBufferBase subclass.
 * @param <U> the type of the concrete subclass of this BufferConfiguration that is applicable to the
 *            ReaderLookaheadBufferBase subclass.
 */
public abstract class BufferConfiguration<T extends BufferEventHandler, U extends BufferConfiguration<T, U>> {

    /**
     * Provides logic common to all BufferConfiguration Builder implementations.
     * @param <T> the type of {@link BufferEventHandler} used by the BufferConfiguration.
     * @param <U> the type of BufferConfiguration.
     * @param <V> the type of Builder that builds BufferConfiguration subclasses of type T.
     */
    public static abstract class Builder<
        T extends BufferEventHandler,
        U extends BufferConfiguration<T, U>,
        V extends Builder<T, U, V>
    > {

        /**
         * Large enough that most streams will never need to grow the buffer. NOTE: this only needs to be large
         * enough to exceed the length of the longest top-level value plus any system values that precede it.
         */
        static final int DEFAULT_INITIAL_BUFFER_SIZE = 32 * 1024; // bytes

        /**
         * The initial size of the lookahead buffer, in bytes.
         */
        private int initialBufferSize = DEFAULT_INITIAL_BUFFER_SIZE;

        /**
         * The maximum number of bytes that will be buffered.
         */
        private int maximumBufferSize = Integer.MAX_VALUE;

        /**
         * The handler that will be notified when the maximum buffer size is exceeded.
         */
        private T eventHandler = null;

        /**
         * Sets the initial size of the buffer that will be used to hold the data between top-level values. Default:
         * 64KB.
         *
         * @param initialBufferSizeInBytes the value.
         * @return this Builder.
         */
        public final V withInitialBufferSize(final int initialBufferSizeInBytes) {
            initialBufferSize = initialBufferSizeInBytes;
            return (V) this;
        }

        /**
         * @return the initial size of the lookahead buffer, in bytes.
         */
        public final int getInitialBufferSize() {
            return initialBufferSize;
        }

        /**
         * Sets the handler that will be notified when bytes are processed and when the maximum buffer size is exceeded
         * (if applicable). If the maximum buffer size is finite (see {@link #withMaximumBufferSize(int)}, the handler
         * is required to be non-null. Otherwise, the handler may be null, in which case the number of bytes processed
         * will not be reported.
         *
         * @param handler the handler.
         * @return this builder.
         */
        public final V withHandler(final T handler) {
            eventHandler = handler;
            return (V) this;
        }

        /**
         * @return the handler that will be notified when the maximum buffer size is exceeded.
         */
        public final T getHandler() {
            return eventHandler;
        }

        /**
         * Set the maximum number of bytes between top-level values. This can be used to limit growth of the internal
         * buffer. For binary Ion, the minimum value is 5 because all valid binary Ion data begins with a 4-byte Ion
         * version marker and the smallest value is 1 byte. For delimited text Ion, the minimum value is 2 because the
         * smallest text Ion value is 1 byte and the smallest delimiter is 1 byte. Default: Integer.MAX_VALUE.
         *
         * @param maximumBufferSizeInBytes the value.
         * @return this builder.
         */
        public final V withMaximumBufferSize(final int maximumBufferSizeInBytes) {
            maximumBufferSize = maximumBufferSizeInBytes;
            return (V) this;
        }

        /**
         * @return the maximum number of bytes that will be buffered.
         */
        public int getMaximumBufferSize() {
            return maximumBufferSize;
        }

        /**
         * Gets the minimum allowed maximum buffer size.
         * @return the value.
         */
        public abstract int getMinimumMaximumBufferSize();

        /**
         * @return the no-op {@link BufferEventHandler} for the type of BufferConfiguration that this Builder builds.
         */
        public abstract T getNoOpBufferEventHandler();

        /**
         * Creates a new BufferConfiguration from the Builder's current settings.
         * @return a new instance.
         */
        public abstract U build();
    }

    /**
     * The initial size of the lookahead buffer, in bytes.
     */
    private final int initialBufferSize;

    /**
     * The maximum number of bytes that will be buffered.
     */
    private final int maximumBufferSize;

    /**
     * The handler that will be notified when the maximum buffer size is exceeded.
     */
    private final T eventHandler;

    /**
     * Constructs an instance from the given Builder.
     * @param builder the builder containing the settings to apply to the new configuration.
     */
    protected BufferConfiguration(BufferConfiguration.Builder<T, U, ?> builder) {
        initialBufferSize = builder.getInitialBufferSize();
        maximumBufferSize = builder.getMaximumBufferSize();
        if (initialBufferSize > maximumBufferSize) {
            throw new IllegalArgumentException("Initial buffer size may not exceed the maximum buffer size.");
        }
        if (maximumBufferSize < builder.getMinimumMaximumBufferSize()) {
            throw new IllegalArgumentException(String.format(
                "Maximum buffer size must be at least %d bytes.", builder.getMinimumMaximumBufferSize()
            ));
        }
        if (builder.getHandler() == null) {
            if (maximumBufferSize < Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                    "Must specify an EventHandler when a maximum buffer size is specified."
                );
            }
            eventHandler = builder.getNoOpBufferEventHandler();
        } else {
            eventHandler = builder.getHandler();
        }
    }

    /**
     * @return the initial size of the lookahead buffer, in bytes.
     */
    public final int getInitialBufferSize() {
        return initialBufferSize;
    }

    /**
     * @return the maximum number of bytes that will be buffered.
     */
    public final int getMaximumBufferSize() {
        return maximumBufferSize;
    }

    /**
     * @return the handler that will be notified when the maximum buffer size is exceeded.
     */
    public final T getHandler() {
        return eventHandler;
    }
}
