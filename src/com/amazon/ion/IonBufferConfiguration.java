package com.amazon.ion;

/**
 * Configures Ion lookahead buffers.
 */
public final class IonBufferConfiguration extends BufferConfiguration<IonBufferEventHandler, IonBufferConfiguration> {

    /**
     * Builds IonBufferConfiguration instances.
     */
    public static final class Builder
        extends BufferConfiguration.Builder<IonBufferEventHandler, IonBufferConfiguration, Builder> {

        /**
         * 4-byte IVM + 1 byte user value.
         */
        private static final int MINIMUM_MAX_VALUE_SIZE = 5;

        /**
         * An IonBufferEventHandler that does nothing.
         */
        private static final IonBufferEventHandler NO_OP_EVENT_HANDLER = new IonBufferEventHandler() {

            @Override
            public void onOversizedSymbolTable() {
                // Do nothing.
            }

            @Override
            public void onOversizedValue() {
                // Do nothing.
            }

            @Override
            public void onData(int numberOfBytes) {
                // Do nothing.
            }
        };

        private Builder() {
            // Must be publicly instantiated via the factory method.
        }

        /**
         * Provides the standard builder with the default initial buffer size, an unbounded maximum buffer size, and
         * an event handler that does nothing.
         * @return a standard Builder.
         */
        public static Builder standard() {
            return new Builder();
        }

        @Override
        public int getMinimumMaximumBufferSize() {
            return MINIMUM_MAX_VALUE_SIZE;
        }

        @Override
        public IonBufferEventHandler getNoOpBufferEventHandler() {
            return NO_OP_EVENT_HANDLER;
        }

        @Override
        public IonBufferConfiguration build() {
            return new IonBufferConfiguration(this);
        }
    }

    /**
     * Constructs an instance from the given Builder.
     * @param builder the builder containing the settings to apply to the new configuration.
     */
    private IonBufferConfiguration(Builder builder) {
        super(builder);
    }
}
