package com.guest_platform.service.photo;

/** Deliberately contains no provider response, credential, URL or original filename. */
public class PhotoUnavailableException extends RuntimeException {
    public PhotoUnavailableException() { super("Photographs are temporarily unavailable. Please retry."); }
}
