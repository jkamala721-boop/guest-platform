package com.guest_platform.exception;
public class LifecycleConflictException extends RuntimeException { private final String code; public LifecycleConflictException(String code,String message){super(message);this.code=code;} public String getCode(){return code;} }
