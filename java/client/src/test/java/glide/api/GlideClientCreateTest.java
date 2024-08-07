/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.api.BaseClient.buildChannelHandler;
import static glide.api.BaseClient.buildMessageHandler;
import static glide.api.GlideClient.buildCommandManager;
import static glide.api.GlideClient.buildConnectionManager;
import static glide.api.GlideClient.createClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.exceptions.ClosingException;
import glide.connectors.handlers.ChannelHandler;
import glide.connectors.handlers.MessageHandler;
import glide.connectors.resources.ThreadPoolResource;
import glide.connectors.resources.ThreadPoolResourceAllocator;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

public class GlideClientCreateTest {

    private MockedStatic<BaseClient> mockedClient;
    private ChannelHandler channelHandler;
    private ConnectionManager connectionManager;
    private CommandManager commandManager;
    private MessageHandler messageHandler;
    private ThreadPoolResource threadPoolResource;

    @BeforeEach
    public void init() {
        mockedClient = mockStatic(BaseClient.class);

        channelHandler = mock(ChannelHandler.class);
        commandManager = mock(CommandManager.class);
        connectionManager = mock(ConnectionManager.class);
        messageHandler = mock(MessageHandler.class);
        threadPoolResource = mock(ThreadPoolResource.class);

        mockedClient.when(() -> buildChannelHandler(any(), any())).thenReturn(channelHandler);
        mockedClient.when(() -> buildConnectionManager(channelHandler)).thenReturn(connectionManager);
        mockedClient.when(() -> buildCommandManager(channelHandler)).thenReturn(commandManager);
        mockedClient.when(() -> buildMessageHandler(any())).thenReturn(messageHandler);
        mockedClient.when(() -> createClient(any(), any())).thenCallRealMethod();

        var threadPoolResource = ThreadPoolResourceAllocator.getOrCreate(() -> null);
        if (threadPoolResource != null) {
            threadPoolResource.getEventLoopGroup().shutdownGracefully();
            ThreadPoolResourceAllocator.getOrCreate(() -> null);
        }
    }

    @AfterEach
    public void teardown() {
        mockedClient.close();
    }

    @Test
    @SneakyThrows
    public void createClient_with_default_config_successfully_returns_GlideClient() {
        // setup
        CompletableFuture<Void> connectToValkeyFuture = new CompletableFuture<>();
        connectToValkeyFuture.complete(null);
        GlideClientConfiguration config = GlideClientConfiguration.builder().build();

        when(connectionManager.connectToValkey(eq(config))).thenReturn(connectToValkeyFuture);

        // exercise
        CompletableFuture<GlideClient> result = createClient(config);
        GlideClient client = result.get();

        // verify
        assertEquals(connectionManager, client.connectionManager);
        assertEquals(commandManager, client.commandManager);
    }

    @Test
    @SneakyThrows
    public void createClient_with_custom_config_successfully_returns_GlideClient() {
        // setup
        CompletableFuture<Void> connectToValkeyFuture = new CompletableFuture<>();
        connectToValkeyFuture.complete(null);
        GlideClientConfiguration config =
                GlideClientConfiguration.builder().threadPoolResource(threadPoolResource).build();

        when(connectionManager.connectToValkey(eq(config))).thenReturn(connectToValkeyFuture);

        // exercise
        CompletableFuture<GlideClient> result = createClient(config);
        GlideClient client = result.get();

        // verify
        assertEquals(connectionManager, client.connectionManager);
        assertEquals(commandManager, client.commandManager);
    }

    @SneakyThrows
    @Test
    public void createClient_error_on_connection_throws_ExecutionException() {
        // setup
        CompletableFuture<Void> connectToValkeyFuture = new CompletableFuture<>();
        ClosingException exception = new ClosingException("disconnected");
        connectToValkeyFuture.completeExceptionally(exception);
        GlideClientConfiguration config =
                GlideClientConfiguration.builder().threadPoolResource(threadPoolResource).build();

        when(connectionManager.connectToValkey(eq(config))).thenReturn(connectToValkeyFuture);

        // exercise
        CompletableFuture<GlideClient> result = createClient(config);

        ExecutionException executionException = assertThrows(ExecutionException.class, result::get);

        // verify
        assertEquals(exception, executionException.getCause());
    }

    // TODO check message queue and subscriptionConfiguration
}
