package com.bankmanagement.common.web;

import com.bankmanagement.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/**
 * Validates uploaded files by their content, never by the client-supplied name or content type, so a
 * script renamed to {@code logo.png} is rejected. SVG is deliberately not accepted (it can carry script).
 */
public final class UploadedFiles {

    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String WEBP = "image/webp";
    public static final String PDF = "application/pdf";

    private UploadedFiles() {
    }

    /** A validated upload: the detected content type, the bytes and a safe display name. */
    public record Upload(String contentType, byte[] data, String fileName) {
    }

    /**
     * Reads and validates an upload.
     *
     * @param allowed  content types accepted for this upload
     * @param maxBytes size limit
     * @param field    request part name, used in validation errors
     */
    public static Upload read(MultipartFile file, Set<String> allowed, int maxBytes, String field) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.validation("Choose a file to upload.", field);
        }
        if (file.getSize() > maxBytes) {
            throw BusinessException.validation("The file is larger than " + (maxBytes / 1024) + " KB.", field);
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw BusinessException.validation("The file could not be read. Try again.", field);
        }
        String type = detect(data);
        if (type == null || !allowed.contains(type)) {
            throw BusinessException.validation("Unsupported file type. Allowed: " + describe(allowed) + ".", field);
        }
        return new Upload(type, data, safeName(file.getOriginalFilename(), type));
    }

    /** Content type from the file signature, or null when it is none of the supported formats. */
    static String detect(byte[] d) {
        if (startsWith(d, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        if (startsWith(d, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (d.length >= 12 && startsWith(d, 'R', 'I', 'F', 'F')
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return WEBP;
        }
        if (startsWith(d, '%', 'P', 'D', 'F', '-')) {
            return PDF;
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Keeps letters, digits, dot, dash and underscore; falls back to a generic name. */
    private static String safeName(String original, String type) {
        String extension = switch (type) {
            case PNG -> ".png";
            case JPEG -> ".jpg";
            case WEBP -> ".webp";
            default -> ".pdf";
        };
        if (original == null || original.isBlank()) {
            return "file" + extension;
        }
        String base = original.replaceAll(".*[/\\\\]", "").replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.length() > 100) {
            base = base.substring(base.length() - 100);
        }
        return base.isBlank() ? "file" + extension : base;
    }

    private static String describe(Set<String> allowed) {
        return String.join(", ", Arrays.stream(allowed.toArray(String[]::new))
                .map(t -> t.substring(t.indexOf('/') + 1).toUpperCase()).sorted().toList());
    }
}
