package com.calyvora.knowledge.storage;

import com.calyvora.common.error.NotFoundException;
import com.calyvora.knowledge.CompanyFile;
import com.calyvora.knowledge.CompanyFileBlob;
import com.calyvora.knowledge.CompanyFileBlobRepository;
import org.springframework.stereotype.Component;

/**
 * Bytes in Postgres (company_file_blobs). Under row-level security like every other tenant table, so
 * one company's file can never be read on another company's connection.
 */
@Component
public class DbFileStorage implements FileStorage {

    private final CompanyFileBlobRepository blobs;

    public DbFileStorage(CompanyFileBlobRepository blobs) {
        this.blobs = blobs;
    }

    @Override
    public String kind() {
        return "DB";
    }

    @Override
    public String put(CompanyFile file, byte[] bytes) {
        blobs.save(new CompanyFileBlob(file.getId(), file.getCompanyId(), bytes));
        return null;
    }

    @Override
    public byte[] get(CompanyFile file) {
        return blobs.findById(file.getId())
                .orElseThrow(() -> new NotFoundException("File contents not found"))
                .getData();
    }

    @Override
    public void delete(CompanyFile file) {
        // The blob row also goes with the file row (on delete cascade); this is explicit for clarity.
        blobs.deleteById(file.getId());
    }
}
