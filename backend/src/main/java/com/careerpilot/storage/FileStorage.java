package com.careerpilot.storage;

import com.careerpilot.resume.domain.StorageProvider;

import java.time.Duration;
import java.util.UUID;

/**
 * Outbound port for binary file storage.
 *
 * <p>{@code ResumeService} depends on this interface and never on Cloudinary.
 * The payoff is not vendor-agnosticism for its own sake — it is that
 * {@link LocalFileStorage} exists, so every integration test in Phase 5 runs
 * with no Cloudinary account, no API key, and no network. Upload code that can
 * only be exercised against a live third party is upload code that is exercised
 * rarely and breaks quietly.
 *
 * <p>The methods speak in application terms — store this user's file, give me a
 * link that expires — rather than in any provider's vocabulary. A port that
 * mirrored Cloudinary's API would leak Cloudinary's model into the service and
 * defeat the purpose.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface FileStorage {

    /**
     * Stores a file and returns its handle.
     *
     * @param userId           owner, used to namespace the stored object
     * @param content          the raw bytes
     * @param originalFilename sanitised original name, for the stored object's label
     * @param mimeType         type determined from the bytes, not from the client
     * @return a handle sufficient to fetch or delete the object later
     * @throws StorageException if the provider rejects or fails the upload
     */
    StoredFile store(UUID userId, byte[] content, String originalFilename, String mimeType);

    /**
     * Produces a time-limited URL granting read access.
     *
     * <p>The URL <em>is</em> the access control, which is why it must expire:
     * anything longer-lived is a permanent public link to a private document,
     * and links leak through browser history, referrer headers, and shared
     * screenshots.
     *
     * <p>Returned rather than streamed deliberately. Streaming resume bytes
     * through the API container costs CPU time we are billed for and adds a hop,
     * with no security benefit over a URL that expires in minutes.
     *
     * @param publicId provider handle from {@link StoredFile#publicId()}
     * @param validFor how long the URL should remain usable
     * @return a signed, expiring URL
     * @throws StorageException if the URL cannot be produced
     */
    String generateSignedUrl(String publicId, Duration validFor);

    /**
     * Fetches a file's bytes.
     *
     * <p>Used by the parser in Phase 6. Not used to serve downloads to clients —
     * see {@link #generateSignedUrl}.
     *
     * @param publicId provider handle
     * @return the file's content
     * @throws StorageException if the object is missing or unreadable
     */
    byte[] retrieve(String publicId);

    /**
     * Permanently removes a stored file.
     *
     * <p>Implementations must treat an already-absent object as success. This is
     * called by the purge job, which may retry after a partial failure, and a
     * retry that fails because the previous attempt succeeded would block the
     * purge indefinitely.
     *
     * @param publicId provider handle
     * @throws StorageException only on a genuine failure, never on "not found"
     */
    void delete(String publicId);

    /**
     * @return which provider this implementation is, recorded on each row so the
     *         delete path knows whom to call for files stored before a migration
     */
    StorageProvider getProvider();
}
