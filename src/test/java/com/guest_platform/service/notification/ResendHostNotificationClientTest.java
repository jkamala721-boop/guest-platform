package com.guest_platform.service.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendHostNotificationClientTest {
    private static final String TEMPLATE_ID = "d23c7838-b503-4792-b7f9-60e54969828c";

    @Test
    void sendsCurrentTemplatePayloadWithExactTemplateIdAndVariableKeys() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendHostNotificationClient client = new ResendHostNotificationClient(builder, "re_test_secret",
                "Hostvero <notifications@hostvero.net>", TEMPLATE_ID);
        Map<String, String> variables = variables();
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(header("Authorization", "Bearer re_test_secret"))
                .andExpect(content().json("""
                        {"from":"Hostvero <notifications@hostvero.net>","to":["host@example.com"],
                         "template":{"id":"d23c7838-b503-4792-b7f9-60e54969828c","variables":{
                           "HOST_NAME":"Grace Host","PROPERTY_NAME":"Garden Suite",
                           "NOTIFICATION_TITLE":"Payment confirmed","MESSAGE":"A guest paid.",
                           "ACTION_LABEL":"View booking","ACTION_URL":"https://app.hostvero.net/#/bookings/1",
                           "FIRST_NAME":"Grace"}}}
                        """, true))
                .andRespond(withSuccess("{\"id\":\"email-id\"}", MediaType.APPLICATION_JSON));

        client.send("host@example.com", variables);
        server.verify();
    }

    @Test
    void rawResendErrorBodyIsNotExposed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendHostNotificationClient client = new ResendHostNotificationClient(builder, "re_test_secret",
                "notifications@hostvero.net", TEMPLATE_ID);
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withBadRequest().body("raw provider response with secret details"));

        assertThatThrownBy(() -> client.send("host@example.com", variables()))
                .hasMessage("Resend host template delivery failed with HTTP 400")
                .hasMessageNotContaining("raw provider response")
                .hasMessageNotContaining("secret details");
    }

    private Map<String, String> variables() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("HOST_NAME", "Grace Host");
        values.put("PROPERTY_NAME", "Garden Suite");
        values.put("NOTIFICATION_TITLE", "Payment confirmed");
        values.put("MESSAGE", "A guest paid.");
        values.put("ACTION_LABEL", "View booking");
        values.put("ACTION_URL", "https://app.hostvero.net/#/bookings/1");
        values.put("FIRST_NAME", "Grace");
        return values;
    }
}
