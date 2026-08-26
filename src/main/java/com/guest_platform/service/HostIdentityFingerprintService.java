package com.guest_platform.service;
import java.nio.charset.StandardCharsets; import java.security.GeneralSecurityException; import java.util.Locale;
import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service;
import com.guest_platform.entity.HostIdentityType;
@Service
public class HostIdentityFingerprintService {
 private final byte[] secret;
 public HostIdentityFingerprintService(@Value("${app.security.host-identity-fingerprint-secret}") String secret){if(secret==null||secret.isBlank())throw new IllegalStateException("Host identity fingerprint secret is required");this.secret=secret.getBytes(StandardCharsets.UTF_8);}
 public String normalize(HostIdentityType type,String value){return type.name()+":"+value.replaceAll("[\\s\\-_/]","").toUpperCase(Locale.ROOT);}
 public String fingerprint(HostIdentityType type,String value){return hmac(normalize(type,value));}
 public String privacyHash(String namespace,String value){return value==null||value.isBlank()?null:hmac(namespace+":"+value.trim());}
 private String hmac(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException("Host identity fingerprinting is unavailable",e);}}
}
