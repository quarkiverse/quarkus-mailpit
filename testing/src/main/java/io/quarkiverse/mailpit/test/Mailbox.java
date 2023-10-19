package io.quarkiverse.mailpit.test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.quarkiverse.mailpit.test.invoker.ApiClient;
import io.quarkiverse.mailpit.test.invoker.ApiException;
import io.quarkiverse.mailpit.test.model.AppInformation;
import io.quarkiverse.mailpit.test.model.DeleteRequest;
import io.quarkiverse.mailpit.test.model.Message;
import io.quarkiverse.mailpit.test.model.MessageSummary;
import io.quarkiverse.mailpit.test.model.MessagesSummary;
import io.quarkiverse.mailpit.test.rest.ApplicationApi;
import io.quarkiverse.mailpit.test.rest.MessageApi;
import io.quarkiverse.mailpit.test.rest.MessagesApi;

/**
 * Injected MailContext wrapping the API to Mailpit for unit testing.
 */
public class Mailbox {
    private ApiClient apiClient;
    private ApplicationApi applicationApi;
    private MessagesApi messagesApi;
    private MessageApi messageApi;

    /**
     * Delete a single message.
     *
     * @param ID Database ID to delete
     * @throws ApiException if fails to make API call
     */
    public void delete(String ID) throws ApiException {
        final MessagesApi messagesApi = getMessagesApi();
        final DeleteRequest request = new DeleteRequest();
        request.addIdsItem(ID);
        messagesApi.delete(request);
    }

    /**
     * Delete all messages.
     *
     * @throws ApiException if fails to make API call
     */
    public void clear() throws ApiException {
        final MessagesApi messagesApi = getMessagesApi();
        final DeleteRequest request = new DeleteRequest();
        messagesApi.delete(request);
    }

    /**
     * Search messages. Returns the latest messages matching a search.
     *
     * @param query Search query (required)
     * @param start Pagination offset (optional, default to 0)
     * @param limit Limit results (optional, default to 50)
     * @return List<Message>
     * @throws ApiException if fails to make API call
     */
    public List<Message> find(String query, Integer start, Integer limit) throws ApiException {
        final List<Message> results = new ArrayList<>();
        final MessagesApi messagesApi = getMessagesApi();
        final MessageApi messageApi = getMessageApi();
        final MessagesSummary messages = messagesApi.messagesSummary(query, start, limit);
        for (MessageSummary summary : messages.getMessages()) {
            Message message = messageApi.message(summary.getID());
            results.add(message);
        }

        return results;
    }

    /**
     * Search messages. Returns the first message matching a search.
     *
     * @param query Search query (required)
     * @return Message
     * @throws ApiException if fails to make API call
     */
    public Message findFirst(String query) throws ApiException {
        final List<Message> results = find(query, 0, 1);
        if (results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    /**
     * Get application information
     * Returns basic runtime information, message totals and latest release version.
     *
     * @return AppInformation
     * @throws ApiException if fails to make API call
     */
    public AppInformation getApplicationInfo() throws ApiException {
        final ApplicationApi api = getApplicationApi();
        return api.appInformation();
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
                () -> new IllegalStateException("Mailer cannot find `mailpit.http.server` so it cannot be used in testing."));
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
}