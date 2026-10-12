package com.plmtracker.exception;

/** The part number does not exist in Mini PLM, so no change request can be raised against it. */
public class UnknownPartException extends RuntimeException {

    public UnknownPartException(String partNumber) {
        super("Part number " + partNumber + " does not exist in Mini PLM");
    }
}
