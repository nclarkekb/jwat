package org.jwat.common;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * <code>InputStream</code> with a maximum amount of bytes available to read.
 * When the stream is closed the remaining bytes are left untouched.
 *
 * @author lbihanic, selghissassi, nicl
 */
public class MaxLengthRecordingInputStream extends FilterInputStream {

    /** Buffer size to use when read skipping. */
    public static final int SKIP_READ_BUFFER_SIZE = 1024;

    protected byte[] skip_read_buffer = new byte[SKIP_READ_BUFFER_SIZE];

    /** Output stream used to keep a record of data read. */
    protected ByteArrayOutputStream record;

    /** Maximum remaining bytes available. */
    protected long available;

    /**
     * Create a new input stream with a maximum number of bytes available from
     * the underlying stream.
     * @param in the input stream to wrap
     * @param available maximum number of bytes available through this stream
     */
    public MaxLengthRecordingInputStream(InputStream in, long available) {
        super(in);
        this.record = new ByteArrayOutputStream();
        this.available = available;
    }

    /**
     * Return the bytes recorded by the stream.
     * @return recorded data as a byte array
     */
    public byte[] getRecording() {
        return record.toByteArray();
    }

    /**
     * Closing will only closes the recording.
     */
    @Override
    public void close() throws IOException {
        record.close();
    }

    @Override
    public int available() throws IOException {
        return (available > Integer.MAX_VALUE)
                                ? Integer.MAX_VALUE : (int) (available);
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized void mark(int readlimit) {
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int read() throws IOException {
        int b = -1;
        if (available > 0) {
            b = in.read();
            if (b != -1) {
                --available;
                record.write(b);
            }
        }
        return b;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int l = -1;
        if (available > 0) {
            l = in.read(b, off, (int) Math.min(len, available));
            if (l > 0){
                available -= l;
                record.write(b, off, l);
            }
        }
        return l;
    }

    @Override
    public long skip(long n) throws IOException {
        long l = 0;
        if (available > 0) {
            l = read(skip_read_buffer, 0, (int) Math.min(
                            Math.min(n, available), SKIP_READ_BUFFER_SIZE));
            if (l > 0) {
                record.write(skip_read_buffer, 0, (int) l);
            } else {
                l = 0;
            }
        }
        return l;
    }

}
