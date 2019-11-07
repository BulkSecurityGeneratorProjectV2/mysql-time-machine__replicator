package com.booking.replication.augmenter;

import com.booking.replication.augmenter.model.schema.ColumnSchema;
import com.booking.replication.augmenter.model.schema.DataType;
import com.booking.replication.augmenter.model.schema.FullTableName;
import com.booking.replication.augmenter.model.schema.TableSchema;

import com.booking.replication.supplier.model.TableMapRawEventData;
import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.TableMapEventMetadata;
import com.github.shyiko.mysql.binlog.event.deserialization.ColumnType;
import org.apache.commons.dbcp2.BasicDataSource;

import java.security.acl.LastOwnerException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

public class SchemaHelpers {

    private static final int VARCHAR_MAXIMUM_LENGTH = 65535;

    public static TableSchema computeTableSchemaFromBinlogMetadata(
            String schema,
            String tableName,
            TableMapRawEventData tableMapEventData) {

        TableMapEventMetadata tableMapEventMetadata = tableMapEventData.getEventMetadata();

        List<ColumnSchema> columnSchemaList = new ArrayList<>();

        List<String> columnNameList = tableMapEventMetadata.getColumnNames();

        // TODO: we assume column index is the same for all these lists/sets/arrays - verify
        int columnIndex = 0;

        int charsetIdIndex = 0;
        int enumAndSetCharsetIdIndex = 0;

        for (String columnName : columnNameList) {

            boolean isNullable = tableMapEventData.getColumnNullability().get(columnIndex);

            // Note:
            //       - PRIMARY_KEY_WITH_PREFIX is not supported
            //       - Only SIMPLE_PRIMARY_KEY is supported
            //
            //  Ref Read:
            //        - https://github.com/shyiko/mysql-binlog-connector-java/blob/682a17af38d0382902d5f18040182d2d793cc055/src/main/java/com/github/shyiko/mysql/binlog/event/deserialization/TableMapEventMetadataDeserializer.java
            //        - https://dev.mysql.com/doc/dev/mysql-server/latest/classbinary__log_1_1Table__map__event.html#a1b84e5b226c76eaf9c0df8ed03ba1393aed5533f760899bd3476ea3d14df8d35c
            //        - https://dev.mysql.com/doc/dev/mysql-server/latest/classbinary__log_1_1Table__map__event.html#a1b84e5b226c76eaf9c0df8ed03ba1393a7779ea099ef4de159d1e0211a1d7c427
            //        - https://dev.mysql.com/doc/dev/mysql-server/latest/namespacebinary__log.html#a10ab62a4112af1703ce26b7009aa2865

            // A sequence of column indexes that make up primary key
            List<Integer> pkColumnIndexes = tableMapEventMetadata.getSimplePrimaryKeys();

            DataType dataType = getColumnTypeCode(tableMapEventData, columnIndex);

            boolean isPrimary = pkColumnIndexes.contains(columnIndex) ? true : false;

            ColumnSchema columnSchema = new ColumnSchema(

                    tableMapEventMetadata.getColumnNames().get(columnIndex), // Column Name

                    // TODO: include Signedness
                    dataType,

                    dataType.getCode(),

                    isNullable,

                    isPrimary                                                // COLUMN_KEY->PRI; other values (UNI/MUL)
                                                                             // seem to not be supported in binlog
                                                                             // additional metadata
            );

            // MySQL Charsets & Collations https://dev.mysql.com/doc/internals/en/character-set.html
            // Common ones:
            //      33 - utf8_general_ci
            //      63 - binary
            // NOTE: in the binlog metadata the charset ids are listed in the order of columns that have them, but
            //       in case there are non-char/non-text columns in the table, the indexes will be different than
            //       column indexes, so we need to maintain a separate charsetIdIndex
            Integer columnCollationId = null;
            switch (dataType) {

                case VARCHAR:
                case CHAR:
                case TINYTEXT:
                case TEXT:
                case MEDIUMTEXT:
                case LONGTEXT:
                case BINARY:
                case VARBINARY:
                case BLOB:
                case TINYBLOB:
                case MEDIUMBLOB:
                case LONGBLOB:
                    List<Integer> charsets = tableMapEventMetadata.getColumnCharsets();
                    if (charsets != null) {
                        if (charsets.size() <= charsetIdIndex) {
                            // column specific collations depleted, use default charset/collation
                            columnCollationId = tableMapEventMetadata.getDefaultCharset().getDefaultCharsetCollation();
                            if (columnCollationId == null) {
                                System.out.println("Cannot determine default charset, defaulting to binary for columnIndex #" + columnIndex);
                                columnCollationId = 63; // binary
                            }
                        } else {
                            columnCollationId = charsets.get(charsetIdIndex);
                            if (columnCollationId == null) {
                                // this should never happen
                                throw new RuntimeException(
                                        "columnCollationId is null for { charsetIdIndex: " +
                                                charsetIdIndex +
                                                " , charsetList:  " +
                                                charsets.toString() +
                                                " }"
                                );
                            }
                        }
                    }

                    charsetIdIndex++;

                    columnSchema
                            .setCollation(String.valueOf(columnCollationId)) // TODO: lookup table for collation name
                            .setCharMaxLength(VARCHAR_MAXIMUM_LENGTH);       // TODO: remove this field in future versions
                                                                             // In extra metadata there is no max char length,
                                                                             // but for the time being keeping it for compatibility
                                                                             // with active schema implementation
                    break;

                case ENUM:
                case SET:
                    List<Integer> enumAndSetCharsets = tableMapEventMetadata.getEnumAndSetColumnCharsets();
                    if (enumAndSetCharsets != null) {
                        if (enumAndSetCharsets.size() <= enumAndSetCharsetIdIndex) {
                            // column specific collations depleted, use default charset/collation
                            columnCollationId = tableMapEventMetadata.getEnumAndSetDefaultCharset().getDefaultCharsetCollation();
                            if (columnCollationId == null) {
                                System.out.println("Cannot determine default charset, defaulting to binary for columnIndex #" + columnIndex);
                                columnCollationId = 63; // binary
                            }
                        } else {
                            columnCollationId = enumAndSetCharsets.get(enumAndSetCharsetIdIndex);
                            if (columnCollationId == null) {
                                // this should never happen
                                throw new RuntimeException(
                                        "columnCollationId is null for { enumAndSetCharsetIdIndex: " +
                                                enumAndSetCharsetIdIndex +
                                                " , charsetList:  " +
                                                enumAndSetCharsets.toString() +
                                                " }"
                                );
                            }
                        }
                    }
                    columnSchema
                            .setCollation(String.valueOf(columnCollationId)); // TODO: lookup table for collation name

                    enumAndSetCharsetIdIndex++;

                    break;

                default:
                    break;

            }

            // TODO: remove this field in future versions
            // In extra metadata there is no default value
            // but keeping here for compatibility with active schema implementation
            columnSchema.setDefaultValue("NA");

            columnSchemaList.add(columnSchema);

            columnIndex++;
        }

        return
                new TableSchema(
                        new FullTableName(schema, tableName),
                        columnSchemaList,
                        "NA"
                );
    }

    private static DataType getColumnTypeCode(TableMapRawEventData tableMapEventData, int columnIndex) {

        byte[] columnTypes = tableMapEventData.getColumnTypes();

        Integer columnTypeCode = Byte.toUnsignedInt(columnTypes[columnIndex]);
        ColumnType columnType = ColumnType.byCode(columnTypeCode);

        switch (columnType) {

            case DECIMAL:
                return DataType.byCode("DECIMAL");
            case NEWDECIMAL:
                return DataType.byCode("NEWDECIMAL");

            case TINY:
                return DataType.byCode("TINYINT");
            case SHORT:
                return DataType.byCode("SMALLINT");
            case INT24:
                return DataType.byCode("MEDIUMINT");
            case LONG:
                return DataType.byCode("INT");
            case LONGLONG:
                return DataType.byCode("BIGINT");

            case FLOAT:
                return DataType.byCode("FLOAT");
            case DOUBLE:
                return DataType.byCode("DOUBLE");

            case NULL:
                return DataType.byCode("UNKNOWN");

            case TIMESTAMP:
                return DataType.byCode("TIMESTAMP");
            case DATE:
                return DataType.byCode("DATE");
            case TIME:
                return DataType.byCode("TIME");
            case DATETIME:
                return DataType.byCode("DATETIME");
            case YEAR:
                return DataType.byCode("YEAR");

            case NEWDATE:
                return DataType.byCode("NEWDATE");
            case TIMESTAMP_V2:
                return DataType.byCode("TIMESTAMP_V2");
            case DATETIME_V2:
                return DataType.byCode("DATETIME_V2");
            case TIME_V2:
                return DataType.byCode("TIME_V2");

            case BIT:
                return DataType.byCode("BIT");
            case JSON:
                return DataType.byCode("JSON");

            case ENUM:
                return DataType.byCode("ENUM");
            case SET:
                return DataType.byCode("SET");

            case TINY_BLOB:
                return DataType.byCode("TINYBLOB");
            case MEDIUM_BLOB:
                return DataType.byCode("MEDIUMBLOB");
            case BLOB:
                return DataType.byCode("BLOB");
            case LONG_BLOB:
                return DataType.byCode("LONGBLOB");

            case VARCHAR:
                return DataType.byCode("VARCHAR");
            case VAR_STRING:
                return DataType.byCode("VARCHAR");
            case STRING:
                return DataType.byCode("VARCHAR");

            case GEOMETRY:
                return DataType.byCode("GEOMETRY");

            default:
                return DataType.byCode("UNKNOWN");
        }
    }

    public static TableSchema computeTableSchemaFromActiveSchemaInstance(String schema, String tableName, BasicDataSource dataSource, DataSource binlogDataSource) {

        try (Connection connection = dataSource.getConnection()) {
            Statement statementListColumns      = connection.createStatement();
            Statement statementShowCreateTable  = connection.createStatement();

            //  connection.getSchema() returns null for MySQL, so we do this ugly hack
            // TODO: find nicer way
            String[] terms = connection.getMetaData().getURL().split("/");
            String schemaName = terms[terms.length - 1];

            List<ColumnSchema> columnList = new ArrayList<>();

            ResultSet resultSet;
            SchemaHelpers.createTableIfNotExists(tableName, connection, binlogDataSource);

            resultSet = statementListColumns.executeQuery(
                    String.format(ActiveSchemaManager.LIST_COLUMNS_SQL, schema, tableName)
            );

            while (resultSet.next()) {

                boolean isNullable = (resultSet.getString("IS_NULLABLE").equals("NO") ? false : true);

                DataType dataType = DataType.byCode(resultSet.getString("DATA_TYPE"));

                boolean isPrimary = (resultSet.getString("COLUMN_KEY").toLowerCase().contains("pri")) ? true : false;

                ColumnSchema columnSchema = new ColumnSchema(
                        resultSet.getString("COLUMN_NAME"),
                        dataType,
                        resultSet.getString("COLUMN_TYPE"),
                        isNullable,
                        isPrimary
                );

                columnSchema
                        .setCollation(resultSet.getString("COLLATION_NAME"))
                        .setDefaultValue(resultSet.getString("COLUMN_DEFAULT"))
                        .setCharMaxLength(resultSet.getInt("CHARACTER_MAXIMUM_LENGTH"));

                columnList.add(columnSchema);
            }

            ResultSet showCreateTableResultSet = statementShowCreateTable.executeQuery(
                    String.format(ActiveSchemaManager.SHOW_CREATE_TABLE_SQL, tableName)
            );
            ResultSetMetaData showCreateTableResultSetMetadata = showCreateTableResultSet.getMetaData();
            String tableCreateStatement = SchemaHelpers.getCreateTableStatement(tableName, showCreateTableResultSet, showCreateTableResultSetMetadata);


            return new TableSchema(new FullTableName(schemaName, tableName),
                    columnList,
                    tableCreateStatement);

        } catch (SQLException exception) {
            throw new IllegalStateException("Could not get table schema: ", exception);
        }
    }

    private static void createTableIfNotExists(String tableName, Connection connection, DataSource binlogDataSource) throws SQLException {
        PreparedStatement stmtShowTables = connection.prepareStatement("show tables like ?");
        stmtShowTables.setString(1, tableName);
        ResultSet resultSet = stmtShowTables.executeQuery();
        if (resultSet.next()) {
            return;
        } else {
            //get from orignal table
            try (Connection binlogDbConn = binlogDataSource.getConnection()) {
                PreparedStatement preparedStatement = binlogDbConn.prepareStatement("show create table " + tableName);
                ResultSet showCreateTableResultSet = preparedStatement.executeQuery();
                ResultSetMetaData showCreateTableResultSetMetadata = showCreateTableResultSet.getMetaData();
                String createTableStatement = SchemaHelpers.getCreateTableStatement(tableName, showCreateTableResultSet, showCreateTableResultSetMetadata);
                boolean executed = connection.createStatement().execute(createTableStatement);
            }
        }
    }

    private static String getCreateTableStatement(String tableName, ResultSet showCreateTableResultSet, ResultSetMetaData showCreateTableResultSetMetadata) throws SQLException {
        String tableCreateStatement = null;
        while (showCreateTableResultSet.next()) {
            if (showCreateTableResultSetMetadata.getColumnCount() != 2) {
                throw new SQLException("SHOW CREATE TABLE should return 2 columns.");
            }
            String returnedTableName = showCreateTableResultSet.getString(1);
            if (!returnedTableName.equalsIgnoreCase(tableName)) {
                throw new SQLException("We asked for '" + tableName + "' and got '" + returnedTableName + "'");
            }
            tableCreateStatement = showCreateTableResultSet.getString(2);
        }
        return tableCreateStatement;
    }

    ;
}
