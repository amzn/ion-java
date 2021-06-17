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

package com.amazon.ion.impl.bin;

import static com.amazon.ion.Decimal.isNegativeZero;
import static com.amazon.ion.IonType.BLOB;
import static com.amazon.ion.IonType.BOOL;
import static com.amazon.ion.IonType.CLOB;
import static com.amazon.ion.IonType.DECIMAL;
import static com.amazon.ion.IonType.FLOAT;
import static com.amazon.ion.IonType.INT;
import static com.amazon.ion.IonType.LIST;
import static com.amazon.ion.IonType.NULL;
import static com.amazon.ion.IonType.SEXP;
import static com.amazon.ion.IonType.STRING;
import static com.amazon.ion.IonType.STRUCT;
import static com.amazon.ion.IonType.SYMBOL;
import static com.amazon.ion.IonType.TIMESTAMP;
import static com.amazon.ion.IonType.isContainer;
import static com.amazon.ion.SystemSymbols.ION_1_0_SID;
import static com.amazon.ion.SystemSymbols.ION_SYMBOL_TABLE_SID;
import static com.amazon.ion.Timestamp.Precision.DAY;
import static com.amazon.ion.Timestamp.Precision.MINUTE;
import static com.amazon.ion.Timestamp.Precision.MONTH;
import static com.amazon.ion.Timestamp.Precision.SECOND;
import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Float.floatToRawIntBits;

import com.amazon.ion.IonCatalog;
import com.amazon.ion.IonException;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.SymbolTable;
import com.amazon.ion.SymbolToken;
import com.amazon.ion.Timestamp;
import com.amazon.ion.impl._Private_RecyclingStack;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Low-level binary {@link IonWriter} that understands encoding concerns but doesn't operate with any sense of symbol table management.
 */
@SuppressWarnings("deprecation")
/*package*/ final class IonRawBinaryWriter extends AbstractIonWriter implements _Private_IonRawWriter
{
    /** short-hand for array of bytes--useful for static definitions. */
    private static byte[] bytes(int... vals) {
        final byte[] octets = new byte[vals.length];

        for (int i = 0; i < vals.length; i++) {
            octets[i] = (byte) vals[i];
        }

        return octets;
    }

    private static final byte[] IVM = bytes(0xE0, 0x01, 0x00, 0xEA);

    private static final byte[] NULLS;
    static {
        final IonType[] types = IonType.values();
        NULLS = new byte[types.length];

        NULLS[NULL.ordinal()]           = (byte) 0x0F;
        NULLS[BOOL.ordinal()]           = (byte) 0x1F;
        NULLS[INT.ordinal()]            = (byte) 0x2F;
        NULLS[FLOAT.ordinal()]          = (byte) 0x4F;
        NULLS[DECIMAL.ordinal()]        = (byte) 0x5F;
        NULLS[TIMESTAMP.ordinal()]      = (byte) 0x6F;
        NULLS[SYMBOL.ordinal()]         = (byte) 0x7F;
        NULLS[STRING.ordinal()]         = (byte) 0x8F;
        NULLS[CLOB.ordinal()]           = (byte) 0x9F;
        NULLS[BLOB.ordinal()]           = (byte) 0xAF;
        NULLS[LIST.ordinal()]           = (byte) 0xBF;
        NULLS[SEXP.ordinal()]           = (byte) 0xCF;
        NULLS[STRUCT.ordinal()]         = (byte) 0xDF;
    }
    private static final byte NULL_NULL = NULLS[NULL.ordinal()];

    private static final byte BOOL_FALSE        = (byte) 0x10;
    private static final byte BOOL_TRUE         = (byte) 0x11;

    private static final byte INT_ZERO          = (byte) 0x20;

    private static final byte POS_INT_TYPE      = (byte) 0x20;
    private static final byte NEG_INT_TYPE      = (byte) 0x30;
    private static final byte FLOAT_TYPE        = (byte) 0x40;

    private static final byte DECIMAL_TYPE      = (byte) 0x50;
    private static final byte TIMESTAMP_TYPE    = (byte) 0x60;
    private static final byte SYMBOL_TYPE       = (byte) 0x70;
    private static final byte STRING_TYPE       = (byte) 0x80;

    private static final byte CLOB_TYPE         = (byte) 0x90;
    private static final byte BLOB_TYPE         = (byte) 0xA0;

    private static final byte DECIMAL_POS_ZERO               = (byte) 0x50;
    private static final byte DECIMAL_NEGATIVE_ZERO_MANTISSA = (byte) 0x80;

    private static final BigInteger BIG_INT_LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger BIG_INT_LONG_MIN_VALUE = BigInteger.valueOf(Long.MIN_VALUE);

    private static final byte VARINT_NEG_ZERO   = (byte) 0xC0;

    // See IonRawBinaryWriter#writeString(String) for usage information.
    static final int SMALL_STRING_SIZE = 4 * 1024;

    // Reusable resources for encoding Strings as UTF-8 bytes
    final CharsetEncoder utf8Encoder = Charset.forName("UTF-8").newEncoder();
    final ByteBuffer utf8EncodingBuffer = ByteBuffer.allocate((int) (SMALL_STRING_SIZE * utf8Encoder.maxBytesPerChar()));
    final char[] charArray = new char[SMALL_STRING_SIZE];
    final CharBuffer reusableCharBuffer = CharBuffer.wrap(charArray);

    private static final byte[] makeTypedPreallocatedBytes(final int typeDesc, final int length)
    {
        final byte[] bytes = new byte[length];
        bytes[0]          = (byte) typeDesc;
        if (length > 1)
        {
            bytes[length - 1] = (byte) 0x80;
        }
        return bytes;
    }

    private static byte[][] makeContainerTypedPreallocatedTable(final int length) {
        final IonType[] types = IonType.values();
        byte[][] extendedSizes = new byte[types.length][];


        extendedSizes[LIST.ordinal()]   = makeTypedPreallocatedBytes(0xBE, length);
        extendedSizes[SEXP.ordinal()]   = makeTypedPreallocatedBytes(0xCE, length);
        extendedSizes[STRUCT.ordinal()] = makeTypedPreallocatedBytes(0xDE, length);

        return extendedSizes;
    }

    /**
     * Determines how container/container-like values should be padded
     */
    /*package*/ enum PreallocationMode
    {
        /** Allocate no length.  (forces side patching) */
        PREALLOCATE_0(0x0000,   1)
        {
            @Override
            /*package*/ void patchLength(final WriteBuffer buffer, final long position, final long lengthValue)
            {
                throw new IllegalStateException("Cannot patch in PREALLOCATE 0 mode");
            }
        },

        /** Preallocate 1 byte of length. */
        PREALLOCATE_1(0x007F,   2)
        {
            @Override
            /*package*/ void patchLength(final WriteBuffer buffer, long position, long lengthValue)
            {
                buffer.writeVarUIntDirect1At(position, lengthValue);
            }
        },

        /** Preallocate 2 bytes of length. */
        PREALLOCATE_2(0x3FFF,   3)
        {
            @Override
            /*package*/ void patchLength(final WriteBuffer buffer, long position, long lengthValue)
            {
                buffer.writeVarUIntDirect2At(position, lengthValue);
            }
        }
        ;

        private final int       contentMaxLength;
        private final int       typedLength;
        private final byte[][]  containerTypedPreallocatedBytes;
        private final byte[]    annotationsTypedPreallocatedBytes;

        private PreallocationMode(final int contentMaxLength, final int typedLength)
        {
            this.contentMaxLength = contentMaxLength;
            this.typedLength = typedLength;
            this.containerTypedPreallocatedBytes   = makeContainerTypedPreallocatedTable(typedLength);
            this.annotationsTypedPreallocatedBytes = makeTypedPreallocatedBytes(0xEE, typedLength);
        }

        /*package*/ abstract void patchLength(final WriteBuffer buffer, final long position, final long length);

        /*package*/ static PreallocationMode withPadSize(final int pad)
        {
            switch (pad)
            {
                case 0:
                    return PreallocationMode.PREALLOCATE_0;
                case 1:
                    return PreallocationMode.PREALLOCATE_1;
                case 2:
                    return PreallocationMode.PREALLOCATE_2;
            }
            throw new IllegalArgumentException("No such preallocation mode for: " + pad);
        }
    }

    private static final byte STRING_TYPE_EXTENDED_LENGTH       = (byte) 0x8E;
    private static final byte[] STRING_TYPED_PREALLOCATED_2     = makeTypedPreallocatedBytes(0x8E, 2);
    private static final byte[] STRING_TYPED_PREALLOCATED_3     = makeTypedPreallocatedBytes(0x8E, 3);

    /** Max supported annotation length specifier size supported. */
    private static final int MAX_ANNOTATION_LENGTH = 0x7F;

    private enum ContainerType
    {
        SEQUENCE(true),
        STRUCT(true),
        VALUE(false),
        ANNOTATION(false);

        public final boolean allowedInStepOut;

        private ContainerType(final boolean allowedInStepOut)
        {
            this.allowedInStepOut = allowedInStepOut;
        }
    }

    private static class ContainerInfo
    {
        /** Whether or not the container is a struct */
        public ContainerType type;
        /** The location of the pre-allocated size descriptor in the buffer. */
        public long position;
        /** The size of the current value. */
        public long length;
        /** The patchlist for this container. */
        public PatchList patches;

        public ContainerInfo()
        {
            type = null;
            position = -1;
            length = -1;
            patches = null;
        }

        public void appendPatch(final PatchPoint patch)
        {
            if (patches == null)
            {
                patches = new PatchList();
            }
            patches.append(patch);
        }

        public void extendPatches(final PatchList newPatches)
        {
            if (patches == null)
            {
                patches = newPatches;
            }
            else
            {
                patches.extend(newPatches);
            }
        }

        public void initialize(final ContainerType type, final long offset) {
            this.type = type;
            this.position = offset;
            this.patches = null;
            this.length = 0;
        }

        @Override
        public String toString()
        {
            return "(CI " + type + " pos:" + position + " len:" + length + ")";
        }
    }

    private static class PatchPoint
    {
        /** position of the data being patched out. */
        public final long oldPosition;
        /** length of the data being patched out.*/
        public final int oldLength;
        /** position of the patch buffer where the length data is stored. */
        public final long patchPosition;
        /** length of the data to be patched in.*/
        public final int patchLength;

        public PatchPoint(final long oldPosition, final int oldLength, final long patchPosition, final int patchLength)
        {
            this.oldPosition = oldPosition;
            this.oldLength = oldLength;
            this.patchPosition = patchPosition;
            this.patchLength = patchLength;
        }

        @Override
        public String toString()
        {
            return "(PP old::(" + oldPosition + " " + oldLength + ") patch::(" + patchPosition + " " + patchLength + ")";
        }
    }

    /**
     * Simple singly linked list node that we can use to construct the patch list in the
     * right order incrementally in recursive segments.
     */
    private static class PatchList implements Iterable<PatchPoint>
    {
        private static class Node {
            public final PatchPoint value;
            public Node next;

            public Node(final PatchPoint value)
            {
                this.value = value;
            }
        }
        private Node head;
        private Node tail;

        public PatchList()
        {
            head = null;
            tail = null;
        }

        public boolean isEmpty()
        {
            return head == null && tail == null;
        }

        public void clear()
        {
            head = null;
            tail = null;
        }

        public void append(final PatchPoint patch)
        {
            final Node node = new Node(patch);
            if (head == null)
            {
                head = node;
                tail = node;
            }
            else
            {
                tail.next = node;
                tail = node;
            }
        }

        public void extend(final PatchList end)
        {
            if (end != null)
            {
                if (head == null)
                {
                    if (end.head != null)
                    {
                        head = end.head;
                        tail = end.tail;
                    }
                }
                else
                {
                    tail.next = end.head;
                    tail = end.tail;
                }
            }
        }

        public PatchPoint truncate(final long oldPosition)
        {
            Node prev = null;
            Node curr = head;
            while (curr != null)
            {
                final PatchPoint patch = curr.value;
                if (patch.oldPosition >= oldPosition)
                {
                    tail = prev;
                    if (tail == null)
                    {
                        head = null;
                    }
                    else
                    {
                        tail.next = null;
                    }
                    return patch;
                }

                prev = curr;
                curr = curr.next;
            }
            return null;
        }

        public Iterator<PatchPoint> iterator()
        {
            return new Iterator<PatchPoint>()
            {
                Node curr = head;

                public boolean hasNext()
                {
                    return curr != null;
                }

                public PatchPoint next()
                {
                    if (!hasNext())
                    {
                        throw new NoSuchElementException();
                    }
                    final PatchPoint value = curr.value;
                    curr = curr.next;
                    return value;
                }

                public void remove()
                {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public String toString()
        {
            final StringBuilder buf = new StringBuilder();
            buf.append("(PATCHES");
            for (final PatchPoint patch : this)
            {
                buf.append(" ");
                buf.append(patch);
            }
            buf.append(")");
            return buf.toString();
        }
    }

    /*package*/ enum StreamCloseMode
    {
        NO_CLOSE,
        CLOSE
    }

    /*package*/ enum StreamFlushMode
    {
        NO_FLUSH,
        FLUSH
    }

    private static final int SID_UNASSIGNED = -1;

    private final BlockAllocator                allocator;
    private final OutputStream                  out;
    private final StreamCloseMode               streamCloseMode;
    private final StreamFlushMode               streamFlushMode;
    private final PreallocationMode             preallocationMode;
    private final boolean                       isFloatBinary32Enabled;
    private final WriteBuffer                   buffer;
    private final WriteBuffer                   patchBuffer;
    private final PatchList                     patchPoints;
    private final _Private_RecyclingStack<ContainerInfo> containers;
    private int                                 depth;
    private boolean                             hasWrittenValuesSinceFinished;
    private boolean                             hasWrittenValuesSinceConstructed;

    private int                     currentFieldSid;
    private final List<Integer>     currentAnnotationSids;
    // XXX this is for managed detection of TLV that is a LST--this is easier to track here than at the managed level
    private boolean                     hasTopLevelSymbolTableAnnotation;

    private boolean                     closed;

    /*package*/ IonRawBinaryWriter(final BlockAllocatorProvider provider,
                                   final int blockSize,
                                   final OutputStream out,
                                   final WriteValueOptimization optimization,
                                   final StreamCloseMode streamCloseMode,
                                   final StreamFlushMode streamFlushMode,
                                   final PreallocationMode preallocationMode,
                                   final boolean isFloatBinary32Enabled)
                                   throws IOException
    {
        super(optimization);

        if (out == null) { throw new NullPointerException(); }

        this.allocator         = provider.vendAllocator(blockSize);
        this.out               = out;
        this.streamCloseMode   = streamCloseMode;
        this.streamFlushMode   = streamFlushMode;
        this.preallocationMode = preallocationMode;
        this.isFloatBinary32Enabled = isFloatBinary32Enabled;
        this.buffer            = new WriteBuffer(allocator);
        this.patchBuffer       = new WriteBuffer(allocator);
        this.patchPoints       = new PatchList();
        this.containers        = new _Private_RecyclingStack<ContainerInfo>(
            10,
            new _Private_RecyclingStack.ElementFactory<ContainerInfo>() {
                public ContainerInfo newElement() {
                    return new ContainerInfo();
                }
            }
        );
        this.depth                            = 0;
        this.hasWrittenValuesSinceFinished    = false;
        this.hasWrittenValuesSinceConstructed = false;

        this.currentFieldSid                  = SID_UNASSIGNED;
        this.currentAnnotationSids            = new ArrayList<Integer>();
        this.hasTopLevelSymbolTableAnnotation = false;

        this.closed = false;
    }

    /** Always returns {@link Symbols#systemSymbolTable()}. */
    public SymbolTable getSymbolTable()
    {
        return Symbols.systemSymbolTable();
    }

    // Current Value Meta

    public void setFieldName(final String name)
    {
        throw new UnsupportedOperationException("Cannot set field name on a low-level binary writer via string");
    }

    public void setFieldNameSymbol(final SymbolToken name)
    {
        setFieldNameSymbol(name.getSid());
    }

    public void setFieldNameSymbol(int sid)
    {
        if (!isInStruct())
        {
            throw new IonException("Cannot set field name outside of struct context");
        }
        currentFieldSid = sid;
    }

    public void setTypeAnnotations(final String... annotations)
    {
        throw new UnsupportedOperationException("Cannot set annotations on a low-level binary writer via string");
    }

    private void clearAnnotations()
    {
        currentAnnotationSids.clear();
        hasTopLevelSymbolTableAnnotation = false;
    }

    public void setTypeAnnotationSymbols(final SymbolToken... annotations)
    {
        clearAnnotations();
        if (annotations != null)
        {
            for (final SymbolToken annotation : annotations)
            {
                addTypeAnnotationSymbol(annotation.getSid());
            }
        }
    }

    public void setTypeAnnotationSymbols(int... sids)
    {
        clearAnnotations();
        if (sids != null)
        {
            for (final int sid : sids)
            {
                addTypeAnnotationSymbol(sid);
            }
        }
    }

    public void addTypeAnnotation(final String annotation)
    {
        throw new UnsupportedOperationException("Cannot add annotations on a low-level binary writer via string");
    }

    // Additional Current State Meta

    /*package*/ void addTypeAnnotationSymbol(final SymbolToken annotation)
    {
        addTypeAnnotationSymbol(annotation.getSid());

    }

    public void addTypeAnnotationSymbol(int sid)
    {
        if (depth == 0 && sid == ION_SYMBOL_TABLE_SID)
        {
            hasTopLevelSymbolTableAnnotation = true;
        }
        currentAnnotationSids.add(sid);
    }

    /*package*/ boolean hasAnnotations()
    {
        return !currentAnnotationSids.isEmpty();
    }

    /** Returns true if a value has been written since construction or {@link #finish()}. */
    /*package*/ boolean hasWrittenValuesSinceFinished()
    {
        return hasWrittenValuesSinceFinished;
    }

    /** Returns true if a value has been written since the writer was constructed. */
    /*package*/ boolean hasWrittenValuesSinceConstructed()
    {
        return hasWrittenValuesSinceConstructed;
    }

    /*package*/ boolean hasTopLevelSymbolTableAnnotation()
    {
        return hasTopLevelSymbolTableAnnotation;
    }

    /*package*/ int getFieldId()
    {
        return currentFieldSid;
    }

    // Compatibility with Implementation Writer Interface

    public IonCatalog getCatalog()
    {
        throw new UnsupportedOperationException();
    }

    public boolean isFieldNameSet()
    {
        return currentFieldSid > SID_UNASSIGNED;
    }

    public void writeIonVersionMarker() throws IOException
    {
        buffer.writeBytes(IVM);
    }

    public int getDepth()
    {
        return depth;
    }

    // Low-Level Writing

    private void updateLength(long length)
    {
        if (containers.isEmpty())
        {
            return;
        }

        containers.peek().length += length;
    }

    private void pushContainer(final ContainerType type)
    {
        // XXX we push before writing the type of container
        containers.push().initialize(type, buffer.position() + 1);
    }

    private void addPatchPoint(final long position, final int oldLength, final long value)
    {
        // record the size in a patch buffer
        final long patchPosition = patchBuffer.position();
        final int patchLength = patchBuffer.writeVarUInt(value);
        final PatchPoint patch = new PatchPoint(position, oldLength, patchPosition, patchLength);
        if (containers.isEmpty())
        {
            // not nested, just append to the root list
            patchPoints.append(patch);
        }
        else
        {
            // nested, apply it to the current container
            containers.peek().appendPatch(patch);
        }
        updateLength(patchLength - oldLength);
    }

    private void extendPatchPoints(final PatchList patches)
    {
        if (containers.isEmpty())
        {
            // not nested, extend root list
            patchPoints.extend(patches);
        }
        else
        {
            // nested, apply it to the current container
            containers.peek().extendPatches(patches);
        }
    }

    private ContainerInfo popContainer()
    {
        final ContainerInfo current = containers.pop();
        if (current == null)
        {
            throw new IllegalStateException("Tried to pop container state without said container");
        }

        // only patch for real containers and annotations -- we use VALUE for tracking only
        final long length = current.length;
        if (current.type != ContainerType.VALUE)
        {
            // patch in the length
            final long position = current.position;
            if (current.length <= preallocationMode.contentMaxLength && preallocationMode != PreallocationMode.PREALLOCATE_0)
            {
                preallocationMode.patchLength(buffer, position, length);
            }
            else
            {
                // side patch
                if (current.length <= 0xD && preallocationMode == PreallocationMode.PREALLOCATE_0)
                {
                    // XXX if we're not using padding we can get here and optimize the length a little without side patching!
                    final long typePosition = position - 1;
                    final long type = (buffer.getUInt8At(typePosition) & 0xF0) | current.length;
                    buffer.writeUInt8At(typePosition, type);
                }
                else
                {
                    addPatchPoint(position, preallocationMode.typedLength - 1, length);
                }
            }
        }
        if (current.patches != null)
        {
            // at this point, we've appended our patch points upward, lets make sure we get
            // our child patch points in
            extendPatchPoints(current.patches);
        }

        // make sure to record length upward
        updateLength(length);
        return current;
    }

    private void writeVarUInt(final long value)
    {
        if (value < 0)
        {
            throw new IonException("Cannot write negative value as unsigned");
        }
        final int len = buffer.writeVarUInt(value);
        updateLength(len);
    }

    private void writeVarInt(final long value)
    {
        final int len = buffer.writeVarInt(value);
        updateLength(len);
    }

    private static void checkSid(int sid)
    {
        if (sid < 0)
        {
            throw new IllegalArgumentException("Invalid symbol with SID: " + sid);
        }
    }

    /** prepare to write values with field name and annotations. */
    private void prepareValue()
    {
        if (isInStruct() && currentFieldSid <= SID_UNASSIGNED)
        {
            throw new IllegalStateException("IonWriter.setFieldName() must be called before writing a value into a struct.");
        }
        if (currentFieldSid > SID_UNASSIGNED)
        {
            checkSid(currentFieldSid);
            writeVarUInt(currentFieldSid);

            // clear out field name
            currentFieldSid = SID_UNASSIGNED;
        }
        if (!currentAnnotationSids.isEmpty())
        {
            // we have to push a container context for annotations
            updateLength(preallocationMode.typedLength);
            pushContainer(ContainerType.ANNOTATION);
            buffer.writeBytes(preallocationMode.annotationsTypedPreallocatedBytes);

            final long annotationsLengthPosition = buffer.position();
            buffer.writeVarUInt(0L);
            int annotationsLength = 0;
            for (final int symbol : currentAnnotationSids)
            {
                checkSid(symbol);
                final int symbolLength = buffer.writeVarUInt(symbol);
                annotationsLength += symbolLength;
            }
            if (annotationsLength > MAX_ANNOTATION_LENGTH)
            {
                // TODO deal with side patching if we want to support > 32 4-byte symbols annotations... seems excessive
                throw new IonException("Annotations too large: " + currentAnnotationSids);
            }

            // update the annotations size
            updateLength(/*length specifier*/ 1 + annotationsLength);
            // patch the annotations length
            buffer.writeVarUIntDirect1At(annotationsLengthPosition, annotationsLength);

            // clear out annotations
            currentAnnotationSids.clear();
            hasTopLevelSymbolTableAnnotation = false;
        }
    }

    /** Closes out annotations. */
    private void finishValue()
    {
        if (!containers.isEmpty() && containers.peek().type == ContainerType.ANNOTATION)
        {
            // close out and patch the length
            popContainer();
        }
        hasWrittenValuesSinceFinished = true;
        hasWrittenValuesSinceConstructed = true;
    }

    // Container Manipulation

    public void stepIn(final IonType containerType) throws IOException
    {
        if (!isContainer(containerType))
        {
            throw new IonException("Cannot step into " + containerType);
        }
        prepareValue();
        updateLength(preallocationMode.typedLength);
        pushContainer(containerType == STRUCT ? ContainerType.STRUCT : ContainerType.SEQUENCE);
        depth++;
        buffer.writeBytes(preallocationMode.containerTypedPreallocatedBytes[containerType.ordinal()]);
    }

    public void stepOut() throws IOException
    {
        if (currentFieldSid > SID_UNASSIGNED)
        {
            throw new IonException("Cannot step out with field name set");
        }
        if (!currentAnnotationSids.isEmpty())
        {
            throw new IonException("Cannot step out with field name set");
        }
        if (containers.isEmpty() || !containers.peek().type.allowedInStepOut)
        {
            throw new IonException("Cannot step out when not in container");
        }
        // close out the container
        popContainer();
        depth--;
        // close out the annotations if any
        finishValue();
    }

    public boolean isInStruct()
    {
        return !containers.isEmpty() && containers.peek().type == ContainerType.STRUCT;
    }

    // Write Value Methods

    public void writeNull() throws IOException
    {
        prepareValue();
        updateLength(1);
        buffer.writeByte(NULL_NULL);
        finishValue();
    }

    public void writeNull(final IonType type) throws IOException
    {
        byte data = NULL_NULL;
        if (type != null)
        {
            data = NULLS[type.ordinal()];
            if (data == 0)
            {
                throw new IllegalArgumentException("Cannot write a null for: " + type);
            }
        }

        prepareValue();
        updateLength(1);
        buffer.writeByte(data);
        finishValue();
    }

    public void writeBool(final boolean value) throws IOException
    {
        prepareValue();
        updateLength(1);
        if (value)
        {
            buffer.writeByte(BOOL_TRUE);
        }
        else
        {
            buffer.writeByte(BOOL_FALSE);
        }
        finishValue();
    }

    /**
     * Writes a type descriptor followed by unsigned integer value.
     * Does not check for sign.
     * Note that this does not do {@link #prepareValue()} or {@link #finishValue()}.
     */
    private void writeTypedUInt(final int type, final long value)
    {
        if (value <= 0xFFL)
        {
            updateLength(2);
            buffer.writeUInt8(type | 0x01);
            buffer.writeUInt8(value);
        }
        else if (value <= 0xFFFFL)
        {
            updateLength(3);
            buffer.writeUInt8(type | 0x02);
            buffer.writeUInt16(value);
        }
        else if (value <= 0xFFFFFFL)
        {
            updateLength(4);
            buffer.writeUInt8(type | 0x03);
            buffer.writeUInt24(value);
        }
        else if (value <= 0xFFFFFFFFL)
        {
            updateLength(5);
            buffer.writeUInt8(type | 0x04);
            buffer.writeUInt32(value);
        }
        else if (value <= 0xFFFFFFFFFFL)
        {
            updateLength(6);
            buffer.writeUInt8(type | 0x05);
            buffer.writeUInt40(value);
        }
        else if (value <= 0xFFFFFFFFFFFFL)
        {
            updateLength(7);
            buffer.writeUInt8(type | 0x06);
            buffer.writeUInt48(value);
        }
        else if (value <= 0xFFFFFFFFFFFFFFL)
        {
            updateLength(8);
            buffer.writeUInt8(type | 0x07);
            buffer.writeUInt56(value);
        }
        else
        {
            updateLength(9);
            buffer.writeUInt8(type | 0x08);
            buffer.writeUInt64(value);
        }
    }

    public void writeInt(long value) throws IOException
    {
        prepareValue();
        if (value == 0)
        {
            updateLength(1);
            buffer.writeByte(INT_ZERO);
        }
        else
        {
            int type = POS_INT_TYPE;
            if (value < 0)
            {
                type = NEG_INT_TYPE;
                if (value == Long.MIN_VALUE)
                {
                    // XXX special case for min_value which will not play nice with signed arithmetic and fit into the positive space
                    // XXX we keep 2's complement of Long.MIN_VALUE because it encodes to unsigned 2 ** 63 (0x8000000000000000L)
                    // XXX WriteBuffer.writeUInt64() never looks at sign
                    updateLength(9);
                    buffer.writeUInt8(NEG_INT_TYPE | 0x8);
                    buffer.writeUInt64(value);
                }
                else
                {
                    // get the magnitude, sign is already encoded
                    value = -value;
                    writeTypedUInt(type, value);
                }
            }
            else
            {
                writeTypedUInt(type, value);
            }
        }
        finishValue();
    }

    /** Write a raw byte array as some type. Note that this does not do {@link #prepareValue()}. */
    private void writeTypedBytes(final int type, final byte[] data, final int offset, final int length)
    {
        int totalLength = 1 + length;
        if (length < 14)
        {
            buffer.writeUInt8(type | length);
        }
        else
        {
            // need to specify length explicitly
            buffer.writeUInt8(type | 0xE);
            final int sizeLength = buffer.writeVarUInt(length);
            totalLength += sizeLength;
        }
        updateLength(totalLength);
        buffer.writeBytes(data, offset, length);
    }

    public void writeInt(BigInteger value) throws IOException
    {
        if (value == null)
        {
            writeNull(IonType.INT);
            return;
        }
        if (value.compareTo(BIG_INT_LONG_MIN_VALUE) >= 0 && value.compareTo(BIG_INT_LONG_MAX_VALUE) <= 0)
        {
            // for the small stuff, just write it as a signed int64
            writeInt(value.longValue());
            return;
        }

        prepareValue();

        int type = POS_INT_TYPE;
        if(value.signum() < 0)
        {
            type = NEG_INT_TYPE;
            value = value.negate();
        }

        // generate big-endian representation of the positive value
        final byte[] magnitude = value.toByteArray();
        writeTypedBytes(type, magnitude, 0, magnitude.length);

        finishValue();
    }

    public void writeFloat(final double value) throws IOException
    {
        prepareValue();

        if (isFloatBinary32Enabled && value == ((double) ((float) value))) {
            updateLength(5);
            buffer.writeUInt8(FLOAT_TYPE | 4);
            buffer.writeUInt32(floatToRawIntBits((float) value));
        } else {
            updateLength(9);
            buffer.writeUInt8(FLOAT_TYPE | 8);
            buffer.writeUInt64(doubleToRawLongBits(value));
        }

        finishValue();
    }

    /** Encodes a decimal, updating the current container length context (which is probably a Decimal/Timestamp). */
    private void writeDecimalValue(final BigDecimal value)
    {
        final boolean isNegZero = isNegativeZero(value);
        final int signum = value.signum();
        final int exponent = -value.scale();

        writeVarInt(exponent);

        final BigInteger mantissaBigInt = value.unscaledValue();
        if (mantissaBigInt.compareTo(BIG_INT_LONG_MIN_VALUE) >= 0 && mantissaBigInt.compareTo(BIG_INT_LONG_MAX_VALUE) <= 0)
        {
            // we can fit into the long space
            final long mantissa = mantissaBigInt.longValue();
            if (signum == 0 && !isNegZero)
            {
                // positive zero does not need to be encoded
            }
            else if (isNegZero)
            {
                // XXX special case for negative zero, we have to encode as a signed zero in the Int format
                updateLength(1);
                buffer.writeByte(DECIMAL_NEGATIVE_ZERO_MANTISSA);
            }
            else if (mantissa == Long.MIN_VALUE)
            {
                // XXX special case for min value -- we need 64-bits to store the magnitude and we need a bit for sign
                updateLength(9);
                buffer.writeUInt8(0x80);
                buffer.writeUInt64(mantissa);
            }
            else if (mantissa >= 0xFFFFFFFFFFFFFF81L && mantissa <= 0x000000000000007FL)
            {
                updateLength(1);
                buffer.writeInt8(mantissa);
            }
            else if (mantissa >= 0xFFFFFFFFFFFF8001L && mantissa <= 0x0000000000007FFFL)
            {
                updateLength(2);
                buffer.writeInt16(mantissa);
            }
            else if (mantissa >= 0xFFFFFFFFFF800001L && mantissa <= 0x00000000007FFFFFL)
            {
                updateLength(3);
                buffer.writeInt24(mantissa);
            }
            else if (mantissa >= 0xFFFFFFFF80000001L && mantissa <= 0x000000007FFFFFFFL)
            {
                updateLength(4);
                buffer.writeInt32(mantissa);
            }
            else if (mantissa >= 0xFFFFFF8000000001L && mantissa <= 0x0000007FFFFFFFFFL)
            {
                updateLength(5);
                buffer.writeInt40(mantissa);
            }
            else if (mantissa >= 0xFFFF800000000001L && mantissa <= 0x00007FFFFFFFFFFFL)
            {
                updateLength(6);
                buffer.writeInt48(mantissa);
            }
            else if (mantissa >= 0xFF80000000000001L && mantissa <= 0x007FFFFFFFFFFFFFL)
            {
                updateLength(7);
                buffer.writeInt56(mantissa);
            }
            else
            {
                // TODO consider being more space efficient for integers that can be written with 6/7 bytes.
                updateLength(8);
                buffer.writeInt64(mantissa);
            }
        }
        else
        {
            final BigInteger magnitude = signum > 0 ? mantissaBigInt : mantissaBigInt.negate();
            final byte[] bits = magnitude.toByteArray();
            if (signum < 0)
            {
                if ((bits[0] & 0x80) == 0)
                {
                    bits[0] |= 0x80;
                }
                else
                {
                    // not enough space in the bits to store the negative sign
                    updateLength(1);
                    buffer.writeUInt8(0x80);
                }
            }
            updateLength(bits.length);
            buffer.writeBytes(bits);
        }
    }

    private void patchSingleByteTypedOptimisticValue(final byte type, final ContainerInfo info)
    {
        if (info.length <= 0xD)
        {
            // we fit -- overwrite the type byte
            buffer.writeUInt8At(info.position - 1, type | info.length);
        }
        else
        {
            // side patch
            buffer.writeUInt8At(info.position - 1, type | 0xE);
            addPatchPoint(info.position, 0, info.length);
        }
    }

    public void writeDecimal(final BigDecimal value) throws IOException
    {
        if (value == null)
        {
            writeNull(IonType.DECIMAL);
            return;
        }

        prepareValue();

        if (value.signum() == 0 && value.scale() == 0 && !isNegativeZero(value))
        {
            // 0d0 can be written in one byte
            updateLength(1);
            buffer.writeUInt8(DECIMAL_POS_ZERO);
        }
        else
        {
            // optimistically try to fit decimal length in low nibble (most should)
            updateLength(1);
            pushContainer(ContainerType.VALUE);
            buffer.writeByte(DECIMAL_TYPE);
            writeDecimalValue(value);
            final ContainerInfo info = popContainer();
            patchSingleByteTypedOptimisticValue(DECIMAL_TYPE, info);
        }

        finishValue();
    }

    public void writeTimestamp(final Timestamp value) throws IOException
    {
        if (value == null)
        {
            writeNull(IonType.TIMESTAMP);
            return;
        }
        prepareValue();

        // optimistically try to fit a timestamp length in low nibble (most should)
        updateLength(1);
        pushContainer(ContainerType.VALUE);
        buffer.writeByte(TIMESTAMP_TYPE);

        // OFFSET
        final Integer offset = value.getLocalOffset();
        if (offset == null)
        {
            // special case for unknown -00:00
            updateLength(1);
            buffer.writeByte(VARINT_NEG_ZERO);
        }
        else
        {
            writeVarInt(offset.intValue());
        }

        // YEAR
        final int year = value.getZYear();
        writeVarUInt(year);

        // XXX it is really convenient to rely on the ordinal
        final int precision = value.getPrecision().ordinal();

        if (precision >= MONTH.ordinal())
        {
            final int month = value.getZMonth();
            writeVarUInt(month);
        }
        if (precision >= DAY.ordinal())
        {
            final int day = value.getZDay();
            writeVarUInt(day);
        }
        if (precision >= MINUTE.ordinal())
        {
            final int hour = value.getZHour();
            writeVarUInt(hour);
            final int minute = value.getZMinute();
            writeVarUInt(minute);
        }
        if (precision >= SECOND.ordinal())
        {
            final int second = value.getZSecond();
            writeVarUInt(second);
            final BigDecimal fraction = value.getZFractionalSecond();
            if (fraction != null) {
                final BigInteger mantissaBigInt = fraction.unscaledValue();
                final int exponent = -fraction.scale();
                if (!(mantissaBigInt.equals(BigInteger.ZERO) && exponent > -1)) {
                    writeDecimalValue(fraction);
                }
            }
        }

        final ContainerInfo info = popContainer();
        patchSingleByteTypedOptimisticValue(TIMESTAMP_TYPE, info);

        finishValue();
    }

    public void writeSymbol(String content) throws IOException
    {
        throw new UnsupportedOperationException("Symbol writing via string is not supported in low-level binary writer");
    }

    public void writeSymbolToken(final SymbolToken content) throws IOException
    {
        if (content == null)
        {
            writeNull(IonType.SYMBOL);
            return;
        }
        writeSymbolToken(content.getSid());
    }

    boolean isIVM(int sid)
    {
        // When SID 2 occurs at the top level with no annotations, it has the
        // special properties of an IVM. Otherwise, it's considered a normal
        // symbol value.
        // TODO amzn/ion-java/issues/88 requires this behavior to be changed,
        // such that top-level SID 2 is treated as a symbol value, not an IVM.
        return depth == 0 && sid == ION_1_0_SID && !hasAnnotations();
    }

    public void writeSymbolToken(int sid) throws IOException
    {
        if (isIVM(sid))
        {
            throw new IonException("Direct writing of IVM is not supported in low-level binary writer");
        }
        checkSid(sid);
        prepareValue();
        writeTypedUInt(SYMBOL_TYPE, sid);
        finishValue();
    }

    public void writeString(final String value) throws IOException
    {
        if (value == null)
        {
            writeNull(IonType.STRING);
            return;
        }
        prepareValue();

        /*
         This method relies on the standard CharsetEncoder class to encode each String's UTF-16 char[] data into
         UTF-8 bytes. Strangely, CharsetEncoders cannot operate directly on instances of a String. The CharsetEncoder
         API requires all inputs and outputs to be specified as instances of java.nio.ByteBuffer and
         java.nio.CharBuffer, making some number of allocations mandatory. Specifically, for each encoding operation
         we need to have:

            1. An instance of a UTF-8 CharsetEncoder.
            2. A CharBuffer representation of the String's data.
            3. A ByteBuffer into which the CharsetEncoder may write UTF-8 bytes.

         To minimize the overhead involved, the IonRawBinaryWriter will reuse previously initialized resources wherever
         possible. However, because CharBuffer and ByteBuffer each have a fixed length, we can only reuse them for
         Strings that are small enough to fit. This creates two kinds of input String to encode: those that are small
         enough for us to reuse our buffers ("small strings"), and those which are not ("large strings").

         The String#getBytes(Charset) method cannot be used for two reasons:

               1. It always allocates, so we cannot reuse any resources.
               2. If/when it encounters character data that cannot be encoded as UTF-8, it simply replaces that data
                 with a substitute character[1]. (Sometimes seen in applications as a '?'.) In order
                 to surface invalid data to the user, the method must be able to detect these events at encoding time.

            [1] https://en.wikipedia.org/wiki/Substitute_character
        */

        CharBuffer stringData;
        ByteBuffer encodingBuffer;

        int length = value.length();

        // While it is possible to encode the Ion string using a fixed-size encodingBuffer, we need to be able to
        // write the length of the complete UTF-8 string to the output stream before we write the string itself.
        // For simplicity, we reuse or create an encodingBuffer that is large enough to hold the full string.

        // In order to encode the input String, we need to pass it to CharsetEncoder as an implementation of CharBuffer.
        // Surprisingly, the intuitive way to achieve this (the CharBuffer#wrap(CharSequence) method) adds a large
        // amount of CPU overhead to the encoding process. Benchmarking shows that it's substantially faster
        // to use String#getChars(int, int, char[], int) to copy the String's backing array and then call
        // CharBuffer#wrap(char[]) on the copy.

        if (length > SMALL_STRING_SIZE) {
            // Allocate a new buffer for large strings
            encodingBuffer = ByteBuffer.allocate((int) (value.length() * utf8Encoder.maxBytesPerChar()));
            char[] chars = new char[value.length()];
            value.getChars(0, value.length(), chars, 0);
            stringData = CharBuffer.wrap(chars);
        } else {
            // Reuse our existing buffers for small strings
            encodingBuffer = utf8EncodingBuffer;
            encodingBuffer.clear();
            stringData = reusableCharBuffer;
            value.getChars(0, value.length(), charArray, 0);
            reusableCharBuffer.rewind();
            reusableCharBuffer.limit(value.length());
        }

        // Because encodingBuffer is guaranteed to be large enough to hold the encoded string, we can
        // perform the encoding in a single call to CharsetEncoder#encode(CharBuffer, ByteBuffer, boolean).
        CoderResult coderResult = utf8Encoder.encode(stringData, encodingBuffer, true);

        // 'Underflow' is the success state of a CoderResult.
        if (!coderResult.isUnderflow()) {
            throw new IllegalArgumentException("Could not encode string as UTF8 bytes: " + value);
        }
        encodingBuffer.flip();
        int utf8Length = encodingBuffer.remaining();

        // Write the type and length codes to the output stream.
        long previousPosition = buffer.position();
        if (utf8Length <= 0xD) {
            buffer.writeUInt8(STRING_TYPE | utf8Length);
        } else {
            buffer.writeUInt8(STRING_TYPE | 0xE);
            buffer.writeVarUInt(utf8Length);
        }

        // Write the encoded UTF-8 bytes to the output stream
        buffer.writeBytes(encodingBuffer.array(), 0, utf8Length);

        long bytesWritten = buffer.position() - previousPosition;
        updateLength(bytesWritten);

        finishValue();
    }

    public void writeClob(byte[] data) throws IOException
    {
        if (data == null)
        {
            writeNull(IonType.CLOB);
            return;
        }
        writeClob(data, 0, data.length);
    }

    public void writeClob(final byte[] data, final int offset, final int length) throws IOException
    {
        if (data == null)
        {
            writeNull(IonType.CLOB);
            return;
        }
        prepareValue();
        writeTypedBytes(CLOB_TYPE, data, offset, length);
        finishValue();
    }

    public void writeBlob(byte[] data) throws IOException
    {
        if (data == null)
        {
            writeNull(IonType.BLOB);
            return;
        }
        writeBlob(data, 0, data.length);
    }

    public void writeBlob(final byte[] data, final int offset, final int length) throws IOException
    {
        if (data == null)
        {
            writeNull(IonType.BLOB);
            return;
        }
        prepareValue();
        writeTypedBytes(BLOB_TYPE, data, offset, length);
        finishValue();
    }

    @Override
    public void writeString(byte[] data, int offset, int length) throws IOException
    {
        if (data == null)
        {
            writeNull(IonType.STRING);
            return;
        }
        prepareValue();
        writeTypedBytes(STRING_TYPE, data, offset, length);
        finishValue();
    }

    /**
     * Writes a raw value into the buffer, updating lengths appropriately.
     * <p>
     * The implication here is that the caller is dumping some valid Ion payload with the correct context.
     */
    public void writeBytes(byte[] data, int offset, int length) throws IOException
    {
        prepareValue();
        updateLength(length);
        buffer.writeBytes(data, offset, length);
        finishValue();
    }

    // Stream Manipulation/Terminators

    /*package*/ long position()
    {
        return buffer.position();
    }

    /*package*/ void truncate(long position)
    {
        buffer.truncate(position);
        // TODO decide if it is worth making this faster than O(N)
        final PatchPoint patch = patchPoints.truncate(position);
        if (patch != null)
        {
            patchBuffer.truncate(patch.patchPosition);
        }
    }

    public void flush() throws IOException {}

    public void finish() throws IOException
    {
        if (closed)
        {
            return;
        }
        if (!containers.isEmpty() || depth > 0)
        {
            throw new IllegalStateException("Cannot finish within container: " + containers);
        }

        if (patchPoints.isEmpty())
        {
            // nothing to patch--write 'em out!
            buffer.writeTo(out);
        }
        else
        {
            long bufferPosition = 0;
            for (final PatchPoint patch : patchPoints)
            {
                // write up to the thing to be patched
                final long bufferLength = patch.oldPosition - bufferPosition;
                buffer.writeTo(out, bufferPosition, bufferLength);

                // write out the patch
                patchBuffer.writeTo(out, patch.patchPosition, patch.patchLength);

                // skip over the preallocated varuint field
                bufferPosition = patch.oldPosition;
                bufferPosition += patch.oldLength;
            }
            buffer.writeTo(out, bufferPosition, buffer.position() - bufferPosition);
        }
        patchPoints.clear();
        patchBuffer.reset();
        buffer.reset();

        if (streamFlushMode == StreamFlushMode.FLUSH)
        {
            out.flush();
        }

        hasWrittenValuesSinceFinished = false;
    }

    public void close() throws IOException
    {
        if (closed)
        {
            return;
        }
        try
        {
            try
            {
                finish();
            }
            catch (final IllegalStateException e)
            {
                // callers don't expect this...
            }

            // release all of our blocks -- these should never throw
            buffer.close();
            patchBuffer.close();
            allocator.close();
        }
        finally
        {
            closed = true;
            if (streamCloseMode == StreamCloseMode.CLOSE)
            {
                // release the stream
                out.close();
            }
        }
    }

}
