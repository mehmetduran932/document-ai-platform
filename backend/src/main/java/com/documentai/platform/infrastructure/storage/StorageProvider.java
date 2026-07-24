package com.documentai.platform.infrastructure.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

/**
 * Abstraction over the object store that holds original uploaded files. The rest of the
 * application only depends on this interface, never on the concrete storage backend (Cloudflare
 * R2 today; anything S3-compatible, or a different backend entirely, tomorrow).
 */
public interface StorageProvider {

    void upload(String key, InputStream content, long size, String contentType);

    InputStream download(String key);

    void delete(String key);

    URL presignedDownloadUrl(String key, Duration ttl);
}
