package com.careerpilot.config;

import com.careerpilot.config.properties.CloudinaryProperties;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Cloudinary client.
 *
 * <p>Only instantiated when {@code app.storage.provider=cloudinary}, so a
 * developer running against local storage never needs credentials.
 *
 * <p>The explicit credential check is the point of this class. Cloudinary's
 * client constructor accepts blank values happily and fails later, at the first
 * upload, with an authentication error — which surfaces as a broken feature in
 * production rather than a failed deployment. Checking here converts that into a
 * startup failure naming the missing variables.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Configuration
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "cloudinary")
public class CloudinaryConfig {

    /**
     * @param properties Cloudinary credentials from the environment
     * @return a configured client
     * @throws IllegalStateException if credentials are missing
     */
    @Bean
    public Cloudinary cloudinary(CloudinaryProperties properties) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("""
                    app.storage.provider is 'cloudinary' but credentials are missing. \
                    Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET, \
                    or set app.storage.provider=local for development.""");
        }

        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", properties.cloudName(),
                "api_key", properties.apiKey(),
                "api_secret", properties.apiSecret(),
                // Always HTTPS. Resume bytes must not cross the network in clear.
                "secure", true));
    }
}
