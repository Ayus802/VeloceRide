package com.rideshare.verification;

import com.rideshare.common.enums.VerificationChannel;
import com.rideshare.config.AppProperties;
import com.rideshare.verification.delivery.ProviderOtpDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Verifies the exact request sent to Twilio for SMS and for the WhatsApp sandbox. */
class ProviderOtpDispatcherTest {

    private static final String MESSAGES_URL = "https://api.twilio.com/2010-04-01/Accounts/AC123/Messages.json";

    private static AppProperties props(String from) {
        return new AppProperties(null, null, null, null, null, null, null, null, null, null,
                new AppProperties.Sms("AC123", "secret", from), null);
    }

    private static String form(String to, String from) {
        return "To=" + java.net.URLEncoder.encode(to, java.nio.charset.StandardCharsets.UTF_8)
                + "&From=" + java.net.URLEncoder.encode(from, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void whatsappSenderPrefixesTheRecipient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith(
                        form("whatsapp:+919876543210", "whatsapp:+14155238886"))))
                .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));

        ProviderOtpDispatcher dispatcher = new ProviderOtpDispatcher(props("whatsapp:+14155238886"), builder);
        assertThat(dispatcher.canDeliver(VerificationChannel.PHONE)).isTrue();
        dispatcher.send(VerificationChannel.PHONE, "+919876543210", "Asha", "123456");
        server.verify();
    }

    @Test
    void plainSenderSendsRegularSms() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith(form("+919876543210", "+15017122661"))))
                .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));

        new ProviderOtpDispatcher(props("+15017122661"), builder)
                .send(VerificationChannel.PHONE, "+919876543210", "Asha", "123456");
        server.verify();
    }
}
