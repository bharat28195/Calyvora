package com.calyvora.knowledge.storage;

import com.calyvora.knowledge.CompanyFile;

/**
 * Where the bytes of a company file live. Two backends: the database (the default, works on any
 * deployment with nothing to set up) and Cloudflare R2 (switched on by configuration, for when
 * files outgrow the database's free tier).
 */
public interface FileStorage {

    /** {@code DB} or {@code R2}; recorded on the file so it is always read back from where it went. */
    String kind();

    /**
     * Store the bytes.
     *
     * @return the object key to remember, or null when the backend needs none
     */
    String put(CompanyFile file, byte[] bytes);

    byte[] get(CompanyFile file);

    void delete(CompanyFile file);
}
