package com.booking.replication.supplier.mysql.binlog;

import com.booking.replication.commons.checkpoint.Checkpoint;
import com.booking.replication.supplier.model.RawEvent;
import com.booking.replication.supplier.Supplier;
import com.booking.replication.supplier.mysql.binlog.handler.RawEventInvocationHandler;
import com.github.shyiko.mysql.binlog.BinaryLogClient;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BinaryLogSupplier implements Supplier {
    private static final Logger LOG = Logger.getLogger(BinaryLogSupplier.class.getName());

    public interface Configuration {
        String MYSQL_HOSTNAME = "mysql.hostname";
        String MYSQL_PORT = "mysql.port";
        String MYSQL_SCHEMA = "mysql.schema";
        String MYSQL_USERNAME = "mysql.username";
        String MYSQL_PASSWORD = "mysql.password";
    }

    private final ExecutorService executor;
    private final AtomicBoolean running;

    private final List<String> hostname;
    private final int port;
    private final String schema;
    private final String username;
    private final String password;

    private BinaryLogClient client;
    private Consumer<RawEvent> consumer;
    private Consumer<Exception> handler;

    public BinaryLogSupplier(Map<String, Object> configuration) {
        Object hostname = configuration.get(Configuration.MYSQL_HOSTNAME);
        Object port = configuration.getOrDefault(Configuration.MYSQL_PORT, "3306");
        Object schema = configuration.get(Configuration.MYSQL_SCHEMA);
        Object username = configuration.get(Configuration.MYSQL_USERNAME);
        Object password = configuration.get(Configuration.MYSQL_PASSWORD);

        Objects.requireNonNull(hostname, String.format("Configuration required: %s", Configuration.MYSQL_HOSTNAME));
        Objects.requireNonNull(schema, String.format("Configuration required: %s", Configuration.MYSQL_SCHEMA));
        Objects.requireNonNull(username, String.format("Configuration required: %s", Configuration.MYSQL_USERNAME));
        Objects.requireNonNull(password, String.format("Configuration required: %s", Configuration.MYSQL_PASSWORD));

        this.executor = Executors.newSingleThreadExecutor();
        this.running = new AtomicBoolean(false);

        this.hostname = this.getList(hostname);
        this.port = Integer.parseInt(port.toString());
        this.schema = schema.toString();
        this.username = username.toString();
        this.password = password.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(Object object) {
        if (List.class.isInstance(object)) {
            return (List<String>) object;
        } else {
            return Collections.singletonList(object.toString());
        }
    }

    private BinaryLogClient getClient(String hostname) {
        // TODO: Implement status variable parser: https://github.com/shyiko/mysql-binlog-connector-java/issues/174
        return new BinaryLogClient(hostname, this.port, this.schema, this.username, this.password);
    }

    @Override
    public void onEvent(Consumer<RawEvent> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onException(Consumer<Exception> handler) {
        this.handler = handler;
    }

    @Override
    public void start(Checkpoint checkpoint) {
        if (!this.running.getAndSet(true)) {
            this.connect(checkpoint);
        }
    }

    @Override
    public void connect(Checkpoint checkpoint) {
        if (this.client == null || !this.client.isConnected()) {
            this.executor.submit(() -> {
                for (String hostname : this.hostname) {
                    try {
                        this.client = this.getClient(hostname);

                        if (this.consumer != null) {
                            this.client.registerEventListener(
                                    event -> {
                                        try {
                                            this.consumer.accept(RawEvent.getRawEventProxy(new RawEventInvocationHandler(event)));
                                        } catch (ReflectiveOperationException exception) {
                                            throw new RuntimeException(exception);
                                        }
                                    }
                            );
                        }

                        if (checkpoint != null) {
                            this.client.setGtidSet(null);
                            this.client.setServerId(checkpoint.getServerId());
                            this.client.setBinlogFilename(checkpoint.getBinlogFilename());
                            this.client.setBinlogPosition(checkpoint.getBinlogPosition());
                        }

                        this.client.connect();

                        return;
                    } catch (IOException exception) {
                        BinaryLogSupplier.LOG.log(Level.WARNING, String.format("error connecting to %s, falling over to the next one", hostname), exception);
                    }
                }

                if (this.handler != null) {
                    this.handler.accept(new IOException("error connecting"));
                }
            });
        }
    }

    @Override
    public void disconnect() {
        if (this.client != null && this.client.isConnected()) {
            try {
                this.client.disconnect();
                this.client = null;
            } catch (IOException exception) {
                BinaryLogSupplier.LOG.log(Level.SEVERE, "error disconnecting", exception);
            }
        }
    }

    @Override
    public void stop() {
        if (this.running.getAndSet(false)) {
            this.disconnect();

            try {
                this.executor.shutdown();
                this.executor.awaitTermination(5L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                throw new RuntimeException(exception);
            } finally {
                this.executor.shutdownNow();
            }
        }
    }
}
