package com.booking.replication;

import com.booking.replication.applier.EventApplier;
import com.booking.replication.augmenter.Augmenter;
import com.booking.replication.coordinator.Coordinator;
import com.booking.replication.model.Checkpoint;
import com.booking.replication.model.Event;
import com.booking.replication.streams.Streams;
import com.booking.replication.supplier.EventSupplier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Replicator {
    private static final Logger LOG = Logger.getLogger(Replicator.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private void start(Map<String, String> configuration) {
        try {
            Coordinator coordinator = Coordinator.build(
                    configuration
            );

            EventSupplier supplier = EventSupplier.build(
                    configuration,
                    this.loadCheckpoint(
                            coordinator,
                            configuration
                    )
            );

            EventApplier applier = EventApplier.build(
                    configuration
            );

            Consumer<Event> storeCheckpoint = (event) -> {
                try {
                    this.storeCheckpoint(
                            Checkpoint.of(event),
                            coordinator,
                            configuration
                    );
                } catch (IOException exception) {
                    Replicator.LOG.log(Level.SEVERE, "error storing checkpoint", exception);
                }
            };

            Consumer<Exception> exceptionHandle = (streamsException) -> {
                try {
                    Replicator.LOG.log(Level.SEVERE, "error inside streams", streamsException);
                    Replicator.LOG.log(Level.INFO, "stopping coordinator");

                    coordinator.stop();
                } catch (InterruptedException exception) {
                    Replicator.LOG.log(Level.SEVERE, "error stopping", exception);
                }
            };

//            Augmenter augmenter = Augmenter.build(
//                    configuration
//            );

            Streams<Event, Event> streamsApplier = Streams.<Event>builder()
                    .threads(100)
                    .tasks(100)
                    .fromPush()
                    .to(applier)
                    .build();

            Streams<Event, Event> streamsSupplier = Streams.<Event>builder()
                    .fromPush()
                    //.process(augmenter)
                    .to(streamsApplier::push)
                    .build();

            supplier.onEvent(streamsSupplier::push);

            streamsSupplier.onException(exceptionHandle);
            streamsApplier.onException(exceptionHandle);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Replicator.LOG.log(Level.INFO, "stopping coordinator");

                    coordinator.stop();
                } catch (InterruptedException exception) {
                    Replicator.LOG.log(Level.SEVERE, "error stopping", exception);
                }
            }));

            coordinator.onLeadershipTake(() -> {
                try {
                    Replicator.LOG.log(Level.INFO, "starting replicator");

                    streamsApplier.start();
                    streamsSupplier.start();
                    supplier.start();
                } catch (IOException | InterruptedException exception) {
                    Replicator.LOG.log(Level.SEVERE, "error starting", exception);
                }
            });

            coordinator.onLeadershipLoss(() -> {
                try {
                    Replicator.LOG.log(Level.INFO, "stopping replicator");

                    supplier.stop();
                    streamsSupplier.stop();
                    streamsApplier.stop();
                } catch (IOException | InterruptedException exception) {
                    Replicator.LOG.log(Level.SEVERE, "error stopping", exception);
                }
            });

            Replicator.LOG.log(Level.INFO, "starting coordinator");

            coordinator.start();
            coordinator.join();
        } catch (Exception exception) {
            Replicator.LOG.log(Level.SEVERE, "error executing replicator", exception);
        }
    }

    private Checkpoint loadCheckpoint(Coordinator coordinator, Map<String, String> configuration) throws IOException {
        byte[] checkpointBytes = coordinator.loadCheckpoint(
                configuration.getOrDefault(
                        Coordinator.Configuration.CHECKPOINT_PATH,
                        coordinator.defaultCheckpointPath()
                )
        );

        if (checkpointBytes != null && checkpointBytes.length > 0) {
            return Replicator.MAPPER.readValue(checkpointBytes, Checkpoint.class);
        } else {
            return null;
        }
    }

    private void storeCheckpoint(Checkpoint checkpoint, Coordinator coordinator, Map<String, String> configuration) throws IOException {
        if (checkpoint != null) {
            byte[] checkpointBytes = Replicator.MAPPER.writeValueAsBytes(checkpoint);

            if (checkpointBytes != null && checkpointBytes.length > 0) {
                coordinator.storeCheckpoint(
                        configuration.getOrDefault(
                                Coordinator.Configuration.CHECKPOINT_PATH,
                                coordinator.defaultCheckpointPath()
                        ),
                        checkpointBytes
                );
            }
        }
    }

    /*
     * Start the JVM with the argument -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager
     */
    public static void main(String[] arguments) {
        Options options = new Options();

        options.addOption(Option.builder().longOpt("config").argName("key-value").desc("the configuration to be used with the format <key>=<value>").hasArgs().build());
        options.addOption(Option.builder().longOpt("config-file").argName("filename").desc("the configuration file to be used (YAML)").hasArg().build());
        options.addOption(Option.builder().longOpt("supplier").argName("supplier").desc("the supplier to be used").hasArg().build());
        options.addOption(Option.builder().longOpt("applier").argName("applier").desc("the applier to be used").hasArg().build());

        try {
            CommandLine line = new DefaultParser().parse(options, arguments);

            Map<String, String> configuration = new HashMap<>();

            if (line.hasOption("config")) {
                for (String keyValue : line.getOptionValues("config")) {
                    int index = keyValue.indexOf('=');

                    configuration.put(keyValue.substring(0, index), keyValue.substring(index));
                }
            }

            if (line.hasOption("config-file")) {
                configuration.putAll(Replicator.flattenMap(new ObjectMapper(new YAMLFactory()).readValue(
                        new File(line.getOptionValue("config-file")),
                        new TypeReference<Map<String, Object>>(){}
                )));
            }

            if (line.hasOption("supplier")) {
                configuration.put(EventSupplier.Configuration.TYPE, line.getOptionValue("supplier").toUpperCase());
            }

            if (line.hasOption("applier")) {
                configuration.put(EventApplier.Configuration.TYPE, line.getOptionValue("applier").toUpperCase());
            }

            new Replicator().start(configuration);
        } catch (Exception exception) {
            System.out.println();
        }
    }

    private static Map<String, String> flattenMap(Map<String, Object> map) {
        Map<String, String> flattenMap = new HashMap<>();

        Replicator.flattenMap(null, map, flattenMap);

        return flattenMap;
    }

    @SuppressWarnings("unchecked")
    private static void flattenMap(String path, Map<String, Object> map, Map<String, String> flattenMap) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String flattenPath = (path != null)?String.format("%s.%s", path, entry.getKey()):entry.getKey();

            if (Map.class.isInstance(entry.getValue())) {
                Replicator.flattenMap(flattenPath, Map.class.cast(entry.getValue()), flattenMap);
            } else {
                flattenMap.put(flattenPath, entry.getValue().toString());
            }
        }
    }
}
