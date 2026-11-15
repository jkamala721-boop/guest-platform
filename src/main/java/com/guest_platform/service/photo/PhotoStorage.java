package com.guest_platform.service.photo;

public interface PhotoStorage {
    void upload(String path, byte[] jpeg);
    void delete(String prefix);
    java.util.Map<String, String> sign(java.util.List<String> paths, int seconds);
}
