package com.plmtracker.plm;

import java.util.Optional;

/**
 * Port to the Mini PLM part master. Services depend on this interface, so
 * tests use a fake and never call the real Mini PLM.
 */
public interface PartCatalog {

    /** False when no Mini PLM is configured; callers then skip validation. */
    boolean isEnabled();

    /**
     * Looks up one part by its exact part number (case-insensitive).
     *
     * @return the part, or empty if Mini PLM has no such part
     * @throws PartCatalogException if Mini PLM cannot be reached or login fails
     */
    Optional<PartInfo> findByPartNumber(String partNumber);
}
