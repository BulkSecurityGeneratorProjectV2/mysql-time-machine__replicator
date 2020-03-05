package com.booking.replication.applier.hbase.mutation;

import com.booking.replication.applier.hbase.HBaseApplier;

import com.booking.replication.applier.hbase.schema.HBaseRowKeyMapper;
import com.booking.replication.applier.validation.ValidationService;
import com.booking.replication.augmenter.model.AugmenterModel;
import com.booking.replication.augmenter.model.event.AugmentedEventType;
import com.booking.replication.augmenter.model.format.EventDeserializer;
import com.booking.replication.augmenter.model.row.AugmentedRow;
import com.booking.replication.commons.metrics.Metrics;
import com.booking.validator.data.source.DataSource;
import com.booking.validator.data.source.Types;
import com.booking.validator.data.source.bigtable.BigtableQueryOptions;
import com.booking.validator.data.transformation.TransformationTypes;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;
import com.booking.validator.data.source.mysql.MysqQuerylOptions;

/**
 * This class generates HBase mutations and keys
 */
public class HBaseApplierMutationGenerator {

    private static final Logger LOGGER = LogManager.getLogger(HBaseApplierMutationGenerator.class);

    private final Metrics<?> metrics;

    private String shardName;

    private HashMap<String, Object> sourceTransformations;

    private HashMap<String, Object> targetTransformations;

    public class PutMutation {

        private final Put put;
        private final String table;
        private final String sourceRowUri;
        private final DataSource sourceDataSource;
        private final String transactionUUID;

        public PutMutation(Put put, String table, String sourceRowUri, DataSource sourceDataSource, String transactionUUID) {
            this.put = put;
            this.sourceRowUri = sourceRowUri;
            this.sourceDataSource = sourceDataSource;
            this.table = table;
            this.transactionUUID = transactionUUID;
        }

        public Put getPut() {
            return put;
        }

        public String getSourceRowUri() {
            return sourceRowUri;
        }
        public DataSource getSourceDataSource() {
            return sourceDataSource;
        }

        public String getTable() {
            return table;
        }

        public String getTransactionUUID() {
            return transactionUUID;
        }

        public String getTargetRowUri() {

            // TODO: config
            // if (configuration.validationConfig == null) return null;
            // targetDomain <- configuration.getValidationConfiguration().getTargetDomain();
            String targetDomain = "hbase-cluster";
            try {

                String dataSource = targetDomain;

                String row = URLEncoder.encode(Bytes.toStringBinary(put.getRow()), "UTF-8");

                String cf = URLEncoder.encode(Bytes.toString(CF), "UTF-8");

                return String.format("hbase://%s/%s?row=%s&cf=%s", dataSource, table, row, cf);

            } catch (UnsupportedEncodingException e) {
                LOGGER.error("UTF-8 not supported?", e);
                return null;
            }
        }

        public DataSource getTargetDataSource() {
            Object targetDataSource = configuration.getOrDefault(ValidationService.Configuration.VALIDATION_TARGET_DATA_SOURCE, null);
            String row = null;
            String cf = null;
            try {
                row = URLEncoder.encode(Bytes.toStringBinary(put.getRow()), "UTF-8");
                cf = URLEncoder.encode(Bytes.toString(CF), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LOGGER.error("UTF-8 not supported?", e);
            }
            return targetDataSource == null || row == null || cf == null || put.has(CF, Bytes.toBytes("row_status"), Bytes.toBytes("D"))?
                    null :
                    new DataSource((String) targetDataSource,
                                   new BigtableQueryOptions(table, row, cf, targetTransformations));

        }
    }

    private static final byte[] CF                           = Bytes.toBytes("d");
    private static final byte[] TID                          = Bytes.toBytes(AugmenterModel.Configuration.UUID_FIELD_NAME);
    private static final byte[] XID                          = Bytes.toBytes(AugmenterModel.Configuration.XID_FIELD_NAME);

    private final Map<String, Object> configuration;

    // Constructor
    public HBaseApplierMutationGenerator(Map<String, Object> configuration, Metrics<?> metrics) {
        this.configuration = configuration;
        this.metrics = metrics;
        setShardName(this.configuration);
        initValidationTransformations();
    }

    private void initValidationTransformations() {
        sourceTransformations = new HashMap<>();
        sourceTransformations.put(TransformationTypes.MAP_NULL_COLUMNS.getValue(), "NULL");
        sourceTransformations.put(TransformationTypes.TIMESTAMPS_TO_EPOCHS.getValue(), true);

        List<String> ignoreColumns = new ArrayList<>();
        ignoreColumns.add("_replicator_uuid");
        ignoreColumns.add("_replicator_xid");
        ignoreColumns.add("_transaction_uuid");
        ignoreColumns.add("_transaction_xid");
        targetTransformations = new HashMap<>();
        targetTransformations.put(TransformationTypes.IGNORE_COLUMNS.getValue(), ignoreColumns);
    }

    private void setShardName(Map<String, Object> configuration) {
        this.shardName = (String) configuration.getOrDefault(ValidationService.Configuration.VALIDATION_SOURCE_DATA_SOURCE, "");
    }

    public String getShardName() {
        return this.shardName;
    }

    /**
     * Transforms a list of {@link AugmentedRow} to a {@link PutMutation}
     * @param augmentedRow
     * @return PutMutation
     */
    public PutMutation getPutForMirroredTable(AugmentedRow augmentedRow) {

        // Base RowID
        String hbaseRowID = HBaseRowKeyMapper.getSaltedHBaseRowKey(augmentedRow);

        // Base Table Name
        String namespace = (String) configuration.get(HBaseApplier.Configuration.TARGET_NAMESPACE);
        String prefix = "";
        if (!namespace.isEmpty()) {
            prefix = namespace + ":";
        }
        String hbaseTableName = prefix.toLowerCase() + augmentedRow.getTableName().toLowerCase();

        // Context Payload Table
        String payloadTableName =  (String) configuration.get(HBaseApplier.Configuration.PAYLOAD_TABLE_NAME);
        if (payloadTableName != null && payloadTableName.equals(augmentedRow.getTableName())) {
            hbaseRowID = HBaseRowKeyMapper.getPayloadTableHBaseRowKey(augmentedRow);
        }

        // Mutation
        Put put = new Put(Bytes.toBytes(hbaseRowID));
        String uuid = augmentedRow.getTransactionUUID();
        Long xid = augmentedRow.getTransactionXid();

        Long microsecondsTimestamp = augmentedRow.getRowMicrosecondTimestamp();

        switch (augmentedRow.getEventType()) {

            case DELETE: {

                // No need to process columns on DELETE. Only write delete marker.
                String columnName = "row_status";
                String columnValue = "D";
                put.addColumn(
                        CF,
                        Bytes.toBytes(columnName),
                        microsecondsTimestamp,
                        Bytes.toBytes(columnValue)
                );

                this.metrics.getRegistry()
                        .counter("applier.hbase.columns.mutations.count").inc(1L);
                this.metrics.getRegistry()
                        .counter("applier.hbase.columns.mutations.delete.count").inc(1L);

                if (uuid != null) {
                    put.addColumn(
                            CF,
                            TID,
                            microsecondsTimestamp,
                            Bytes.toBytes(uuid)
                    );

                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.count").inc(1L);
                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.delete.count").inc(1L);
                }
                if (xid != null) {
                    put.addColumn(
                            CF,
                            XID,
                            microsecondsTimestamp,
                            Bytes.toBytes(xid.toString())
                    );

                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.count").inc(1L);
                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.delete.count").inc(1L);
                }
                break;
            }
            case UPDATE: {

                // Only write values that have changed
                String columnValue;

                for (String columnName : augmentedRow.getValues().keySet()) {

                    String valueBefore = augmentedRow.getValueAsString(columnName, EventDeserializer.Constants.VALUE_BEFORE);
                    String valueAfter  = augmentedRow.getValueAsString(columnName, EventDeserializer.Constants.VALUE_AFTER);

                    if ((valueAfter == null) && (valueBefore == null)) {
                        // no change, skip;
                    } else if (
                            ((valueBefore == null) && (valueAfter != null))
                                    ||
                                    ((valueBefore != null) && (valueAfter == null))
                                    ||
                                    (!valueAfter.equals(valueBefore))) {

                        columnValue = valueAfter;
                        put.addColumn(
                                CF,
                                Bytes.toBytes(columnName),
                                microsecondsTimestamp,
                                Bytes.toBytes(columnValue)
                        );

                        this.metrics.getRegistry()
                                .counter("applier.hbase.columns.mutations.count").inc(1L);
                        this.metrics.getRegistry()
                                .counter("applier.hbase.columns.mutations.update.count").inc(1L);

                    } else {
                        // no change, skip
                    }
                }

                put.addColumn(
                        CF,
                        Bytes.toBytes("row_status"),
                        microsecondsTimestamp,
                        Bytes.toBytes("U")
                );
                this.metrics.getRegistry()
                        .counter("applier.hbase.columns.mutations.count").inc(1L);
                this.metrics.getRegistry()
                        .counter("applier.hbase.columns.mutations.update.count").inc(1L);

                if (uuid != null) {
                    put.addColumn(
                            CF,
                            TID,
                            microsecondsTimestamp,
                            Bytes.toBytes(uuid)
                    );
                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.count").inc(1L);
                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.update.count").inc(1L);
                }

                if (xid != null) {
                    put.addColumn(
                            CF,
                            XID,
                            microsecondsTimestamp,
                            Bytes.toBytes(xid.toString())
                    );
                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.count").inc(1L);
                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.update.count").inc(1L);
                }
                break;
            }
            case INSERT: {

                String columnValue;

                for (String columnName : augmentedRow.getValues().keySet()) {
                    columnValue = augmentedRow.getValueAsString(columnName);
                    if (columnValue == null) {
                        columnValue = "NULL";
                    }

                    put.addColumn(
                            CF,
                            Bytes.toBytes(columnName),
                            microsecondsTimestamp,
                            Bytes.toBytes(columnValue)
                    );

                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.count").inc(1L);
                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.insert.count").inc(1L);
                }

                put.addColumn(
                        CF,
                        Bytes.toBytes("row_status"),
                        microsecondsTimestamp,
                        Bytes.toBytes("I")
                );

                this.metrics.getRegistry()
                        .counter("applier.hbase.columns.mutations.count").inc(1L);
                this.metrics.getRegistry()
                        .counter("applier.hbase.columns.mutations.insert.count").inc(1L);

                if (uuid != null) {
                    put.addColumn(
                            CF,
                            TID,
                            microsecondsTimestamp,
                            Bytes.toBytes(uuid)
                    );
                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.count").inc(1L);
                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.insert.count").inc(1L);
                }

                if (xid != null) {
                    put.addColumn(
                            CF,
                            XID,
                            microsecondsTimestamp,
                            Bytes.toBytes(xid.toString())
                    );
                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.count").inc(1L);
                    this.metrics.getRegistry()
                            .counter("applier.hbase.columns.mutations.insert.count").inc(1L);
                }
                break;
            }
            default:
                LOGGER.error("Wrong event type " + augmentedRow.getEventType() + ". Expected INSERT/UPDATE/DELETE.");
        }
        return new PutMutation(
                put,
                hbaseTableName,
                getRowUri(augmentedRow),
                getSourceDataSource(augmentedRow),
                augmentedRow.getTransactionUUID()
        );
    }

    private String getRowUri(AugmentedRow row) {

        String sourceDomain = row.getTableSchema().toString().toLowerCase();
        String configShardName = this.getShardName();
        if (configShardName != null && !configShardName.isEmpty()) {
            sourceDomain = configShardName;
        }
        AugmentedEventType eventType = row.getEventType();

        String table = row.getTableName();
        String originalTableName = row.getOriginalTableName();
        if (originalTableName != null && !originalTableName.isEmpty()) {
            table = originalTableName;
        }

        String keys  = row.getPrimaryKeyColumns().stream()
                .map( column -> {
                    try {
                        String value = row.getValueAsString(column, AugmentedEventType.UPDATE == eventType ? EventDeserializer.Constants.VALUE_AFTER : null);
                        return URLEncoder.encode(column,"UTF-8") + "=" + URLEncoder.encode(value,"UTF-8");
                    } catch (UnsupportedEncodingException e) {

                        LOGGER.error("Unexpected encoding exception", e);

                        return null;

                    }
                } )
                .collect(Collectors.joining("&"));

        return String.format("mysql://%s/%s?%s", sourceDomain, table, keys  );
    }

    private DataSource getSourceDataSource(AugmentedRow row) {
        AugmentedEventType eventType = row.getEventType();
        String table = row.getTableName();
        String originalTableName = row.getOriginalTableName();
        if (originalTableName != null && !originalTableName.isEmpty()) {
            table = originalTableName;
        }
        HashMap<String, Object> primaryKeys = new HashMap<>();
        row.getPrimaryKeyColumns().forEach(column->{
            String value = row.getValueAsString(column, AugmentedEventType.UPDATE == eventType ? EventDeserializer.Constants.VALUE_AFTER : null);
            try {
                primaryKeys.put(URLEncoder.encode(column,"UTF-8"), URLEncoder.encode(value,"UTF-8"));
            } catch (UnsupportedEncodingException e) {
                LOGGER.error("Unexpected encoding exception", e);
            }
        });
        String dataSourceName = (String) configuration.getOrDefault(ValidationService.Configuration.VALIDATION_SOURCE_DATA_SOURCE, null);
        return dataSourceName == null ? null : new DataSource(dataSourceName,
                                                              new MysqQuerylOptions(Types.MYSQL.getValue(),
                                                                                    table,
                                                                                    primaryKeys,
                                                                      sourceTransformations));
    }


}
