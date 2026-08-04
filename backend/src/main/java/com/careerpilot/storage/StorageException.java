package com.careerpilot.storage;

import com.careerpilot.common.exception.ApiException;
import com.careerpilot.common.exception.ErrorCode;

/**
 * Raised when a storage backend fails.
 *
 * <p>Maps to {@link ErrorCode#SERVICE_UNAVAILABLE} — 503 rather than 500 —
 * because the failure is a third party being unreachable, not a defect in this
 * application. That distinction matters to a client: 503 signals "retry shortly",
 * while 500 signals "this will keep failing until someone fixes it".
 *
 * <p>The provider's own message is passed as the cause so it reaches the log,
 * and never as the client-facing message: provider errors routinely embed
 * account identifiers, bucket names, and occasionally signed URLs.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public class StorageException extends ApiException {

    /**
     * @param message client-safe description
     * @param cause   the provider's failure, for logs only
     */
    public StorageException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }

    /**
     * @param message client-safe description
     */
    public StorageException(String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
    }
}
