/**
 * Java Web Archive Toolkit - Software to read and validate ARC, WARC
 * and GZip files. (http://jwat.org/)
 * Copyright 2011-2012 Netarkivet.dk (http://netarkivet.dk/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwat.gzip;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;

/**
 * A writer for (multi-part) GZip files.
 * As per the GZIP file format specification version 4.3.
 * The class writes a GZip header. The compressed data and trailer is also
 * written by this class but is instigated by calling the write method on the
 * GZip entry itself.
 *
 * @author nicl
 */
public class GzipWriter {

    /** Default input buffer size. */
    public static final int DEFAULT_INPUT_BUFFER_SIZE = 1024;

    /** Output stream for GZip (multi-part) file. */
    protected OutputStream out;
    /** Deflater used to compress GZip entries. */
    protected Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    /** Checksum object used to calculate CRC16 and CRC32 values. */
    protected CRC32 crc = new CRC32();
    /** Input buffer used to feed the deflater. */
    protected byte[] inputBytes;

    /** Current GZip entry object. */
    protected GzipReaderEntry gzipEntry;

    /** Buffer used to read header.  */
    byte[] headerBytes = new byte[10];
    /** Buffer used to read the XLEN value. */
    byte[] xlenBytes = new byte[2];
    /** Buffer used to read the FNAME data. */
    byte[] fnameBytes = null;
    /** Buffer used to read the FCOMMENT data. */
    byte[] fcommentBytes = null;
    /** Buffer used to read the CRC16 value. */
    byte[] crc16Bytes = new byte[2];
    /** Buffer used to read trailer. */
    byte[] trailerBytes = new byte[8];

    /**
     * Construct a GZip writer with a default input buffer size of 1024.
     * @param out output stream of GZip file
     * @param in input stream of GZip file
     */
    public GzipWriter(OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("out is null!");
        }
        this.out = new BufferedOutputStream(out, DEFAULT_INPUT_BUFFER_SIZE);
        inputBytes = new byte[DEFAULT_INPUT_BUFFER_SIZE];
    }

    /**
     * Construct a GZip writer with the specified input buffer size.
     * @param out output stream of GZip file
     * @param buffer_size input buffer size to use
     */
    public GzipWriter(OutputStream out, int buffer_size) {
        if (out == null) {
            throw new IllegalArgumentException("out is null!");
        }
        if (buffer_size <= 0) {
            throw new IllegalArgumentException("buffer_size is less or equals to zero!");
        }
        this.out = new BufferedOutputStream(out, buffer_size);
        inputBytes = new byte[buffer_size];
    }

    /**
     * Release resources associated with this writer.
     * @throws IOException if an io error occurs while closing writer
     */
    public void close() throws IOException {
        if (gzipEntry != null) {
            gzipEntry.close();
            gzipEntry = null;
        }
        out = null;
        if (def != null) {
            def.end();
            def = null;
        }
    }

    /**
     * Write a GZip entry header and prepare for compressing input data.
     * @param entry GZip entry object
     * @throws IOException if an io error occurs while writing header
     */
    public void writeEntryHeader(GzipReaderEntry entry) throws IOException {
        if (entry == null) {
            throw new IllegalArgumentException("entry is null!");
        }
        crc.reset();
        def.reset();
        gzipEntry = entry;
        /*
         * FTEXT.
         */
        if (gzipEntry.bFText) {
            gzipEntry.flg |= GzipConstants.FLG_FTEXT;
        }
        /*
         * FEXTRA.
         */
        if (entry.extraBytes != null) {
            entry.flg |= GzipConstants.FLG_FEXTRA;
            entry.xlen = entry.extraBytes.length;
            xlenBytes[0] = (byte)(entry.xlen & 255);
            xlenBytes[1] = (byte)((entry.xlen >> 8) & 255);
        }
        /*
         * FNAME.
         */
        if (entry.fname != null) {
            entry.flg |= GzipConstants.FLG_FNAME;
            try {
                fnameBytes = entry.fname.getBytes("ISO-8859-1");
            }
            catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        /*
         * FCOMMENT.
         */
        if (entry.fcomment != null) {
            entry.flg |= GzipConstants.FLG_FCOMMENT;
            try {
                fcommentBytes = entry.fcomment.getBytes("ISO-8859-1");
            }
            catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        /*
         * FHCRC.
         */
        if (gzipEntry.bFhCrc) {
            gzipEntry.flg |= GzipConstants.FLG_FHCRC;
        }
        /*
         * Header.
         */
        if (gzipEntry.date != null) {
            gzipEntry.mtime = gzipEntry.date.getTime() / 1000;
        }
        else if (gzipEntry.mtime != 0) {
            gzipEntry.date = new Date(gzipEntry.mtime * 1000);
        }
        headerBytes[0] = (byte)(entry.magic & 255);
        headerBytes[1] = (byte)((entry.magic >> 8) & 255);
        headerBytes[2] = (byte)entry.cm;
        headerBytes[3] = (byte)entry.flg;
        headerBytes[4] = (byte)(entry.mtime & 255);
        headerBytes[5] = (byte)((entry.mtime >> 8) & 255);
        headerBytes[6] = (byte)((entry.mtime >> 16) & 255);
        headerBytes[7] = (byte)((entry.mtime >> 24) & 255);
        headerBytes[8] = (byte)entry.xfl;
        headerBytes[9] = (byte)entry.os;
        out.write(headerBytes);
        crc.update(headerBytes);
        if ((entry.flg & GzipConstants.FLG_FEXTRA) == GzipConstants.FLG_FEXTRA) {
            out.write(xlenBytes);
            out.write(entry.extraBytes);
            crc.update(xlenBytes);
            crc.update(entry.extraBytes);
        }
        if ((entry.flg & GzipConstants.FLG_FNAME) == GzipConstants.FLG_FNAME) {
            out.write(fnameBytes);
            out.write(0);
            crc.update(fnameBytes);
            crc.update(0);
        }
        if ((entry.flg & GzipConstants.FLG_FCOMMENT) == GzipConstants.FLG_FCOMMENT) {
            out.write(fcommentBytes);
            out.write(0);
            crc.update(fcommentBytes);
            crc.update(0);
        }
        if ((entry.flg & GzipConstants.FLG_FHCRC) == GzipConstants.FLG_FHCRC) {
            entry.comp_crc16 = ((int)crc.getValue()) & 0x0000ffff;
            entry.crc16 = entry.comp_crc16;
            crc16Bytes[0] = (byte)(entry.crc16 & 255);
            crc16Bytes[1] = (byte)((entry.crc16 >> 8) & 255);
            out.write(crc16Bytes);
        }
        /*
         * Prepare Entry InputStream.
         */
        crc.reset();
        entry.isize = 0;
        entry.writer = this;
        entry.bEof = false;
    }

    /**
     * Write the GZip entry trailer.
     * @param entry GZip entry object
     * @throws IOException if an io error occurs while writing trailer
     */
    protected void writeTrailer(GzipReaderEntry entry) throws IOException {
        trailerBytes[0] = (byte)(entry.crc32 & 255);
        trailerBytes[1] = (byte)((entry.crc32 >> 8) & 255);
        trailerBytes[2] = (byte)((entry.crc32 >> 16) & 255);
        trailerBytes[3] = (byte)((entry.crc32 >> 24) & 255);
        trailerBytes[4] = (byte)(entry.isize & 255);
        trailerBytes[5] = (byte)((entry.isize >> 8) & 255);
        trailerBytes[6] = (byte)((entry.isize >> 16) & 255);
        trailerBytes[7] = (byte)((entry.isize >> 24) & 255);
        out.write(trailerBytes);
        out.flush();
    }

    /**
     *
     * @param in uncompressed data input stream
     * @param b compressed data buffer
     * @param off offset in compressed data buffer
     * @param len length of compressed data to read
     * @return number of compressed bytes read
     * @throws DataFormatException if an error occurs in deflater
     * @throws IOException if an error occurs while compressing
     */
    protected int readCompressed(InputStream in, byte[] b, int off, int len) throws DataFormatException, IOException {
        int deflated = 0;
        while ((deflated = def.deflate(b, off, len)) == 0) {
            if (def.finished()) {
                return -1;
            }
            else if (def.needsInput()) {
                int read = in.read(inputBytes, 0, inputBytes.length);
                if (read != -1) {
                    def.setInput(inputBytes, 0, read);
                    crc.update(inputBytes, 0, read);
                }
                else {
                    def.finish();
                }
            }
            else {
                throw new DataFormatException("Deflater malfunction!");
            }
        }
        return deflated;
    }

}
