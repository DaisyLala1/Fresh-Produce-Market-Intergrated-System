package com.freshproduce.pos.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Builds the human-readable reference numbers used throughout the system. */
public final class CodeGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private CodeGenerator() { }

    /** e.g. CON-2026-00056 – unique because it is derived from the database identity. */
    public static String consignmentReference(LocalDate received, long id) {
        return String.format("CON-%d-%05d", received.getYear(), id);
    }

    /** e.g. RCP-20260916-000123 */
    public static String receiptNumber(LocalDateTime saleDate, long id) {
        return String.format("RCP-%s-%06d", saleDate.format(DAY), id);
    }

    /** e.g. FM0001 */
    public static String farmerCode(long id) {
        return String.format("FM%04d", id);
    }
}
