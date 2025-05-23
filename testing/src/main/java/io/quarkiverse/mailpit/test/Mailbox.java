package io.quarkiverse.mailpit.test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

import org.eclipse.microprofile.config.ConfigProvider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.quarkiverse.mailpit.test.invoker.ApiClient;
import io.quarkiverse.mailpit.test.invoker.ApiException;
import io.quarkiverse.mailpit.test.model.*;
import io.quarkiverse.mailpit.test.rest.ApplicationApi;
import io.quarkiverse.mailpit.test.rest.MessageApi;
import io.quarkiverse.mailpit.test.rest.MessagesApi;
import io.quarkiverse.mailpit.test.rest.TestingApi;

/**
 * Injected MailContext wrapping the API to Mailpit for unit testing.
 */
public class Mailbox {
    private ApiClient apiClient;
    private ApplicationApi applicationApi;
    private MessagesApi messagesApi;
    private MessageApi messageApi;
    private TestingApi testingApi;

    /**
     * Delete a single message.
     *
     * @param ID Database ID to delete
     */
    public void delete(String ID) {
        final MessagesApi messagesApi = getMessagesApi();
        final DeleteMessagesParamsRequest request = new DeleteMessagesParamsRequest();
        request.addIdsItem(ID);
        try {
            messagesApi.deleteMessagesParams(request);
        } catch (ApiException e) {
            rethrow(e);
        }
    }

    /**
     * Delete all messages.
     *
     */
    public void clear() {
        final MessagesApi messagesApi = getMessagesApi();
        final DeleteMessagesParamsRequest request = new DeleteMessagesParamsRequest();
        try {
            messagesApi.deleteMessagesParams(request);
        } catch (ApiException e) {
            rethrow(e);
        }
    }

    /**
     * Search messages. Returns the latest messages matching a search.
     *
     * @param query Search query (required)
     * @param start Pagination offset (optional, default to 0)
     * @param limit Limit results (optional, default to 50)
     * @return List<Message>
     */
    public List<Message> find(String query, Integer start, Integer limit) {
        return find(query, start, limit, null);
    }

    /**
     * Search messages. Returns the latest messages matching a search.
     *
     * @param query Search query (required)
     * @param start Pagination offset (optional, default to 0)
     * @param limit Limit results (optional, default to 50)
     * @param timeZone Specify a timezone for before: and after: queries (optional, default null)
     * @return List<Message>
     */
    public List<Message> find(String query, Integer start, Integer limit, TimeZone timeZone) {
        final List<Message> results = new ArrayList<>();
        final MessagesApi messagesApi = getMessagesApi();
        final MessageApi messageApi = getMessageApi();
        try {
            String startStr = null;
            if (start != null) {
                startStr = start.toString();
            }
            String limitStr = null;
            if (limit != null) {
                limitStr = limit.toString();
            }
            String timezoneID = null;
            if (timeZone != null) {
                timezoneID = timeZone.getID();
            }
            final MessagesSummary messages = messagesApi.searchParams(query, startStr, limitStr, timezoneID);
            for (MessageSummary summary : Objects.requireNonNull(messages.getMessages())) {
                Message message = messageApi.getMessageParams(summary.getID());
                results.add(message);
            }
        } catch (ApiException e) {
            rethrow(e);
        }

        return results;
    }

    /**
     * Search messages. Returns the first message matching a search.
     *
     * @param query Search query (required)
     * @return Message
     */
    public Message findFirst(String query) {
        final List<Message> results = find(query, 0, 1);
        if (results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    /**
     * Set chaos testing.
     *
     * @param chaosConfig chaos configuration (required)
     *
     */
    public void setChaos(ChaosConfig chaosConfig) {
        final TestingApi testingApi = getTestingApi();

        try {
            testingApi.setChaosParams(convertChaosTriggers(chaosConfig.getChaosTriggers()));
        } catch (ApiException e) {
            rethrow(e);
        }
    }

    /**
     * Disable chaos testing.
     *
     */
    public void disableChaos() {
        final TestingApi testingApi = getTestingApi();

        try {
            ChaosConfig chaosConfig = ChaosConfig.builder().build();
            testingApi.setChaosParams(convertChaosTriggers(chaosConfig.getChaosTriggers()));
        } catch (ApiException e) {
            rethrow(e);
        }
    }

    /**
     * Converts an internal ChaosTrigger to the OpenAPI Trigger.
     *
     * @param chaosTrigger the internal representation
     * @return the OpenAPI Trigger model
     */
    private Trigger convertTrigger(ChaosTrigger chaosTrigger) {
        Trigger openApiTrigger = new Trigger();
        openApiTrigger.setErrorCode((long) chaosTrigger.getErrorCode());
        openApiTrigger.setProbability((long) chaosTrigger.getProbability());
        return openApiTrigger;
    }

    /**
     * Converts an internal ChaosTriggers to the OpenAPI Triggers.
     *
     * @param chaosTriggers the internal triggers
     * @return the OpenAPI Triggers model
     */
    private Triggers convertChaosTriggers(ChaosTriggers chaosTriggers) {
        Triggers openApiTriggers = new Triggers();
        openApiTriggers.setAuthentication(convertTrigger(chaosTriggers.getAuthentication()));
        openApiTriggers.setRecipient(convertTrigger(chaosTriggers.getRecipient()));
        openApiTriggers.setSender(convertTrigger(chaosTriggers.getSender()));
        return openApiTriggers;
    }

    /**
     * Get application information
     * Returns basic runtime information, message totals and latest release version.
     *
     * @return AppInformation
     */
    public AppInformation getApplicationInfo() {
        final ApplicationApi api = getApplicationApi();
        try {
            return api.appInformation();
        } catch (ApiException e) {
            rethrow(e);
            return null;
        }
    }

    public ApiClient getApiClient() {
        if (this.apiClient == null) {
            this.apiClient = createApiClient();
        }
        return this.apiClient;
    }

    public ApplicationApi getApplicationApi() {
        if (this.applicationApi == null) {
            this.applicationApi = new ApplicationApi(this.getApiClient());
        }
        return this.applicationApi;
    }

    public TestingApi getTestingApi() {
        if (this.testingApi == null) {
            this.testingApi = new TestingApi(this.getApiClient());
        }
        return this.testingApi;
    }

    public MessagesApi getMessagesApi() {
        if (this.messagesApi == null) {
            this.messagesApi = new MessagesApi(this.getApiClient());
        }
        return this.messagesApi;
    }

    public MessageApi getMessageApi() {
        if (this.messageApi == null) {
            this.messageApi = new MessageApi(this.getApiClient());
        }
        return this.messageApi;
    }

    public ApiClient createApiClient() {
        final HttpClient.Builder httpClient = HttpClient.newBuilder();
        final ApiClient client = new ApiClient();
        client.setHttpClientBuilder(httpClient);
        client.setObjectMapper(createDefaultObjectMapper());
        client.updateBaseUri(getMailApiUrl());
        client.setConnectTimeout(Duration.ofSeconds(5));
        client.setReadTimeout(Duration.ofSeconds(30));
        return client;
    }

    /**
     * Use "mailpit.http.server" to get the running Mailpit API Server.
     *
     * @return the mailer URL or an exception if not found
     */
    public String getMailApiUrl() {
        final Optional<String> mailPort = ConfigProvider.getConfig().getOptionalValue("mailpit.http.server",
                String.class);
        return mailPort.orElseThrow(
                () -> new IllegalStateException("Mailer cannot find `mailpit.http.server` so it cannot be used in testing."))
                + ConfigProvider.getConfig().getOptionalValue("MP_WEBROOT", String.class)
                        .orElse("/");
    }

    /**
     * Create the JSON Mapper with some defaults and custom LocalDateTime
     * parsing.
     *
     * @return the {@link ObjectMapper}
     */
    public ObjectMapper createDefaultObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        final JavaTimeModule module = new JavaTimeModule();
        mapper.registerModule(module);
        return mapper;
    }

    private <T extends Throwable> void rethrow(Throwable x) throws T {
        throw (T) x;
    }
}
