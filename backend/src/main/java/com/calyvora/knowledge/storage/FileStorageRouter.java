package com.calyvora.knowledge.storage;

import com.calyvora.knowledge.CompanyFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Picks the backend for a new file and finds the right one for an existing file.
 *
 * <p>New files go to R2 when all four R2 settings are present, otherwise to the database. An existing
 * file is always read from the backend recorded on it, so switching R2 on later does not strand any
 * file uploaded before.
 */
@Component
public class FileStorageRouter {

    private static final Logger log = LoggerFactory.getLogger(FileStorageRouter.class);

    private final DbFileStorage db;
    private final R2FileStorage r2;

    public FileStorageRouter(DbFileStorage db,
                             @Value("${calyvora.files.r2.account-id:}") String accountId,
                             @Value("${calyvora.files.r2.bucket:}") String bucket,
                             @Value("${calyvora.files.r2.access-key-id:}") String accessKeyId,
                             @Value("${calyvora.files.r2.secret-access-key:}") String secretAccessKey) {
        this.db = db;
        boolean configured = !accountId.isBlank() && !bucket.isBlank()
                && !accessKeyId.isBlank() && !secretAccessKey.isBlank();
        this.r2 = configured ? new R2FileStorage(accountId.trim(), bucket.trim(), accessKeyId.trim(), secretAccessKey.trim()) : null;
        log.info("Company files are stored in {}.", configured ? "Cloudflare R2 (bucket " + bucket.trim() + ")" : "the database");
    }

    /** Where a new upload goes. */
    public FileStorage forNewFile() {
        return r2 != null ? r2 : db;
    }

    /** Where an existing file's bytes are. */
    public FileStorage forExisting(CompanyFile file) {
        if ("R2".equals(file.getStorage())) {
            if (r2 == null) {
                throw new IllegalStateException("File " + file.getId() + " is in R2 but R2 is not configured");
            }
            return r2;
        }
        return db;
    }

    public boolean usingDatabase() {
        return r2 == null;
    }
}
