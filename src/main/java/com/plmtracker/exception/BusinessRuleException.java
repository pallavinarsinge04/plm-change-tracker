package com.plmtracker.exception;

/** A request was valid JSON but breaks a business rule. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
