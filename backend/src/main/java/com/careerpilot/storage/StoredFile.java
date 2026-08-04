package com.careerpilot.storage;

import com.careerpilot.resume.domain.StorageProvider;

/**
 * A handle to a stored file.
 *
 * @param publicId  provider handle, used to fetch and delete. Not a URL, and not
 *                  guessable — see {@code CloudinaryFileStorage} for why that
 *                  matters.
 * @param url       base URL of the object. Not directly usable for private
 *                  files; signed at read time via
 *                  {@link FileStorage#generateSignedUrl}.
 * @param sizeBytes size actually stored, as reported by the provider rather than
 *                  as claimed by the uploader
 * @param provider  which backend holds it
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record StoredFile(
        String publicId,
        String url,
        int sizeBytes,
        StorageProvider provider
) {
}
