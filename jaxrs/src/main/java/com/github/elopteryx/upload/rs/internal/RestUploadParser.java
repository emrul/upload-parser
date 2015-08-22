package com.github.elopteryx.upload.rs.internal;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import com.github.elopteryx.upload.errors.MultipartException;
import com.github.elopteryx.upload.errors.RequestSizeException;
import com.github.elopteryx.upload.internal.BlockingUploadParser;
import com.github.elopteryx.upload.internal.MultipartParser;
import com.github.elopteryx.upload.internal.Headers;
import com.github.elopteryx.upload.internal.PartStreamImpl;
import com.github.elopteryx.upload.internal.UploadContextImpl;
import com.github.elopteryx.upload.rs.Part;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A subclass of the blocking parser. It doesn't have a dependency
 * on the servlet request and can be initialized from the header values.
 * This makes it ideal for a Jax-Rs environment, to be used in a
 * message body reader.
 */
public class RestUploadParser extends BlockingUploadParser {

    /**
     * Public constructor.
     */
    public RestUploadParser() {
        super(null);
    }

    /**
     * Initializes the parser from the given parameters and performs
     * a blocking parse.
     * @param contentLength The length of the request
     * @param mimeType The content type of the request
     * @param encoding The character encoding of the request
     * @param stream The request stream
     * @return The multipart object, representing the request
     * @throws IOException If an error occurred with the I/O
     */
    public MultiPartImpl doBlockingParse(long contentLength, String mimeType, String encoding, InputStream stream)
    throws IOException {
        if (maxRequestSize > -1) {
            if (contentLength > maxRequestSize) {
                throw new RequestSizeException("The size of the request ("
                        + contentLength
                        + ") is greater than the allowed size (" + maxRequestSize + ")!",
                        contentLength, maxRequestSize);
            }
        }

        checkBuffer = ByteBuffer.allocate(sizeThreshold);
        context = new UploadContextImpl(null, null);
        buf = new byte[maxBytesUsed / 2];

        String boundary;
        if (mimeType != null && mimeType.startsWith(MULTIPART_FORM_DATA)) {
            boundary = Headers.extractBoundaryFromHeader(mimeType);
            if (boundary == null) {
                throw new IllegalArgumentException("Could not find boundary in multipart request with ContentType: "
                        + mimeType
                        + ", multipart data will not be available");
            }
            Charset charset = encoding != null ? Charset.forName(encoding) : ISO_8859_1;
            parseState = MultipartParser.beginParse(this, boundary.getBytes(), maxBytesUsed, charset);

            inputStream = stream;
        }
        while (true) {
            int count = inputStream.read(buf);
            if (count == -1) {
                if (!parseState.isComplete()) {
                    throw new MultipartException("Stream ended unexpectedly!");
                } else {
                    break;
                }
            } else if (count > 0) {
                checkRequestSize(count);
                parseState.parse(ByteBuffer.wrap(buf, 0, count));
            }
        }
        List<Part> parts = context.getPartStreams()
                .stream()
                .map(partStream -> new PartImpl((PartStreamImpl) partStream))
                .collect(Collectors.toList());
        return new MultiPartImpl(parts, requestSize);
    }
}
