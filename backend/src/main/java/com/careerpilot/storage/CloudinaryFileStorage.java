package com.careerpilot.storage;

import com.careerpilot.config.properties.CloudinaryProperties;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.careerpilot.resume.domain.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Cloudinary-backed {@link FileStorage}.
 *
 * <p>Active when {@code app.storage.provider=cloudinary}.
 *
 * <p><strong>Three Cloudinary specifics carry real weight here.</strong>
 *
 * <p><em>{@code resource_type: raw}.</em> Cloudinary's default resource type is
 * {@code image}, and uploading a PDF as an image causes it to be processed,
 * rasterised, and served through the image pipeline — which mangles the file the
 * parser later needs. Resumes are opaque documents; {@code raw} stores the bytes
 * unchanged.
 *
 * <p><em>{@code type: authenticated}.</em> Cloudinary's default delivery is
 * public: anyone with the URL can fetch the asset, and the URL is derivable from
 * the public id. For a document containing a person's full name, phone number,
 * address, and employment history, that is unacceptable (NFR-SEC-06).
 * {@code authenticated} delivery requires a signed URL for every read.
 *
 * <p><em>A random component in the public id.</em> Even with authenticated
 * delivery, a predictable id such as {@code resumes/{userId}/resume} narrows an
 * attack to guessing one signature. A UUID in the path removes the guessing
 * entirely.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "cloudinary")
public class CloudinaryFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryFileStorage.class);

    private static final String RESOURCE_TYPE_RAW = "raw";
    private static final String DELIVERY_TYPE_AUTHENTICATED = "authenticated";

    private final Cloudinary cloudinary;
    private final String rootFolder;

    public CloudinaryFileStorage(Cloudinary cloudinary, CloudinaryProperties properties) {
        this.cloudinary = cloudinary;
        this.rootFolder = properties.folder() == null || properties.folder().isBlank()
                ? "careerpilot"
                : properties.folder();
        log.info("Cloudinary file storage active, root folder '{}'", rootFolder);
    }

    @Override
    public StoredFile store(UUID userId, byte[] content, String originalFilename, String mimeType) {
        String publicId = "%s/resumes/%s/%s".formatted(rootFolder, userId, UUID.randomUUID());

        try {
            Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", RESOURCE_TYPE_RAW,
                    "type", DELIVERY_TYPE_AUTHENTICATED,
                    // We generate the id; letting Cloudinary derive one from the
                    // filename would put the user's name in a URL path.
                    "use_filename", false,
                    "unique_filename", false,
                    // Fail rather than silently replacing an existing object.
                    // A collision here would mean two users' resumes sharing an
                    // id, which must never pass quietly.
                    "overwrite", false));

            String url = String.valueOf(result.get("secure_url"));
            int bytes = result.get("bytes") instanceof Number n ? n.intValue() : content.length;

            return new StoredFile(publicId, url, bytes, StorageProvider.CLOUDINARY);

        } catch (IOException e) {
            // The provider's message reaches the log via the cause, never the
            // client: Cloudinary errors routinely embed the cloud name and
            // occasionally a signed URL.
            throw new StorageException("Could not store the file", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Caveat worth knowing before this reaches production.</strong>
     * A Cloudinary signed URL for an {@code authenticated} asset is signed but
     * not, by itself, time-limited — the signature stays valid while the API
     * secret does. Genuine expiry requires either a {@code private} delivery
     * type with a download URL carrying {@code expires_at}, or an auth-token
     * feature that is not on every Cloudinary plan.
     *
     * <p>What this buys today is still substantial: the asset is not publicly
     * addressable, and the URL cannot be derived from the public id without the
     * secret. What it does not yet buy is a URL that stops working on its own.
     * The {@code expiresAt} returned to clients therefore describes our policy,
     * not a provider-enforced deadline.
     *
     * <p>Closing that gap needs a decision about the Cloudinary plan, so it is
     * recorded here rather than papered over. Until then, treat a leaked
     * download URL as a leaked document.
     */
    @Override
    public String generateSignedUrl(String publicId, Duration validFor) {
        return cloudinary.url()
                .resourceType(RESOURCE_TYPE_RAW)
                .type(DELIVERY_TYPE_AUTHENTICATED)
                .signed(true)
                .generate(publicId);
    }

    @Override
    public byte[] retrieve(String publicId) {
        String url = generateSignedUrl(publicId, Duration.ofMinutes(2));
        try (var stream = java.net.URI.create(url).toURL().openStream()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new StorageException("Could not read the stored file", e);
        }
    }

    @Override
    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", RESOURCE_TYPE_RAW,
                    "type", DELIVERY_TYPE_AUTHENTICATED,
                    "invalidate", true));
            // Cloudinary returns {"result":"not found"} rather than an error for
            // an absent object, which is exactly the idempotency the purge job
            // needs. Nothing to handle.
        } catch (IOException e) {
            throw new StorageException("Could not delete the stored file", e);
        }
    }

    @Override
    public StorageProvider getProvider() {
        return StorageProvider.CLOUDINARY;
    }
}
