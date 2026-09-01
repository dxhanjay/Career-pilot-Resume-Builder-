package com.careerpilot.storage;

import com.careerpilot.config.properties.StorageProperties;
import com.careerpilot.resume.domain.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

/**
 * Filesystem-backed {@link FileStorage} for development and tests.
 *
 * <p><strong>This adapter is what makes Phase 5 testable.</strong> Without it,
 * every integration test covering upload, download, and delete would need a
 * Cloudinary account, an API key in CI, and a network round trip per test — so
 * in practice those tests would not exist, and the upload path would be the
 * least-tested code in the application while handling the least-trusted input.
 *
 * <p>Active when {@code app.storage.provider=local}, which is the default for
 * the {@code dev} and {@code test} profiles.
 *
 * <p><strong>Not suitable for production, for two reasons.</strong> Railway
 * containers have ephemeral filesystems, so every redeploy would silently delete
 * every uploaded resume. And with more than one replica, a file written by one
 * container is invisible to the others. The {@code prod} profile selects
 * Cloudinary, and {@code CloudinaryConfig} refuses to start without credentials.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "local")
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path rootDirectory;

    public LocalFileStorage(StorageProperties storageProperties, Environment environment) {
        String configured = storageProperties.localDirectory();
        this.rootDirectory = Paths.get(
                configured == null || configured.isBlank()
                        ? System.getProperty("java.io.tmpdir") + "/careerpilot-uploads"
                        : configured);

        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create local storage directory", e);
        }

        // Loud, once, at startup — never a silent degradation.
        //
        // On a container platform the filesystem is ephemeral: every redeploy,
        // restart, or scale-down deletes every uploaded resume, and with more
        // than one instance a file written by one is invisible to the others.
        // The symptom is a resume that uploaded fine yesterday and 404s today,
        // which is exactly the kind of thing nobody thinks to check for.
        if (environment.matchesProfiles("prod")) {
            log.warn("Local file storage is active in the PROD profile, at {}. Container "
                    + "filesystems are ephemeral: uploaded resumes will be LOST on the next "
                    + "redeploy or restart, and will not be visible to a second instance. "
                    + "Survivable for a demo, not suitable for real use. To fix, set "
                    + "APP_STORAGE_PROVIDER=cloudinary together with CLOUDINARY_CLOUD_NAME, "
                    + "CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET.", rootDirectory);
        } else {
            log.info("Local file storage active at {}", rootDirectory);
        }
    }

    @Override
    public StoredFile store(UUID userId, byte[] content, String originalFilename, String mimeType) {
        String publicId = "resumes/" + userId + "/" + UUID.randomUUID();
        Path target = resolve(publicId);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new StorageException("Could not store the file", e);
        }

        return new StoredFile(publicId, target.toUri().toString(), content.length, StorageProvider.LOCAL);
    }

    /**
     * Returns a {@code file:} URI.
     *
     * <p>Not signed and not time-limited, because there is no signing authority
     * on a local disk. Acceptable only because this adapter never runs where a
     * real user could reach it — the property that makes it unsuitable for
     * production is the same one that makes signing meaningless here.
     */
    @Override
    public String generateSignedUrl(String publicId, Duration validFor) {
        return resolve(publicId).toUri().toString();
    }

    @Override
    public byte[] retrieve(String publicId) {
        try {
            return Files.readAllBytes(resolve(publicId));
        } catch (IOException e) {
            throw new StorageException("Could not read the stored file", e);
        }
    }

    @Override
    public void delete(String publicId) {
        try {
            // deleteIfExists, not delete: the purge job may retry after a partial
            // failure, and a retry that failed because the previous attempt
            // succeeded would block the purge indefinitely.
            Files.deleteIfExists(resolve(publicId));
        } catch (IOException e) {
            throw new StorageException("Could not delete the stored file", e);
        }
    }

    @Override
    public StorageProvider getProvider() {
        return StorageProvider.LOCAL;
    }

    /**
     * Resolves a public id to a path, refusing anything that escapes the root.
     *
     * <p>Path traversal defence. A {@code publicId} of {@code ../../etc/passwd}
     * would otherwise read or delete an arbitrary file. Ids are generated by this
     * class today, so the value is trusted — but "the input is trusted" is a
     * property of today's call sites, and the check costs two lines.
     */
    private Path resolve(String publicId) {
        Path resolved = rootDirectory.resolve(publicId).normalize();
        if (!resolved.startsWith(rootDirectory)) {
            throw new StorageException("Invalid storage identifier");
        }
        return resolved;
    }
}
