package com.guest_platform.exception;
public class LifecycleNotFoundException extends RuntimeException { private final String code; public LifecycleNotFoundException(String code,String message){super(message);this.code=code;} public String getCode(){return code;} }
