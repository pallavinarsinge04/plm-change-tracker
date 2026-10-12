package com.plmtracker.plm;

/** What the tracker needs to know about a part that exists in Mini PLM. */
public record PartInfo(String partNumber, String name, String latestRevision, String latestState) {
}
