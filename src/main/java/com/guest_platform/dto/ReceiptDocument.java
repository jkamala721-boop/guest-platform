package com.guest_platform.dto;

/** A rendered receipt document returned only after the caller is authorized. */
public record ReceiptDocument(String filename, String html) {
}
