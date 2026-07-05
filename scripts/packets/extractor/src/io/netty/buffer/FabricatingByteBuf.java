package io.netty.buffer;

import java.nio.charset.Charset;

/**
 * A ByteBuf that fabricates data on demand. Every read is intercepted: before the read,
 * enough bytes are synthesized (values chosen by the {@link Fabricator} based on the current
 * call stack) and appended to the buffer; the read is then recorded.
 *
 * <p>Extends the real heap buffer (in-package) because the delegating WrappedByteBuf marks
 * its methods final. Inherited methods may call each other (readBoolean -> readByte), so a
 * re-entrancy guard keeps each application-level read recorded exactly once.
 */
public class FabricatingByteBuf extends UnpooledHeapByteBuf {

    /** Decides fabricated byte values and receives read events. Implemented by the tracer. */
    public interface Fabricator {
        /** Fill {@code out} with fabricated content for a pending read. {@code stack} is the current call stack. */
        void fabricate(byte[] out, StackTraceElement[] stack);

        /**
         * Extra bytes to append immediately after the fabricated read (e.g. string content whose
         * length was just fabricated and is checked against readableBytes() before consumption).
         */
        byte[] extraContent(byte[] justFilled, StackTraceElement[] stack);

        /** Called after each intercepted read. {@code value} is a best-effort numeric value of what was read. */
        void onRead(String op, int byteLength, long value, StackTraceElement[] stack);

        /**
         * Bytes that must be readable ahead of time for readers that snapshot readableBytes()
         * (e.g. NBT via ByteBufInputStream). Null if the current caller needs none.
         */
        byte[] advanceReserve(StackTraceElement[] stack);

        /** Hard cap exceeded — throw to abort the decode. */
        void checkBudget(int totalOps);
    }

    private final Fabricator fabricator;
    private boolean entered;
    private int ops;

    public FabricatingByteBuf(Fabricator fabricator) {
        super(UnpooledByteBufAllocator.DEFAULT, 256, Integer.MAX_VALUE);
        this.fabricator = fabricator;
    }

    private StackTraceElement[] ensure(int n) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        fabricator.checkBudget(++ops);
        int missing = n - super.readableBytes();
        if (missing > 0) {
            byte[] fill = new byte[missing];
            fabricator.fabricate(fill, stack);
            writeAppend(fill);
            byte[] extra = fabricator.extraContent(fill, stack);
            if (extra != null && extra.length > 0) {
                writeAppend(extra);
            }
        }
        return stack;
    }

    /** Append without triggering our own interception. */
    private void writeAppend(byte[] bytes) {
        boolean prev = entered;
        entered = true;
        try {
            super.writeBytes(bytes);
        } finally {
            entered = prev;
        }
    }

    private void record(String op, int len, long value, StackTraceElement[] stack) {
        fabricator.onRead(op, len, value, stack);
    }

    /** Runs a read with fabrication + recording, guarding against nested self-calls. */
    private interface Read<T> { T run(); }

    private <T> T traced(String op, int n, Read<T> read, java.util.function.ToLongFunction<T> value) {
        if (entered) return read.run();
        entered = true;
        try {
            StackTraceElement[] stack = ensure(n);
            T v = read.run();
            record(op, n, value.applyAsLong(v), stack);
            return v;
        } finally {
            entered = false;
        }
    }

    /* ── single-value reads ─────────────────────────────── */

    @Override public boolean readBoolean() { return traced("readBoolean", 1, super::readBoolean, v -> v ? 1 : 0); }
    @Override public byte readByte() { return traced("readByte", 1, super::readByte, v -> v); }
    @Override public short readUnsignedByte() { return traced("readByte", 1, super::readUnsignedByte, v -> v); }
    @Override public short readShort() { return traced("readShort", 2, super::readShort, v -> v); }
    @Override public short readShortLE() { return traced("readShort", 2, super::readShortLE, v -> v); }
    @Override public int readUnsignedShort() { return traced("readShort", 2, super::readUnsignedShort, v -> v); }
    @Override public int readUnsignedShortLE() { return traced("readShort", 2, super::readUnsignedShortLE, v -> v); }
    @Override public int readMedium() { return traced("readMedium", 3, super::readMedium, v -> v); }
    @Override public int readMediumLE() { return traced("readMedium", 3, super::readMediumLE, v -> v); }
    @Override public int readUnsignedMedium() { return traced("readMedium", 3, super::readUnsignedMedium, v -> v); }
    @Override public int readUnsignedMediumLE() { return traced("readMedium", 3, super::readUnsignedMediumLE, v -> v); }
    @Override public int readInt() { return traced("readInt", 4, super::readInt, v -> v); }
    @Override public int readIntLE() { return traced("readInt", 4, super::readIntLE, v -> v); }
    @Override public long readUnsignedInt() { return traced("readInt", 4, super::readUnsignedInt, v -> v); }
    @Override public long readUnsignedIntLE() { return traced("readInt", 4, super::readUnsignedIntLE, v -> v); }
    @Override public long readLong() { return traced("readLong", 8, super::readLong, v -> v); }
    @Override public long readLongLE() { return traced("readLong", 8, super::readLongLE, v -> v); }
    @Override public char readChar() { return traced("readChar", 2, super::readChar, v -> v); }
    @Override public float readFloat() { return traced("readFloat", 4, super::readFloat, v -> 0); }
    @Override public double readDouble() { return traced("readDouble", 8, super::readDouble, v -> 0); }

    /* ── bulk reads ─────────────────────────────────────── */

    @Override public ByteBuf readBytes(int length) { return traced("readBytes", length, () -> super.readBytes(length), v -> 0); }
    @Override public ByteBuf readSlice(int length) { return traced("readBytes", length, () -> super.readSlice(length), v -> 0); }
    @Override public ByteBuf readRetainedSlice(int length) { return traced("readBytes", length, () -> super.readRetainedSlice(length), v -> 0); }

    @Override public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        return traced("readBytes", length, () -> super.readBytes(dst, dstIndex, length), v -> 0);
    }

    @Override public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        return traced("readBytes", length, () -> super.readBytes(dst, dstIndex, length), v -> 0);
    }

    @Override public ByteBuf readBytes(java.nio.ByteBuffer dst) {
        return traced("readBytes", dst.remaining(), () -> super.readBytes(dst), v -> 0);
    }

    @Override public ByteBuf readBytes(java.io.OutputStream out, int length) throws java.io.IOException {
        if (entered) return super.readBytes(out, length);
        entered = true;
        try {
            StackTraceElement[] stack = ensure(length);
            super.readBytes(out, length);
            record("readBytes", length, 0, stack);
            return this;
        } finally {
            entered = false;
        }
    }

    @Override public int readBytes(java.nio.channels.GatheringByteChannel out, int length) throws java.io.IOException {
        if (entered) return super.readBytes(out, length);
        entered = true;
        try {
            StackTraceElement[] stack = ensure(length);
            int v = super.readBytes(out, length);
            record("readBytes", length, 0, stack);
            return v;
        } finally {
            entered = false;
        }
    }

    @Override public CharSequence readCharSequence(int length, Charset charset) {
        return traced("readCharSequence", length, () -> super.readCharSequence(length, charset), v -> 0);
    }

    @Override public ByteBuf skipBytes(int length) {
        return traced("skipBytes", length, () -> super.skipBytes(length), v -> 0);
    }

    /* ── snapshot + absolute access paths ───────────────── */

    @Override public int readableBytes() {
        if (!entered) {
            StackTraceElement[] stack = new Throwable().getStackTrace();
            byte[] reserve = fabricator.advanceReserve(stack);
            if (reserve != null && super.readableBytes() < reserve.length) {
                byte[] missing = new byte[reserve.length - super.readableBytes()];
                System.arraycopy(reserve, reserve.length - missing.length, missing, 0, missing.length);
                writeAppend(missing);
            }
        }
        return super.readableBytes();
    }

    @Override public String toString(int index, int length, Charset charset) {
        if (entered) return super.toString(index, length, charset);
        entered = true;
        try {
            StackTraceElement[] stack = new Throwable().getStackTrace();
            int needed = index + length - super.writerIndex();
            if (needed > 0) {
                byte[] fill = new byte[needed];
                fabricator.fabricate(fill, stack);
                writeAppend(fill);
            }
            if (length > 0) {
                record("stringContent", length, 0, stack);
            }
            return super.toString(index, length, charset);
        } finally {
            entered = false;
        }
    }
}
