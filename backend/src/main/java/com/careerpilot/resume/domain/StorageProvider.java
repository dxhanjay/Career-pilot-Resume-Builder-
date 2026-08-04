package com.careerpilot.resume.domain;

/**
 * Which storage backend holds a file's bytes.
 *
 * <p>Recorded per row rather than assumed globally. A deployment that starts on
 * local disk and later moves to Cloudinary will have rows of both kinds, and the
 * delete path must know which provider to call for each — otherwise migrating
 * storage silently orphans every previously uploaded file.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum StorageProvider {

    /** Cloudinary, with authenticated delivery. Used in deployed environments. */
    CLOUDINARY,

    /** Local filesystem. Development and tests only; not durable. */
    LOCAL
}
