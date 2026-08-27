package com.guest_platform.exception;
import com.guest_platform.dto.HostOnboardingResponse;
public class HostOnboardingIncompleteException extends RuntimeException {
    private final HostOnboardingResponse onboarding;
    public HostOnboardingIncompleteException(HostOnboardingResponse onboarding){super("Complete your Hostvero setup before using this feature.");this.onboarding=onboarding;}
    public HostOnboardingResponse getOnboarding(){return onboarding;}
}
