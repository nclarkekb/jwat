package org.jwat.arc;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.jwat.common.ByteCountingPushBackInputStream;
import org.jwat.common.ContentType;
import org.jwat.common.HttpResponse;
import org.jwat.common.IPAddressParser;
import org.jwat.common.Payload;
import org.jwat.common.PayloadOnClosedHandler;

/**
 * This abstract class represents the common base ARC data which is present in
 * both a record or version block. This includes possible common
 * validation and format warnings/errors encountered in the process.
 * This class also contains the common parts of the ARC parser which are
 * intended to be called by extending classes.
 *
 * @author lbihanic, selghissassi, nicl
 */
public abstract class ArcRecordBase implements PayloadOnClosedHandler {

    /** Buffer size used in toString(). */
    public static final int TOSTRING_BUFFER_SIZE = 256;

    /** Invalid ARC file property. */
    protected static final String ARC_FILE = "ARC file";

    /** Invalid ARC record property. */
    protected static final String ARC_RECORD = "ARC record";

    /** ARC record version. */
    protected ArcVersion version;

    /** ARC record version block. */
    protected ArcVersionBlock versionBlock;

    /** ARC record starting offset relative to the source arc file input
     *  stream. */
    protected long startOffset = -1L;

    /** Validation errors. */
    protected List<ArcValidationError> errors = null;

    /** Do the record fields comply in number with the one dictated by its
     *  version. */
    protected boolean hasCompliantFields = false;

    /*
     * Raw fields.
     */

    /** ARC record string field: url. */
    public String recUrl;

    /** ARC record string field: ip address. */
    public String recIpAddress;

    /** ARC record string field: archive date. */
    public String recArchiveDate;

    /** ARC record string field: content-type. */
    public String recContentType;

    /** ARC record string field: result code. */
    public Integer recResultCode = null;

    /** ARC record string field: checksum. */
    public String recChecksum = "-";

    /** ARC record string field: location. */
    public String recLocation = "-";

    /** ARC record string field: offset. */
    public Long recOffset = null;

    /** ARC record string field: filename. */
    public String recFilename;

    /** ARC record string field: length. */
    public Long recLength;

    /*
     * Parsed fields.
     */

    /** String Url parsed and validated into an <code>URI</code> object. */
    public URI url;

    /** Url Scheme. (filedesc, http, https, dns, etc.) */
    public String protocol;

    /** IpAddress parsed and validated to a <code>InetAddress</code> object. */
    public InetAddress inetAddress;

    /** String to <code>Date</code> conversion from "YYYYMMDDhhmmss" format. */
    public Date archiveDate;

    /** Content-Type wrapper object with optional parameters. */
    public ContentType contentType;

    /** Specifies whether the network has been already validated or not. */
    //private boolean isNetworkDocValidated = false;

    /*
     * Payload
     */

    /** Has payload been closed before. */
    protected boolean bPayloadClosed;

    /** Has record been closed flag. */
    protected boolean bClosed;

    /** Payload object if any exists. */
    protected Payload payload;

    /** HttpResponse header content parsed from payload. */
    protected HttpResponse httpResponse;

    /** Computed block digest. */
    public byte[] computedBlockDigest;

    /** Computed payload digest. */
    public byte[] computedPayloadDigest;

    /**
     * Creates an ARC record from the specified record description.
     * @param recordLine ARC record string
     */
    public void parseRecord(String recordLine) {
        hasCompliantFields = false;
        if (recordLine != null) {
            String[] records = recordLine.split(" ", -1);
            // Compare to expected numbers of fields.
            // Extract mandatory version-independent header data.
            hasCompliantFields = (records.length
                              == versionBlock.descValidator.fieldNames.length);
            if(!hasCompliantFields) {
                this.addValidationError(ArcErrorType.INVALID, ARC_RECORD,
                        "URL record definition and record definition are not "
                        + "compliant");
            }
            // Parse
            recUrl = ArcFieldValidator.getArrayValue(records,
                    ArcConstants.AF_IDX_URL);
            recIpAddress = ArcFieldValidator.getArrayValue(records,
                    ArcConstants.AF_IDX_IPADDRESS);
            recArchiveDate = ArcFieldValidator.getArrayValue(records,
                    ArcConstants.AF_IDX_ARCHIVEDATE);
            recContentType = ArcFieldValidator.getArrayValue(records,
                    ArcConstants.AF_IDX_CONTENTTYPE);
            // Validate
            url = this.parseUri(recUrl);
            inetAddress = parseIpAddress(recIpAddress);
            archiveDate = parseDate(recArchiveDate);
            contentType = parseContentType(recContentType);
            // Version 2
            if (version.equals(ArcVersion.VERSION_2)) {
                recResultCode = parseInteger(
                        ArcFieldValidator.getArrayValue(records,
                                ArcConstants.AF_IDX_RESULTCODE),
                        ArcConstants.RESULT_CODE_FIELD, false);
                recChecksum = parseString(
                        ArcFieldValidator.getArrayValue(records,
                                ArcConstants.AF_IDX_CHECKSUM),
                        ArcConstants.CHECKSUM_FIELD);
                recLocation = parseString(
                        ArcFieldValidator.getArrayValue(records,
                                ArcConstants.AF_IDX_LOCATION),
                        ArcConstants.LOCATION_FIELD, true);
                recOffset = this.parseLong(
                        ArcFieldValidator.getArrayValue(records,
                                ArcConstants.AF_IDX_OFFSET),
                        ArcConstants.OFFSET_FIELD);
                recFilename = parseString(
                        ArcFieldValidator.getArrayValue(records,
                                ArcConstants.AF_IDX_FILENAME),
                        ArcConstants.FILENAME_FIELD);
            }
            recLength = parseLong(
                    ArcFieldValidator.getArrayValue(records,
                            records.length - 1), ArcConstants.LENGTH_FIELD);
            // Check read and computed offset value only if we're reading
            // a plain ARC file, not a GZipped ARC.
            if ((recOffset != null) && (startOffset > 0L)
                                && (recOffset.longValue() != startOffset)) {
                addValidationError(ArcErrorType.INVALID,
                        ArcConstants.OFFSET_FIELD, recOffset.toString());
            }
        }
    }

    /**
     * Returns the starting offset of the record in the containing ARC.
     * @return the starting offset of the record
     */
    public long getStartOffset() {
        return startOffset;
    }

    /**
     * Checks if the ARC record is valid.
     * @return true/false based on whether the ARC record is valid or not
     */
    public boolean isValid() {
        return (hasCompliantFields && !hasErrors());
    }

    /**
     * Checks if the ARC record has errors.
     * @return true/false based on whether the ARC record is valid or not
     */
    public boolean hasErrors() {
        return ((errors != null) && (!errors.isEmpty()));
    }

    /**
     * Validation errors getter.
     * @return validation errors list
     */
    public Collection<ArcValidationError> getValidationErrors() {
        return (hasErrors())? Collections.unmodifiableList(errors) : null;
    }

    /**
     * Add validation error.
     * @param errorType the error type {@link ArcErrorType}.
     * @param field the field name
     * @param value the error value
     */
    protected void addValidationError(ArcErrorType errorType,
                                      String field, String value) {
        if (errors == null) {
            errors = new LinkedList<ArcValidationError>();
        }
        errors.add(new ArcValidationError(errorType, field, value));
    }

    /**
     * Checks if the ARC record has warnings.
     * @return true/false based on whether the ARC record has warnings or not
     */
    public boolean hasWarnings() {
        return false;
    }

    /**
     * Gets Network doc warnings.
     * @return validation errors list/
     */
    public Collection<String> getWarnings() {
        return null;
    }

    /**
     * Called when the payload object is closed and final steps in the
     * validation process can be performed.
     * @throws IOException io exception in final validation processing
     */
    @Override
    public void payloadClosed() throws IOException {
        if (!bPayloadClosed) {
            if (payload != null) {
                // Check for truncated payload.
                if (payload.getUnavailable() > 0) {
                    addValidationError(ArcErrorType.INVALID, ARC_RECORD,
                            "Payload length mismatch");
                }
                /*
                 * Check block digest.
                 */
                MessageDigest md = payload.getMessageDigest();
                if (md != null) {
                    computedBlockDigest = md.digest();
                }
                if (httpResponse != null) {
                    /*
                     * Check payload digest.
                     */
                    md = httpResponse.getMessageDigest();
                    if (md != null) {
                        computedPayloadDigest = md.digest();
                    }
                }
            }
            bPayloadClosed = true;
        }
    }

    /**
     * Check to see if the record has been closed.
     * @return boolean indicating whether this record is closed or not
     */
    public boolean isClosed() {
        return bClosed;
    }

    /**
     * Close resources associated with the ARC record.
     * Mainly payload stream if any.
     * @throws IOException io exception close the payload resources
     */
    public void close() throws IOException {
        if (!bClosed) {
            // Ensure input stream is at the end of the record payload.
            if (payload != null) {
                payload.close();
            }
            payloadClosed();
            payload = null;
            bClosed = true;
        }
    }

    /**
     * Process the ARC record stream for possible payload data.
     * @param in ARC record <code>InputStream</code>
     * @param reader <code>ArcReader</code> used, with access to user defined
     * options
     * @throws IOException io exception in the parsing process
     */
    protected abstract void processPayload(ByteCountingPushBackInputStream in,
                                        ArcReader reader) throws IOException;

    /**
     * isNetworkDocValidated getter.
     * @return the isNetworkDocValidated
     */
    /*
    public boolean isNetworkDocValidated() {
        return isNetworkDocValidated;
    }
    */

    /**
     * Validates the network doc. Subclasses have to check the
     * coherence of the network doc.
     */
    //public abstract void validateNetworkDoc();

    /**
     * Validates the network doc. This method is called when processing
     * compressed ARC.
     * @throws IOException io exception in parsing
     */
    /*
    public final void validateNetworkDocContent(InputStream in)
                                                    throws IOException {
        if(payload != null && !isNetworkDocValidated){
            boolean isValid = this.isValid(in);
            isNetworkDocValidated = true;
            if(!isValid){
                this.addValidationError(ArcErrorType.INVALID, ARC_RECORD,
                    "Non LF characters encountered after network doc");
            }
        }
    }
    */

    /**
     * Specifies whether this record has a payload or not.
     * @return true/false whether the ARC record has a payload
     */
    public boolean hasPayload() {
        return (payload != null);
    }

    /**
     * Return Payload object.
     * @return payload or <code>null</code>
     */
    public Payload getPayload() {
        return payload;
    }

    /**
     * Payload content <code>InputStream</code> getter.
     * @return Payload content <code>InputStream</code>
     */
    public InputStream getPayloadContent() {
        return (payload != null) ? payload.getInputStream() : null;
    }

    /**
     * Parses the remaining input stream and validates that the characters
     * encountered are equal to LF or CR.
     * @param in <code>InputStream</code> to validate
     * @return true/false based on whether the remaining input stream contains
     * only LF and CR characters or not.
     * @throws IOException io exception in parsing
     */
    public boolean isValid(InputStream in) throws IOException{
        if (in == null) {
            throw new IllegalArgumentException("in");
        }
        boolean isValid = true;
        int b;
        while ((b = in.read()) != -1) {
            if (b != '\n' && b != '\r') {
                isValid = false;
                break;
            }
        }
        return isValid;
    }

    /**
     * Returns an Integer object holding the value of the specified string.
     * @param intStr the value to parse.
     * @param field field name
     * @return an integer object holding the value of the specified string
     */
    protected Integer parseInteger(String intStr, String field) {
         Integer iVal = null;
         if (intStr != null && intStr.length() > 0) {
            try {
                iVal = Integer.valueOf(intStr);
            } catch (Exception e) {
                // Invalid long value.
                this.addValidationError(ArcErrorType.INVALID, field, intStr);
            }
         }
         return iVal;
    }

    /**
     * Returns an Integer object holding the value of the specified string.
     * @param intStr the value to parse.
     * @param field field name
     * @param optional specifies if the value is optional or not
     * @return an integer object holding the value of the specified string
     */
    protected Integer parseInteger(String intStr, String field,
                                   boolean optional) {
        Integer result = this.parseInteger(intStr, field);
        if((result == null) && (!optional)){
            // Missing mandatory value.
             this.addValidationError(ArcErrorType.MISSING, field, intStr);
        }
        return result;
    }

    /**
     * Returns a Long object holding the value of the specified string.
     * @param longStr the value to parse.
     * @param field field name
     * @return a long object holding the value of the specified string
     */
    protected Long parseLong(String longStr, String field) {
        Long lVal = null;
         if (longStr != null && longStr.length() > 0) {
            try {
                lVal = Long.valueOf(longStr);
            } catch (Exception e) {
                // Invalid long value.
                this.addValidationError(ArcErrorType.INVALID, field, longStr);
            }
         } else {
             // Missing mandatory value.
             this.addValidationError(ArcErrorType.MISSING, field, longStr);
         }
         return lVal;
    }

    /**
     * Parses a string.
     * @param str the value to parse
     * @param field field name
     * @param optional specifies if the value is optional or not
     * @return the parsed value
     */
    protected String parseString(String str, String field, boolean optional) {
        if (((str == null) || (str.trim().length() == 0))
            && (!optional)) {
            this.addValidationError(ArcErrorType.MISSING, field, str);
        }
        return str;
    }

    /**
     * Parses a string.
     * @param str the value to parse
     * @param field field name
     * @return the parsed value
     */
    protected String parseString(String str, String field) {
        return parseString(str, field, false);
    }

    /**
     * Returns an URL object holding the value of the specified string.
     * @param value the URL to parse
     * @return an URL object holding the value of the specified string
     */
    protected URI parseUri(String value) {
        URI uri = null;
        if ((value != null) && (value.length() != 0)) {
            try {
                uri = new URI(value);
                protocol = uri.getScheme();
            } catch (Exception e) {
                // Invalid URI.
                addValidationError(ArcErrorType.INVALID,
                                   ArcConstants.URL_FIELD, value);
            }
        } else {
            // Missing mandatory value.
            addValidationError(ArcErrorType.MISSING,
                               ArcConstants.URL_FIELD, value);
        }
        return uri;
    }

    /**
     * Parses ARC record IP address.
     * @param ipAddress the IP address to parse
     * @return the IP address
     */
    protected InetAddress parseIpAddress(String ipAddress) {
        InetAddress inetAddr = null;
        if (ipAddress != null && ipAddress.length() > 0) {
            inetAddr = IPAddressParser.getAddress(ipAddress);
            if (inetAddr == null) {
                // Invalid date.
                addValidationError(ArcErrorType.INVALID,
                                   ArcConstants.IP_ADDRESS_FIELD, ipAddress);
            }
        } else {
            // Missing mandatory value.
            addValidationError(ArcErrorType.MISSING,
                               ArcConstants.IP_ADDRESS_FIELD, ipAddress);
        }
        return inetAddr;
    }

    /**
     * Parses ARC record date.
     * @param dateStr the date to parse.
     * @return the formatted date.
     */
    protected Date parseDate(String dateStr) {
        Date date = null;
        if (dateStr != null && dateStr.length() > 0) {
                date = ArcDateParser.getDate(dateStr);
                if (date == null) {
                    // Invalid date.
                    addValidationError(ArcErrorType.INVALID,
                                       ArcConstants.DATE_FIELD, dateStr);
                }
        } else {
            // Missing mandatory value.
            addValidationError(ArcErrorType.MISSING,
                               ArcConstants.DATE_FIELD, dateStr);
        }
        return date;
    }

    /**
     * Parses ARC record content type.
     * @param contentTypeStr ARC record content type
     * @return ARC record content type
     */
    protected ContentType parseContentType(String contentTypeStr) {
        ContentType contentType = null;
        if (contentTypeStr != null && contentTypeStr.length() != 0) {
            contentType = ContentType.parseContentType(contentTypeStr);
            if (contentType == null) {
                // Invalid content-type.
                addValidationError(ArcErrorType.INVALID,
                                   ArcConstants.CONTENT_TYPE_FIELD,
                                   contentTypeStr);
            }
        } else {
            // Missing content-type.
            addValidationError(ArcErrorType.MISSING,
                               ArcConstants.CONTENT_TYPE_FIELD,
                               contentTypeStr);
        }
        return contentType;
    }

    /**
     * Version getter.
     * @return the version
     */
    public ArcVersion getVersion() {
        return version;
    }

    /**
     * URL getter.
     * @return the URL
     */
    public URI getUrl() {
        return url;
    }

    /**
     * Raw URL getter.
     * @return the raw URL
     */
    public String getRawUrl() {
        return recUrl;
    }

    /**
     * <code>inetAddress</code> getter.
     * @return the InetAddress
     */
    public InetAddress getInetAddress() {
        return inetAddress;
    }

    /**
     * IpAddress getter.
     * @return the ipAddress
     */
    public String getRawIpAddress() {
        return recIpAddress;
    }

    /**
     * ArchiveDate getter.
     * @return the archiveDate
     */
    public Date getArchiveDate() {
        return archiveDate;
    }

    /**
     * Raw ArchiveDate getter.
     * @return the rawArchiveDate
     */
    public String getRawArchiveDate() {
        return recArchiveDate;
    }

    /**
     * Content-Type getter.
     * @return the contentType
     */
    public String getContentType() {
        return recContentType;
    }

    /**
     * Result-Code getter.
     * @return the resultCode
     */
    public Integer getResultCode() {
        return recResultCode;
    }

    /**
     * Checksum getter.
     * @return the checksum
     */
    public String getChecksum() {
        return recChecksum;
    }

    /**
     * Location getter.
     * @return the location
     */
    public String getLocation() {
        return recLocation;
    }

    /**
     * Offset getter.
     * @return the offset
     */
    public Long getOffset() {
        return recOffset;
    }


    /**
     * FileName getter.
     * @return the fileName
     */
    public String getFileName() {
        return recFilename;
    }

    /**
     * Length getter.
     * @return the length
     */
    public Long getLength() {
        return recLength;
    }

    /**
     * Protocol getter.
     * @return the protocol
     */
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(TOSTRING_BUFFER_SIZE);
        if (recUrl != null) {
            builder.append("url:").append(recUrl);
        }
        if (recIpAddress != null) {
            builder.append(", ipAddress: ").append(recIpAddress);
        }
        if (recArchiveDate != null) {
            builder.append(", archiveDate: ").append(recArchiveDate);
        }
        if (recContentType != null) {
            builder.append(", contentType: ").append(recContentType);
        }
        if (recResultCode != null) {
            builder.append(", resultCode: ").append(recResultCode);
        }
        if (recChecksum != null) {
            builder.append(", checksum: ").append(recChecksum);
        }
        if (recLocation != null) {
            builder.append(", location: ").append(recLocation);
        }
        if (recOffset != null) {
            builder.append(", offset: ").append(recOffset);
        }
        if (recFilename != null) {
            builder.append(", fileName: ")
                .append(recFilename);
            if (recLength != null) {
                builder.append(", length: ").append(recLength);
            }
        }
        return builder.toString();
    }

}
