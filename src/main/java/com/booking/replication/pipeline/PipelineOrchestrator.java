package com.booking.replication.pipeline;

import com.booking.replication.Configuration;
import com.booking.replication.Coordinator;
import com.booking.replication.Metrics;
import com.booking.replication.applier.*;
import com.booking.replication.augmenter.EventAugmenter;


import com.booking.replication.binlog.EventPosition;
import com.booking.replication.binlog.event.QueryEventType;
import com.booking.replication.binlog.event.impl.BinlogEventQuery;
import com.booking.replication.binlog.event.impl.BinlogEventTableMap;
import com.booking.replication.binlog.event.impl.BinlogEventXid;
import com.booking.replication.checkpoints.PseudoGTIDCheckpoint;
import com.booking.replication.exceptions.RowListMessageSerializationException;
import com.booking.replication.pipeline.event.handler.*;

import com.booking.replication.binlog.event.*;

import com.booking.replication.replicant.ReplicantPool;
import com.booking.replication.schema.ActiveSchemaVersion;
import com.booking.replication.schema.column.types.TypeConversionRules;
import com.booking.replication.schema.exception.SchemaTransitionException;
import com.booking.replication.schema.exception.TableMapException;
import com.booking.replication.sql.QueryInspector;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;


/**
 * Pipeline Orchestrator.
 *
 * <p>Manages data flow from event producer into the applier.
 * Also manages persistence of metadata necessary for the replicator features.</p>
 *
 * <p>On each event handles:
 *      1. schema version management
 *      2  augmenting events with schema info
 *      3. sending of events to applier.
 * </p>
 */
public class PipelineOrchestrator extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineOrchestrator.class);
    private static final Meter eventsReceivedCounter = Metrics.registry.meter(name("events", "eventsReceivedCounter"));
    private static final Meter eventsProcessedCounter = Metrics.registry.meter(name("events", "eventsProcessedCounter"));
    private static final Meter eventsSkippedCounter = Metrics.registry.meter(name("events", "eventsSkippedCounter"));
    private static final Meter eventsRewindedCounter = Metrics.registry.meter(name("events", "eventsRewindedCounter"));

    private static final int  BUFFER_FLUSH_INTERVAL = 30000; // <- force buffer flush every 30 sec
    private static final int  DEFAULT_VERSIONS_FOR_MIRRORED_TABLES = 1000;
    private static final long QUEUE_POLL_TIMEOUT = 100L;
    private static final long QUEUE_POLL_SLEEP = 500;

    private static       int numberOfForceFlushesSinceLastEvent = 0;

    private final  BlockingQueue<IBinlogEvent> binlogEventQueue;
    private static EventAugmenter                  eventAugmenter;
    private static ActiveSchemaVersion             activeSchemaVersion;
    private static PseudoGTIDCheckpoint lastVerifiedPseudoGTIDCheckPoint;

    public  final Configuration configuration;
    private final Configuration.OrchestratorConfiguration orchestratorConfiguration;
    private final ReplicantPool replicantPool;
    private final Applier applier;
    private final EventDispatcher eventDispatcher = new EventDispatcher();

    private final PipelinePosition pipelinePosition;
    private final BinlogEventProducer binlogEventProducer;
    public CurrentTransaction currentTransaction = null;

    private volatile boolean running = false;
    private volatile boolean replicatorShutdownRequested = false;


    private HashMap<String, Boolean> rotateEventAllreadySeenForBinlogFile = new HashMap<>();

    /**
     * Fake microsecond counter.
     * <p>
     * <p>This is a special feature that
     * requires some explanation</p>
     * <p>
     * <p>MySQL binlog events have second-precision timestamps. This
     * obviously means that we can't have microsecond precision,
     * but that is not the intention here. The idea is to at least
     * preserve the information about ordering of events,
     * especially if one ID has multiple events within the same
     * second. We want to know what was their order. That is the
     * main purpose of this counter.</p>
     */
    private long fakeMicrosecondCounter = 0L;
    private long previousTimestamp = 0L;
    private long timeOfLastEvent = 0L;
    private boolean isRewinding = false;

    private static Long replDelay = 0L;

    public PipelineOrchestrator(
            LinkedBlockingQueue<IBinlogEvent> binlogEventQueue,
            PipelinePosition pipelinePosition,
            Configuration repcfg,
            ActiveSchemaVersion asv,
            Applier applier,
            ReplicantPool replicantPool,
            BinlogEventProducer binlogEventProducer,
            long fakeMicrosecondCounter
    ) throws SQLException, URISyntaxException {

        this.binlogEventQueue = binlogEventQueue;
        configuration = repcfg;
        orchestratorConfiguration = configuration.getOrchestratorConfiguration();

        this.replicantPool = replicantPool;
        this.fakeMicrosecondCounter = fakeMicrosecondCounter;
        this.binlogEventProducer = binlogEventProducer;
        activeSchemaVersion = asv;

        eventAugmenter = new EventAugmenter(
                activeSchemaVersion,
                configuration.getAugmenterApplyUuid(),
                configuration.getAugmenterApplyXid(),
                new TypeConversionRules(configuration)
        );

        this.applier = applier;

        LOGGER.info("Created consumer with binlog position => { "
                + " binlogFileName => "
                + pipelinePosition.getCurrentPosition().getBinlogFilename()
                + ", binlogPosition => "
                + pipelinePosition.getCurrentPosition().getBinlogPosition()
                + " }"
        );

        this.pipelinePosition = pipelinePosition;

        initEventDispatcher();
    }

    public static void registerMetrics() {
        Metrics.registry.register(MetricRegistry.name("events", "replicatorReplicationDelay"),
                (Gauge<Long>) () -> replDelay);
    }


    private void initEventDispatcher() {
        EventHandlerConfiguration eventHandlerConfiguration = new EventHandlerConfiguration(applier, eventAugmenter, this);

        eventDispatcher.registerHandler(
                BinlogEventType.QUERY_EVENT,
                new QueryEventHandler(eventHandlerConfiguration, activeSchemaVersion, pipelinePosition));

        eventDispatcher.registerHandler(
                BinlogEventType.TABLE_MAP_EVENT,
                new TableMapEventHandler(eventHandlerConfiguration, pipelinePosition, replicantPool));

        eventDispatcher.registerHandler(Arrays.asList(
                BinlogEventType.UPDATE_ROWS_EVENT),
                new UpdateRowsEventHandler(eventHandlerConfiguration));

        eventDispatcher.registerHandler(Arrays.asList(
                BinlogEventType.WRITE_ROWS_EVENT),
                new WriteRowsEventHandler(eventHandlerConfiguration));

        eventDispatcher.registerHandler(Arrays.asList(
                BinlogEventType.DELETE_ROWS_EVENT),
                new DeleteRowsEventHandler(eventHandlerConfiguration));

        eventDispatcher.registerHandler(
                BinlogEventType.XID_EVENT,
                new XidEventHandler(eventHandlerConfiguration));

        eventDispatcher.registerHandler(
                BinlogEventType.FORMAT_DESCRIPTION_EVENT,
                new FormatDescriptionEventHandler(eventHandlerConfiguration));

        eventDispatcher.registerHandler(
                BinlogEventType.ROTATE_EVENT,
                new RotateEventHandler(eventHandlerConfiguration, pipelinePosition, configuration.getLastBinlogFileName()));

        eventDispatcher.registerHandler(
                BinlogEventType.STOP_EVENT,
                new DummyEventHandler());
    }

    public long getFakeMicrosecondCounter() {
        return fakeMicrosecondCounter;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void requestReplicatorShutdown() {
        replicatorShutdownRequested = true;
    }

    public void requestShutdown() {
        setRunning(false);
        requestReplicatorShutdown();
    }

    public boolean isReplicatorShutdownRequested() {
        return replicatorShutdownRequested;
    }

    @Override
    public void run() {

        setRunning(true);
        timeOfLastEvent = System.currentTimeMillis();
        try {
            // block in a loop
            processQueueLoop();

        } catch (SchemaTransitionException e) {
            LOGGER.error("SchemaTransitionException, requesting replicator shutdown...", e);
            requestReplicatorShutdown();
        } catch (InterruptedException e) {
            LOGGER.error("InterruptedException, requesting replicator shutdown...", e);
            requestReplicatorShutdown();
        } catch (TableMapException e) {
            LOGGER.error("TableMapException, requesting replicator shutdown...", e);
            requestReplicatorShutdown();
        } catch (IOException e) {
            LOGGER.error("IOException, requesting replicator shutdown...", e);
            requestReplicatorShutdown();
        } catch (Exception e) {
            LOGGER.error("Exception, requesting replicator shutdown...", e);
            requestReplicatorShutdown();
        }
    }


    private void processQueueLoop() throws Exception {
        processQueueLoop(null);
    }

    private BinlogPositionInfo updateCurrentPipelinePosition(IBinlogEvent event) {

        fakeMicrosecondCounter++;

        BinlogPositionInfo currentPosition = new BinlogPositionInfo(
                replicantPool.getReplicantDBActiveHost(),
                replicantPool.getReplicantDBActiveHostServerID(),
                EventPosition.getEventBinlogFileName(event),
                EventPosition.getEventBinlogPosition(event),
                fakeMicrosecondCounter
        );

        pipelinePosition.setCurrentPosition(currentPosition);

        return currentPosition;
    }

    private void processQueueLoop(BinlogPositionInfo exitOnBinlogPosition) throws Exception {

        while (isRunning()) {

            IBinlogEvent event = waitForEvent(QUEUE_POLL_TIMEOUT, QUEUE_POLL_SLEEP);
            LOGGER.debug("Received event: " + event);

            timeOfLastEvent = System.currentTimeMillis();
            eventsReceivedCounter.mark();

            // Update pipeline position
            fakeMicrosecondCounter++;

            BinlogPositionInfo currentPosition = new BinlogPositionInfo(
                    replicantPool.getReplicantDBActiveHost(),
                    replicantPool.getReplicantDBActiveHostServerID(),
                    EventPosition.getEventBinlogFileName(event),
                    EventPosition.getEventBinlogPosition(event),
                    fakeMicrosecondCounter
            );
            pipelinePosition.setCurrentPosition(currentPosition);
            // BinlogPositionInfo currentPosition = updateCurrentPipelinePosition(event);

            processEvent(event);

            if (exitOnBinlogPosition != null && currentPosition.compareTo(exitOnBinlogPosition) == 0) {
                LOGGER.debug("currentTransaction: " + currentTransaction);
                break;
            }
            if (currentTransaction != null && currentTransaction.isRewinded()) {
                currentTransaction.setEventsTimestampToFinishEvent();
                applyTransactionDataEvents();
            }
        }
    }

    private void processEvent(IBinlogEvent event) throws Exception {
        
        if (skipEvent(event)) {
            LOGGER.debug("Skipping event: " + event);
            eventsSkippedCounter.mark();
        } else {
            LOGGER.debug("Processing event: " + event);
            calculateAndPropagateChanges(event);
            eventsProcessedCounter.mark();
        }
    }

    private IBinlogEvent rewindToCommitEvent()
        throws
            ApplierException,
            IOException,
            InterruptedException,
            RowListMessageSerializationException {
        return rewindToCommitEvent(QUEUE_POLL_TIMEOUT, QUEUE_POLL_SLEEP);
    }

    private IBinlogEvent rewindToCommitEvent(long timeout, long sleep)
            throws ApplierException, IOException, InterruptedException, RowListMessageSerializationException {

        LOGGER.debug("Rewinding to the next commit event. Either XidEvent or QueryEvent with COMMIT statement");

        IBinlogEvent resultEvent = null;

        // Fast Rewind:
        while (isRunning() && resultEvent == null) {

            IBinlogEvent event = waitForEvent(timeout, sleep);
            if (event == null) continue;

            eventsRewindedCounter.mark();

            moveFakeMicrosecondCounter(event.getTimestamp());

            switch (event.getEventType()) {

                case XID_EVENT:
                    resultEvent = event;
                    break;

                case QUERY_EVENT:
                    if (QueryInspector.getQueryEventType((BinlogEventQuery) event).equals(QueryEventType.COMMIT)) {
                        resultEvent = event;
                    }
                    break;

                default:
                    LOGGER.debug("Skipping event due to rewinding: " + event);
                    break;
            }
        }
        LOGGER.debug("Rewinded to the position: " + EventPosition.getEventBinlogFileNameAndPosition(resultEvent) + ", event: " + resultEvent);
        doTimestampOverride(resultEvent);
        return resultEvent;
    }

    private IBinlogEvent waitForEvent(long timeout, long sleep) throws InterruptedException, ApplierException, IOException, RowListMessageSerializationException {
        while (isRunning()) {

            if (binlogEventQueue.size() > 0) {
                IBinlogEvent binlogEvent = this.binlogEventQueue.poll(timeout, TimeUnit.MILLISECONDS);

                if (binlogEvent == null) {
                    LOGGER.warn("Poll timeout. Will sleep for " + QUEUE_POLL_SLEEP * 2  + "ms and try again.");
                    Thread.sleep(sleep * 2);
                    continue;
                }

                return binlogEvent;

            } else {
                LOGGER.debug("Pipeline report: no items in producer event rawQueue. Will sleep for " + QUEUE_POLL_SLEEP + " and check again.");
                Thread.sleep(sleep);
                long currentTime = System.currentTimeMillis();
                long timeDiff = currentTime - timeOfLastEvent;

                // Force flush if more than 30 seconds passed since last event and there was no flush since then
                boolean forceFlush = (timeDiff > BUFFER_FLUSH_INTERVAL) && (numberOfForceFlushesSinceLastEvent == 0);
                if (forceFlush ) {
                    applier.forceFlush();
                    numberOfForceFlushesSinceLastEvent++;
                }
            }
        }
        return null;
    }

    private void moveFakeMicrosecondCounter(long timestamp) {

        if (fakeMicrosecondCounter > 999998L) {
            fakeMicrosecondCounter = 0L;
            LOGGER.warn("Fake microsecond counter's overflowed, resetting to 0. It might lead to incorrect events order.");
        }

        if (timestamp > previousTimestamp) {
            fakeMicrosecondCounter = 0L;
            previousTimestamp = timestamp;
        }
    }

    /**
     *  Calculate and propagate changes.
     *
     *  <p>STEPS:
     *     ======
     *  1. check event type
     *
     *  2. if DDL:
     *      a. pass to eventAugmenter which will update the schema
     *
     *  3. if DATA:
     *      a. match column names and types
     * </p>
     */
    private void calculateAndPropagateChanges(IBinlogEvent event) throws Exception {

        // Calculate replication delay before the event timestamp is extended with fake microsecond part
        // Note: there is a bug in open replicator which results in rotate event having timestamp value = 0.
        //       This messes up the replication delay time series. The workaround is not to calculate the
        //       replication delay at rotate event.
        if (event.hasHeader()) {
            if ((event.getTimestampOfReceipt() > 0)
                    && (event.getTimestamp() > 0) ) {
                replDelay = event.getTimestampOfReceipt() - event.getTimestamp();
            } else {
                if (!event.isRotate()) {
                    // warn, not expected for other (non-rotate) events
                    LOGGER.warn("Invalid timestamp value for event " + event.toString());
                }
            }
        } else {
            LOGGER.error("Event header can not be null. Shutting down...");
            requestReplicatorShutdown();
        }

        // check if the applier commit stream moved to a new check point. If so, store the the new safe check point.
        PseudoGTIDCheckpoint lastCommittedPseudoGTIDReportedByApplier =
            applier.getLastCommittedPseudGTIDCheckPoint();

        if (lastVerifiedPseudoGTIDCheckPoint == null && lastCommittedPseudoGTIDReportedByApplier != null) {

            lastVerifiedPseudoGTIDCheckPoint = lastCommittedPseudoGTIDReportedByApplier;

            Coordinator.saveCheckpointMarker(lastVerifiedPseudoGTIDCheckPoint);

            LOGGER.info("Saved new checkpoint: " + lastVerifiedPseudoGTIDCheckPoint.toJson());

        } else if (lastVerifiedPseudoGTIDCheckPoint != null && lastCommittedPseudoGTIDReportedByApplier != null) {

            if (lastVerifiedPseudoGTIDCheckPoint.isBeforeCheckpoint(lastCommittedPseudoGTIDReportedByApplier)) {

                LOGGER.info("Reached new safe checkpoint " + lastCommittedPseudoGTIDReportedByApplier.getPseudoGTID());

                lastVerifiedPseudoGTIDCheckPoint = lastCommittedPseudoGTIDReportedByApplier;

                Coordinator.saveCheckpointMarker(lastVerifiedPseudoGTIDCheckPoint);

                LOGGER.info("Saved new checkpoint: " + lastVerifiedPseudoGTIDCheckPoint.toJson());

            }
        }

        moveFakeMicrosecondCounter(event.getTimestamp());
        doTimestampOverride(event);

        // Process Event
        try {
            eventDispatcher.handle(event);
        } catch (TransactionSizeLimitException e) {
            LOGGER.info("Transaction size limit(" + orchestratorConfiguration.getRewindingThreshold() + ") exceeded. Applying with rewinding uuid: " + currentTransaction.getUuid());
            applyTransactionWithRewinding();
        } catch (TransactionException e) {
            LOGGER.error("EventManger failed to handle event: ", e);
            requestShutdown();
        }
    }

    private boolean isReplicant(String schemaName) {
        return schemaName.equals(configuration.getReplicantSchemaName());
    }

    /**
     * Returns true if event type is not tracked, or does not belong to the
     * tracked database.
     *
     * @param  event Binlog event that needs to be checked
     * @return shouldSkip Weather event should be skipped or processed
     */
    public boolean skipEvent(IBinlogEvent event) throws Exception {

        boolean eventIsTracked      = false;

        // if there is a last safe checkpoint, skip events that are before
        // or equal to it, so that the same events are not written multiple
        // times (beside wasting IO, this would fail the DDL operations,
        // for example trying to create a table that already exists)
        if (pipelinePosition.getLastSafeCheckPointPosition() != null) {
            if ((pipelinePosition.getLastSafeCheckPointPosition().greaterThan(pipelinePosition.getCurrentPosition()))
                    || (pipelinePosition.getLastSafeCheckPointPosition().equals(pipelinePosition.getCurrentPosition()))) {
                LOGGER.info("Event position { binlog-filename => "
                        + pipelinePosition.getCurrentPosition().getBinlogFilename()
                        + ", binlog-position => "
                        + pipelinePosition.getCurrentPosition().getBinlogPosition()
                        + " } is lower or equal then last safe checkpoint position { "
                        + " binlog-filename => "
                        + pipelinePosition.getLastSafeCheckPointPosition().getBinlogFilename()
                        + ", binlog-position => "
                        + pipelinePosition.getLastSafeCheckPointPosition().getBinlogPosition()
                        + " }. Skipping event...");
                return true;
            }
        }

        BinlogEventType binlogEventType = event.getEventType();
        switch (binlogEventType) {

            // Query Event:
            case QUERY_EVENT:

                switch (QueryInspector.getQueryEventType((BinlogEventQuery) event)) {
                    case BEGIN:
                    case PSEUDOGTID:
                        return false;
                    case COMMIT:
                        LOGGER.debug("Got commit event: " + event);

                        // TODO: port currentTransaction to binlog connect
                        // BinlogEventTableMap firstMapEvent = currentTransactionMetadata.getFirstMapEventInTransaction();
                        BinlogEventTableMap firstMapEvent = currentTransaction.getFirstMapEventInTransaction();
                        // ----------------------------------------------------------------------------------
                        // Handle empty transactions:
                        //
                        // Transactions from non-replicated schemas are empty since their events are skipped.
                        // If however we encounter an empty transaction for the replicated schema than a WARN
                        // should be logged since this is not expected.
                        if (firstMapEvent == null) {
                            String schemaName = ((BinlogEventQuery) event).getDatabaseName().toString();
                            if (isReplicant(schemaName)) {
                                LOGGER.warn(
                                    String.format(
                                        "Received COMMIT event for the replicated schema, but currentTransaction is empty! Position of COMMIT %s",
                                        ((BinlogEventQuery) event).getBinlogFilename() + ":" + ((BinlogEventQuery) event).getPosition()
                                    )
                                );
                            } else {
                                LOGGER.debug(
                                        String.format(
                                                "Received COMMIT event for the non-replicated schema %s and currentTransaction is empty as expected.",
                                                schemaName
                                        )
                                );
                            }
                            dropTransaction();
                            return true;
                        }
                        return false;
                    case DDLTABLE:
                        // DDL event should always contain db name
                        String dbName = ((BinlogEventQuery) event).getDatabaseName().toString();
                        if (dbName.length() == 0) {
                            LOGGER.warn("No Db name in Query Event. Extracted SQL: " + ((BinlogEventQuery) event).getSql().toString());
                        }
                        if (isReplicant(dbName)) {
                            // process event
                            return false;
                        }
                        // skip event
                        LOGGER.warn("DDL statement " + ((BinlogEventQuery) event).getSql() + " on non-replicated database: " + dbName + "");
                        return true;
                    case DDLVIEW:
                        // TODO: handle View statement
                        return true;
                    case ANALYZE:
                        return true;
                    default:
                        LOGGER.warn("Skipping event with unknown query type: " + ((BinlogEventQuery) event).getSql());
                        return true;
                }

            // TableMap event:
            case TABLE_MAP_EVENT:
                return !isReplicant(((BinlogEventTableMap) event).getDatabaseName().toString());

            // Data event:
            case UPDATE_ROWS_EVENT:
            case WRITE_ROWS_EVENT:
            case DELETE_ROWS_EVENT:
                return currentTransaction.getFirstMapEventInTransaction() == null;

            // Xid:
            case XID_EVENT:
                return false;

            case ROTATE_EVENT:
                // This is a  workaround for a bug in open replicator
                // which results in rotate event being created twice per
                // binlog file - once at the end of the binlog file (as it should be)
                // and once at the beginning of the next binlog file (which is a bug)
                String currentBinlogFile =
                        pipelinePosition.getCurrentPosition().getBinlogFilename();
                if (rotateEventAllreadySeenForBinlogFile.containsKey(currentBinlogFile)) {
                    return true;
                }
                rotateEventAllreadySeenForBinlogFile.put(currentBinlogFile, true);
                return false;

            case FORMAT_DESCRIPTION_EVENT:
            case STOP_EVENT:
                return false;

            default:
                LOGGER.warn("Unexpected event type => " + event.getEventType());
                return true;
        }
    }

    public boolean beginTransaction() {
        // a manual transaction beginning
        if (currentTransaction != null) {
            return false;
        }
        currentTransaction = new CurrentTransaction();
        LOGGER.debug("Started transaction " + currentTransaction.getUuid() + " without event");
        return true;
    }

    public boolean beginTransaction(BinlogEventQuery event) {
        // begin a transaction with BEGIN query event
        if (currentTransaction != null) {
            return false;
        }
        currentTransaction = new CurrentTransaction(event);
        LOGGER.debug("Started transaction " + currentTransaction.getUuid() + " with event: " + event);
        return true;
    }

    public void addEventIntoTransaction(IBinlogEvent event) throws TransactionException, TransactionSizeLimitException {
        if (!isInTransaction()) {
            throw new TransactionException("Failed to add new event into a transaction buffer while not in transaction: " + event);
        }

        if (isTransactionSizeLimitExceeded()) {
            throw new TransactionSizeLimitException();
        }

        currentTransaction.addEvent(event);

        if (currentTransaction.getEventsCounter() % 10 == 0) {
            LOGGER.debug("Number of events in current transaction " + currentTransaction.getUuid() + " is: " + currentTransaction.getEventsCounter());
        }
    }

    private void applyTransactionWithRewinding() throws Exception {
        LOGGER.debug("Applying transaction with rewinding");
        if (isRewinding) {
            throw new RuntimeException("Recursive rewinding detected. CurrentTransaction:" + currentTransaction);
        }
        BinlogEventQuery beginEvent = currentTransaction.getBeginEvent();
        LOGGER.debug("Start rewinding transaction from: " + EventPosition.getEventBinlogFileNameAndPosition(beginEvent));

        isRewinding = true;
        currentTransaction.setRewinded(true);

        // drop data events from current transaction
        currentTransaction.clearEvents();

        // get next xid event and skip everything before
        IBinlogEvent commitEvent = rewindToCommitEvent();
        if (commitEvent.getEventType() == BinlogEventType.XID_EVENT) {
            currentTransaction.setFinishEvent((BinlogEventXid) commitEvent);
        } else {
            currentTransaction.setFinishEvent((BinlogEventQuery) commitEvent);
        }

        // set binlog pos to begin pos, start openReplicator and apply the xid data to all events
        try {

           binlogEventProducer.rewindHead(
                   EventPosition.getEventBinlogFileName(beginEvent),
                   EventPosition.getEventBinlogNextPosition(beginEvent)
           );

           // binlogEventProducer.stopAndClearQueue(10000, TimeUnit.MILLISECONDS);

           // binlogEventProducer.setBinlogFileName(EventPosition.getEventBinlogFileName(beginEvent));

           // LOGGER.info("restarting producer from beginEvent position " + EventPosition.getEventBinlogNextPosition(beginEvent));
           // binlogEventProducer.setBinlogPosition(EventPosition.getEventBinlogNextPosition(beginEvent));

           // binlogEventProducer.start();
        } catch (Exception e) {
            throw new BinlogEventProducerException("Can't stop binlogEventProducer to rewind a stream to the end of a transaction: ");
        }

        // apply begin event before data events
        applyTransactionBeginEvent();

        // apply data events
        processQueueLoop(
                new BinlogPositionInfo(
                        replicantPool.getReplicantDBActiveHostServerID(),
                        EventPosition.getEventBinlogFileName(commitEvent),
                        EventPosition.getEventBinlogPosition(commitEvent)
                ));

        if (!isRunning()) return;

        // at this point transaction must be committed by xidEvent which we rewinded to and the commit events must be applied
        if (currentTransaction != null) {
            throw new TransactionException("Transaction must be already committed at this point: " + currentTransaction);
        }

        isRewinding = false;

        LOGGER.debug("Stop rewinding transaction at: " + EventPosition.getEventBinlogFileNameAndPosition(commitEvent));
    }

    public boolean isInTransaction() {
        return (currentTransaction != null);
    }

    public void commitTransaction(long timestamp, long xid) throws TransactionException {
        // manual transaction commit
        currentTransaction.setXid(xid);
        if (currentTransaction.hasBeginEvent()) currentTransaction.setBeginEventTimestamp(timestamp);
        currentTransaction.setEventsTimestamp(timestamp);
        commitTransaction();
    }

    public void commitTransaction(BinlogEventXid xidEvent) throws TransactionException {
        currentTransaction.setFinishEvent(xidEvent);
        commitTransaction();
    }

    public void commitTransaction(BinlogEventQuery queryEvent) throws TransactionException {
        currentTransaction.setFinishEvent(queryEvent);
        commitTransaction();
    }

    private void commitTransaction() throws TransactionException {

        // apply all the buffered events
        LOGGER.debug("/ transaction uuid: " + currentTransaction.getUuid() + ", id: " + currentTransaction.getXid());

        // apply changes from buffer and pass current metadata with xid and uuid
        try {
            if (isRewinding) {
                applyTransactionFinishEvent();
            } else {
                if (isEmptyTransaction()) {
                    LOGGER.debug("Transaction is empty");
                    dropTransaction();
                    return;
                }
                applyTransactionBeginEvent();
                applyTransactionDataEvents();
                applyTransactionFinishEvent();
            }
        } catch (EventHandlerApplyException e) {
            LOGGER.error("Failed to commit transaction: " + currentTransaction, e);
            requestShutdown();
        }
        LOGGER.debug("Transaction committed uuid: " + currentTransaction.getUuid() + ", id: " + currentTransaction.getXid());
        currentTransaction = null;
    }

    private void applyTransactionBeginEvent() throws EventHandlerApplyException, TransactionException {
        // apply begin event
        if (currentTransaction.hasBeginEvent()) {
            currentTransaction.setBeginEventTimestampToFinishEvent();
            eventDispatcher.apply(currentTransaction.getBeginEvent(), currentTransaction);
        }
    }

    private void applyTransactionDataEvents() throws EventHandlerApplyException, TransactionException {
        // apply data-changing events
        if (currentTransaction.hasFinishEvent())    currentTransaction.setEventsTimestampToFinishEvent();
        for (IBinlogEvent event : currentTransaction.getEvents()) {
            eventDispatcher.apply(event, currentTransaction);
        }
        currentTransaction.clearEvents();
    }

    private void applyTransactionFinishEvent() throws EventHandlerApplyException, TransactionException {
        // apply commit event
        if (currentTransaction.hasFinishEvent()) {
            eventDispatcher.apply(currentTransaction.getFinishEvent(), currentTransaction);
        }
    }

    private boolean isEmptyTransaction() {
        return (currentTransaction.hasFinishEvent() && !currentTransaction.hasEvents());
    }

    private void dropTransaction() {
        LOGGER.debug("Transaction dropped");
        currentTransaction = null;
    }

    public CurrentTransaction getCurrentTransaction() {
        return currentTransaction;
    }

    private boolean isTransactionSizeLimitExceeded() {
        return configuration.getOrchestratorConfiguration().isRewindingEnabled()
                && (currentTransaction.getEventsCounter() > orchestratorConfiguration.getRewindingThreshold());
    }

    private void doTimestampOverride(IBinlogEvent event) {
        if (configuration.isInitialSnapshotMode()) {
            doInitialSnapshotEventTimestampOverride(event);
        } else {
            injectFakeMicroSecondsIntoEventTimestamp(event);
        }
    }

    private void injectFakeMicroSecondsIntoEventTimestamp(IBinlogEvent binlogEvent) {

        long overriddenTimestamp = binlogEvent.getTimestamp();

        if (overriddenTimestamp != 0) {
            // timestamp is in millisecond form, but the millisecond part is actually 000 (for example 1447755881000)
            // TODO: this is the case in open replicator; verify it works the same in binlog connector
            overriddenTimestamp = (overriddenTimestamp * 1000) + fakeMicrosecondCounter;
            binlogEvent.overrideTimestamp(overriddenTimestamp);
        }
    }

    // set initial snapshot time to unix epoch.
    private void doInitialSnapshotEventTimestampOverride(IBinlogEvent binlogEvent) {

        long overriddenTimestamp = binlogEvent.getTimestamp();

        if (overriddenTimestamp != 0) {
            overriddenTimestamp = 0;
           binlogEvent.overrideTimestamp(overriddenTimestamp);
        }
    }
}
