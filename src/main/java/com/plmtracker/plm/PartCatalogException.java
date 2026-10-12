package com.plmtracker.plm;

/** Mini PLM could not be reached, or refused our login. Not the same as "part not found". */
public class PartCatalogException extends RuntimeException {

    public PartCatalogException(String message) {
        super(message);
    }

    public PartCatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
