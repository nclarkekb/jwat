/**
 * JHOVE2 - Next-generation architecture for format-aware characterization
 *
 * Copyright (c) 2009 by The Regents of the University of California,
 * Ithaka Harbors, Inc., and The Board of Trustees of the Leland Stanford
 * Junior University.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * o Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * o Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * o Neither the name of the University of California/California Digital
 *   Library, Ithaka Harbors/Portico, or Stanford University, nor the names of
 *   its contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package dk.netarkivet.gzip;


import java.util.Date;


/**
 * A GZip file entry.
 */
public class GzipEntry
{
    /* Error flags */
    public static final int INVALID_EXTRA_FLAGS = 1;
    public static final int INVALID_OPERATING_SYSTEM = 2;
    public static final int INVALID_RESERVED_FLAGS = 4;
    public static final int INVALID_ISIZE = 8;
    public static final int INVALID_CRC16 = 16;
    public static final int INVALID_CRC32 = 32;

    protected int index;
    protected long offset;

    protected boolean asciiFlag;

    protected int method;
    protected Date date;
    protected int extraFlags;
    protected int os;

    /* Non immutable fields: use defensive copy in getter method. */
    protected byte[] extraFields;

    protected String fileName;

    protected String comment;

    protected long readCrc16;
    protected long computedCrc16;


    protected int errors = 0;
    protected long size  = -1L;
    protected long csize = -1L;
    protected long readISize = -1L;
    protected long computedISize = -1;
    protected long readCrc32 = -1L;
    protected long computedCrc32 = -1L;

    
    /** Zero argument constructor. */
    public GzipEntry()
    {
        super();
    }

    /** Creates a new GzipEntry object. */
    GzipEntry(int index, long offset,
                     int method, int extraFlags,
                     Date date, String fileName, int os,
                     String comment, boolean asciiFlag, byte[] extraFields,
                     int reservedFlags, long readCrc16, long computedCrc16) {
        this.index          = index;
        this.offset         = offset;
        this.method         = method;
        this.extraFlags     = extraFlags;
        this.date           = date;
        this.fileName       = fileName;
        this.os             = os;
        this.comment        = comment;
        this.asciiFlag      = asciiFlag;
        this.extraFields    = extraFields;
        this.readCrc16      = readCrc16;
        this.computedCrc16  = computedCrc16;

        if (reservedFlags != 0) {
            this.addErrors(INVALID_RESERVED_FLAGS);
        }
        /*
        if ((os != null) && (! os.isValid())) {
            this.addErrors(INVALID_OPERATING_SYSTEM);
        }
        if ((extraFlags != null) && (! extraFlags.isValid())) {
            this.addErrors(INVALID_EXTRA_FLAGS);
        }
        */
        if ((readCrc16 > 0L) && (readCrc16 != computedCrc16)) {
            this.addErrors(INVALID_CRC16);
        }
    }

    /**
     * Returns the index of this entry in the GZip file.
     * @return the index (0-based) of the entry.
     */
    public int getIndex() {
        return this.index;
    }

    /**
     * Returns the offset of the beginning of this entry in the GZip
     * file.
     * @return the offset of this entry, as a number of bytes from the
     *         start of the GZip file.
     */
    public long getOffset() {
        return offset;
    }

    /**
     * Returns the compression method used for this entry.
     * @return the compression method.
     */
    public int getCompressionMethod() {
        return method;
    }

    /**
     * Returns the most recent modification time of the original
     * compressed file as a number of milliseconds since 00:00:00 GMT,
     * Jan. 1, 1970.
     * @return last modification time of the compressed file or
     *         <code>-1</code> if none is present in the GZip header.
     */
    public long getTime() {
        return (date != null) ? date.getTime() : -1L;
    }

    /**
     * Returns the most recent modification time of the original
     * compressed file as a {@link Date} object.
     * @return last modification date of the compressed file or
     *         <code>null</code> if none is present in the GZip header.
     */
    public Date getDate() {
        return (date != null) ? new Date(date.getTime()) : null;
    }

    /**
     * Returns the compression type indicated in the extra flags of
     * the member header.
     * @return the compression type or <code>null</code> if absent.
     *
     * @see    #isExtraFlagsValid
     */
    public int getCompressionFlags() {
        return extraFlags;
    }

    /**
     * Returns the operating system on which the GZip member was
     * compressed.
     * @return the operating system.
     *
     * @see    #isOperatingSystemValid
     */
    public int getOperatingSystem() {
        return os;
    }

    /**
     * Returns the extra fields of the GZip member header.
     * @return the extra fields as an array of bytes or
     *         <code>null</code> if none are present.
     */
    public byte[] getExtra() {
        int l = extraFields.length;
        byte[] copy = new byte[l];
        System.arraycopy(extraFields, 0, copy, 0, l);
        return copy;
    }

    /**
     * Returns the name of the compressed file.
     * @return the name of the compressed file or <code>null</code> if
     *         the compressed data did not come from a file.
     */
    public String getName() {
        return fileName;
    }

    /**
     * Returns the GZip member comment.
     * @return the GZip member comment or <code>null</code> if absent.
     */
    public String getComment() {
        return comment;
    }

    /**
     * Returns whether the GZip member is announced as containing only
     * ASCII text.
     * @return the ASCII text flag from the member header.
     */
    public boolean isAscii() {
        return asciiFlag;
    }

    /**
     * Returns the CRC16 read from the GZip member header.
     * @return the CRC16 of the GZip member header or <code>-1</code>
     *         if absent.
     *
     * @see    #getComputedHeaderCrc
     * @see    #isHeaderCrcValid
     */
    public long getHeaderCrc() {
        return readCrc16;
    }

    /**
     * Returns the CRC16 computed from the GZip member header.
     * @return the computed CRC16.
     *
     * @see    #getHeaderCrc
     * @see    #isHeaderCrcValid
     */
    public long getComputedHeaderCrc() {
        return computedCrc16;
    }

    /**
     * Returns the data CRC (a.k.a. CRC32) read from the GZip member
     * trailer.
     * @return the CRC32 of the GZip member trailer or <code>-1</code>
     *         if the member trailer has not yet been read.
     *
     * @see    #getComputedDataCrc
     * @see    #isDataCrcValid
     */
    public long getDataCrc() {
        return readCrc32;
    }

    /**
     * Returns the data CRC (a.k.a. CRC32) computed from the read
     * member data.
     * @return the computed CRC32 or <code>-1</code> if the member
     *         trailer has not yet been read.
     *
     * @see    #getDataCrc
     * @see    #isDataCrcValid
     */
    public long getComputedDataCrc() {
        return computedCrc32;
    }

    /**
     * Returns the (computed) uncompressed size of the member data.
     * @return the uncompressed size of the member data or
     *         <code>-1</code> if the member trailer has not yet
     *         been read.
     */
    public long getSize() {
        return size;
    }

    /**
     * Returns the (computed) compressed size of the member data.
     * @return the compressed size of the member data or
     *         <code>-1</code> if the member trailer has not yet
     *         been read.
     */
    public long getCompressedSize() {
        return csize;
    }

    /**
     * Returns the ISIZE of the GZip member trailer, i.e. the announced
     * compressed size of the member data modulo 32.
     * @return the ISIZE value of the member trailer or
     *         <code>-1</code> if the member trailer has not yet
     *         been read.
     *
     * @see    #isISizeValid
     */
    public long getISize() {
        return readISize;
    }

    /**
     * Returns the (computed) ISIZE of the GZip member trailer, i.e.
     * the compressed size of the member data modulo 32.
     * @return the computed ISIZE value of the member data or
     *         <code>-1</code> if the member trailer has not yet
     *         been read.
     *
     * @see    #isISizeValid
     */
    public long getComputedISize() {
        return computedISize;
    }

    /**
     * Returns whether this entry is compliant with the rules listed
     * in section 2.3.1.2 of RFC 1952.
     * <blockquote>
     * Compliant decompressors shall only check ID1 + ID2 (magic
     * number), CM (compression method) and unset reserved flags. They
     * may ignore FTEXT and OS header fields.
     * <blockquote>
     * <p>
     * As no GzipEntry instance can be created with invalid magic number
     * or unsupported compression method (deflate), this method only
     * checks that no reserved flag is set.</p>
     *
     * @return <code>true</code> if the entry is compliant with RFC 1952
     *         rules; <code>false</code> otherwise.
     */
    public boolean isCompliant() {
        return isReservedFlagsValid();
    }

    /**
     * Returns whether this entry is valid, i.e. is compliant and no
     * error (invalid CRC or ISize) was found.
     *
     * @return <code>true</code> if the entry is valid;
     *         <code>false</code> otherwise.
     */
    public boolean isValid() {
        return (errors == 0);
    }

    /**
     * Returns whether the header extra flags are valid, i.e. only the
     * the compression type flags are set at most.
     * @return <code>true</code> if the header extra flags are valid;
     *         <code>false</code> otherwise.
     *
     * @see    #getCompressionFlags
     */
    public boolean isExtraFlagsValid() {
        return (! isErrorSet(INVALID_EXTRA_FLAGS));
    }

    /**
     * Returns whether the operating system value is valid.
     * @return <code>true</code> if the operating system value is valid;
     *         <code>false</code> otherwise.
     *
     * @see    #getOperatingSystem
     */
    public boolean isOperatingSystemValid() {
        return (! isErrorSet(INVALID_OPERATING_SYSTEM));
    }

    /**
     * Returns whether the header reserved flags are valid
     * (i.e. not set).
     * @return <code>true</code> if the header reserved flags are valid;
     *         <code>false</code> otherwise.
     */
    public boolean isReservedFlagsValid() {
        return (! isErrorSet(INVALID_RESERVED_FLAGS));
    }

    /**
     * Returns whether the read data ISIZE and the computed one
     * are equals.
     * @return <code>true</code> if the read ISIZE and the computed one
     *         are equals; <code>false</code> otherwise.
     *
     * @see    #getISize
     */
    public boolean isISizeValid() {
        return (! isErrorSet(INVALID_ISIZE));
    }

    /**
     * Returns whether the read header CRC (a.k.a. CRC16) and the
     * computed one are equals.
     * @return <code>true</code> if the read CRC16) and the computed one
     *         are equals; <code>false</code> otherwise.
     *
     * @see    #getHeaderCrc
     * @see    #getComputedHeaderCrc
     */
    public boolean isHeaderCrcValid() {
        return (! isErrorSet(INVALID_CRC16));
    }

    /**
     * Returns whether the read data CRC (a.k.a. CRC32) and the computed
     * one are equals.
     * @return <code>true</code> if the read CRC32) and the computed one
     *         are equals; <code>false</code> otherwise.
     *
     * @see    #getDataCrc
     * @see    #getComputedDataCrc
     */
    public boolean isDataCrcValid() {
        return (! isErrorSet(INVALID_CRC32));
    }

    /* package */ void setSizes(long csize, long size) {
        this.csize = csize;
        this.size  = size;
    }

    /* package */ void setISize(long readISize, long computedISize) {
        this.readISize = readISize;
        this.computedISize = computedISize;
        if (readISize != computedISize) {
            this.addErrors(INVALID_ISIZE);
        }
    }

    /* package */ void setDataCrc(long readCrc32, long computedCrc32) {
        this.readCrc32 = readCrc32;
        this.computedCrc32 = computedCrc32;
        if (readCrc32 != computedCrc32) {
            this.addErrors(INVALID_CRC32);
        }
    }

    private void addErrors(int errors) {
        errors |= errors;
    }

    private boolean isErrorSet(int errorMask) {
        return ((errors & errorMask) != 0);
    }

}
