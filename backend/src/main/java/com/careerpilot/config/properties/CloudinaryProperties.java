package com.careerpilot.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for {@code app.cloudinary.*}.
 *
 * <p>Deliberately <strong>not</strong> {@code @Validated} with {@code @NotBlank}.
 * These values are required only when {@code app.storage.provider=cloudinary};
 * a developer running locally against {@code LocalFileStorage} has no Cloudinary
 * account, and failing their startup over unused credentials would be hostile.
 *
 * <p>{@code CloudinaryConfig} validates them at the point they are actually
 * needed, so a misconfigured production deployment still fails fast and with a
 * clear message.
 *
 * @param cloudName Cloudinary cloud name
 * @param apiKey    API key
 * @param apiSecret API secret — signs upload requests and delivery URLs
 * @param folder    root folder for uploaded objects
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "app.cloudinary")
public record CloudinaryProperties(
        String cloudName,
        String apiKey,
        String apiSecret,
        String folder
) {

    /**
     * @return whether enough configuration is present to build a client
     */
    public boolean isConfigured() {
        return notBlank(cloudName) && notBlank(apiKey) && notBlank(apiSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
