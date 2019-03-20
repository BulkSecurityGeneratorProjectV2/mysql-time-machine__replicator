package com.booking.replication.checkpoint;

import com.booking.replication.augmenter.model.event.AugmentedEvent;
import com.booking.replication.augmenter.model.event.AugmentedEventTransaction;
import com.booking.replication.commons.checkpoint.Checkpoint;
import com.booking.replication.commons.checkpoint.CheckpointStorage;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CoordinatorCheckpointApplier implements CheckpointApplier {

    private static final Logger LOG = Logger.getLogger(CoordinatorCheckpointApplier.class.getName());

    private final CheckpointStorage storage;
    private final String path;
    private final boolean transactionEnabled;
    private final AtomicLong lastExecution;
    private final Map<Integer, Long> lastTimestampMap;
    private final Map<Integer, AugmentedEventTransaction> lastTransactionMap;
    private final Map<Integer, Checkpoint> lastCheckpointMap;
    private final ScheduledExecutorService executor;

    public CoordinatorCheckpointApplier(CheckpointStorage storage, String path, long period, boolean transactionEnabled) {
        this.storage = storage;
        this.path = path;
        this.transactionEnabled = transactionEnabled;
        this.lastExecution = new AtomicLong();
        this.lastTimestampMap = new ConcurrentHashMap<>();
        this.lastTransactionMap = new ConcurrentHashMap<>();
        this.lastCheckpointMap = new ConcurrentHashMap<>();
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.executor.scheduleAtFixedRate(() -> {

            Checkpoint minimumCheckpoint = null;

            // TODO: improve this; currently minimumCheckpoint is just checkpoint with minimal timestamp
            for (Map.Entry<Integer, Long> entry : this.lastTimestampMap.entrySet()) {

                if (entry.getValue() > this.lastExecution.get() && this.lastCheckpointMap.containsKey(entry.getKey())) {

                    Checkpoint checkpoint = this.lastCheckpointMap.get(entry.getKey());

                    if (minimumCheckpoint == null ) {
                        minimumCheckpoint = checkpoint;
                    }
                }
            }

            if (minimumCheckpoint != null) {

                System.out.println("checkpointApplierExecutor minimumCheckpoint: " + minimumCheckpoint.getGtidSet());
                try {
                    this.storage.saveCheckpoint(this.path, minimumCheckpoint);
                    CoordinatorCheckpointApplier.LOG.log(Level.INFO, "Last checkpoint: " + minimumCheckpoint.toString());
                    this.lastExecution.set(System.currentTimeMillis());
                } catch (IOException exception) {
                    CoordinatorCheckpointApplier.LOG.log(Level.WARNING, "error saving checkpoint", exception);
                }
            } else {
                System.out.println("No minimum checkpoint");
            }

        }, period, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void accept(AugmentedEvent event, Integer task) {
        Checkpoint checkpoint = event.getHeader().getCheckpoint();
        System.out.println("checkpointApplier: " + checkpoint.getGtidSet());

        AugmentedEventTransaction transaction = event.getHeader().getEventTransaction();

        this.lastTimestampMap.put(task, System.currentTimeMillis());
        if (this.transactionEnabled) {
            if (transaction != null && transaction.compareTo(this.lastTransactionMap.get(task)) > 0) {
                this.lastCheckpointMap.put(task, checkpoint);
                this.lastTransactionMap.put(task, transaction);
            }
        } else {
            this.lastCheckpointMap.put(task, checkpoint);
        }

    }

    @Override
    public void close() {
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
