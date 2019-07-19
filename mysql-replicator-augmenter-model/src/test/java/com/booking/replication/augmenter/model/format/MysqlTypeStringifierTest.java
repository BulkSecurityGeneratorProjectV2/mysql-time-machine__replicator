package com.booking.replication.augmenter.model.format;

import com.booking.replication.augmenter.model.schema.ColumnSchema;
import com.booking.replication.augmenter.model.schema.DataType;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.BitSet;

import static org.junit.Assert.assertEquals;

public class MysqlTypeStringifierTest {

    @Test
    public void testBinaryType() {
        ColumnSchema schema = new ColumnSchema("code", DataType.BINARY, "binary(10)", true, "", "");
        schema.setCharMaxLength(10);

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "6F72616E676500000000";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "4F72616E676500000000";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testVarBinaryType() {
        ColumnSchema schema = new ColumnSchema("code", DataType.VARBINARY, "binary(10)", true, "", "");
        schema.setCharMaxLength(10);

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "6F72616E676500000000";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "4F72616E676500000000";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testCharTypeLatinCharacterSet() {
        ColumnSchema schema = new ColumnSchema("name", DataType.CHAR, "char(30)", true, "", "");
        schema.setCollation("latin1_swedish_ci");

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "Orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 82, 65, 78, 71, 69};
            expected    = "ORANGE";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {66, 117, 101, 110, 111, 115, 32, 100, -19, 97, 115};
            expected    = "Buenos días";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {98, 111, 110, 110,101, 32, 106, 111, 117, 114, 110, -23, 101};
            expected    = "bonne journée";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testCharTypeUtf8CharacterSet() {
        ColumnSchema schema = new ColumnSchema("name", DataType.CHAR, "char(30)", true, "", "");
        schema.setCollation("utf8_general_ci");

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "Orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 82, 65, 78, 71, 69};
            expected    = "ORANGE";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {66, 117, 101, 110, 111, 115, 32, 100, -61, -83, 97, 115};
            expected    = "Buenos días";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {98, 111, 110, 110,101, 32, 106, 111, 117, 114, 110, -61, -87, 101};
            expected    = "bonne journée";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {-26, -105, -87, -28, -72, -118, -27, -91, -67};
            expected    = "早上好";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testVarcharTypeLatinCharacterSet() {
        ColumnSchema schema = new ColumnSchema("name", DataType.VARCHAR, "varchar(30)", true, "", "");
        schema.setCollation("latin1_swedish_ci");

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "Orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 82, 65, 78, 71, 69};
            expected    = "ORANGE";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {66, 117, 101, 110, 111, 115, 32, 100, -19, 97, 115};
            expected    = "Buenos días";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {98, 111, 110, 110,101, 32, 106, 111, 117, 114, 110, -23, 101};
            expected    = "bonne journée";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testVarcharTypeUtf8CharacterSet() {
        ColumnSchema schema = new ColumnSchema("name", DataType.VARCHAR, "varchar(30)", true, "", "");
        schema.setCollation("utf8_general_ci");

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "Orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 82, 65, 78, 71, 69};
            expected    = "ORANGE";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {66, 117, 101, 110, 111, 115, 32, 100, -61, -83, 97, 115};
            expected    = "Buenos días";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {98, 111, 110, 110,101, 32, 106, 111, 117, 114, 110, -61, -87, 101};
            expected    = "bonne journée";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {-26, -105, -87, -28, -72, -118, -27, -91, -67};
            expected    = "早上好";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testTextTypeLatinCharacterSet() {
        ColumnSchema schema = new ColumnSchema("name", DataType.TEXT, "text", true, "", "");
        schema.setCollation("latin1_swedish_ci");

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "Orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema , null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 82, 65, 78, 71, 69};
            expected    = "ORANGE";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {66, 117, 101, 110, 111, 115, 32, 100, -19, 97, 115};
            expected    = "Buenos días";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {98, 111, 110, 110,101, 32, 106, 111, 117, 114, 110, -23, 101};
            expected    = "bonne journée";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testTextTypeUtf8CharacterSet() {
        ColumnSchema schema = new ColumnSchema("name", DataType.TEXT, "text", true, "", "");
        schema.setCollation("utf8_general_ci");

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "Orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 82, 65, 78, 71, 69};
            expected    = "ORANGE";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {66, 117, 101, 110, 111, 115, 32, 100, -61, -83, 97, 115};
            expected    = "Buenos días";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {98, 111, 110, 110,101, 32, 106, 111, 117, 114, 110, -61, -87, 101};
            expected    = "bonne journée";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {-26, -105, -87, -28, -72, -118, -27, -91, -67};
            expected    = "早上好";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testMediumTextTypeLatinCharacterSet() {
        ColumnSchema schema = new ColumnSchema("name", DataType.MEDIUMTEXT, "mediumtext", true, "", "");
        schema.setCollation("latin1_swedish_ci");

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "Orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 82, 65, 78, 71, 69};
            expected    = "ORANGE";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {66, 117, 101, 110, 111, 115, 32, 100, -19, 97, 115};
            expected    = "Buenos días";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {98, 111, 110, 110,101, 32, 106, 111, 117, 114, 110, -23, 101};
            expected    = "bonne journée";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testMediumTextTypeUtf8CharacterSet() {
        ColumnSchema schema = new ColumnSchema("name", DataType.MEDIUMTEXT, "mediumtext", true, "", "");
        schema.setCollation("utf8_general_ci");

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "Orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 82, 65, 78, 71, 69};
            expected    = "ORANGE";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {66, 117, 101, 110, 111, 115, 32, 100, -61, -83, 97, 115};
            expected    = "Buenos días";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {98, 111, 110, 110,101, 32, 106, 111, 117, 114, 110, -61, -87, 101};
            expected    = "bonne journée";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {-26, -105, -87, -28, -72, -118, -27, -91, -67};
            expected    = "早上好";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testTinyTextTypeLatinCharacterSet() {
        ColumnSchema schema = new ColumnSchema("name", DataType.TINYTEXT, "tinytext", true, "", "");
        schema.setCollation("latin1_swedish_ci");

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "Orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 82, 65, 78, 71, 69};
            expected    = "ORANGE";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {66, 117, 101, 110, 111, 115, 32, 100, -19, 97, 115};
            expected    = "Buenos días";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {98, 111, 110, 110,101, 32, 106, 111, 117, 114, 110, -23, 101};
            expected    = "bonne journée";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testTinyTextTypeUtf8CharacterSet() {
        ColumnSchema schema = new ColumnSchema("name", DataType.TINYTEXT, "tinytext", true, "", "");
        schema.setCollation("utf8_general_ci");

        byte[] testByteArr;
        String expected, actual;

        {
            testByteArr = new byte[] {111, 114, 97, 110, 103, 101};
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 114, 97, 110, 103, 101};
            expected    = "Orange";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {79, 82, 65, 78, 71, 69};
            expected    = "ORANGE";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {66, 117, 101, 110, 111, 115, 32, 100, -61, -83, 97, 115};
            expected    = "Buenos días";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {98, 111, 110, 110,101, 32, 106, 111, 117, 114, 110, -61, -87, 101};
            expected    = "bonne journée";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }

        {
            testByteArr = new byte[] {-26, -105, -87, -28, -72, -118, -27, -91, -67};
            expected    = "早上好";

            actual = MysqlTypeStringifier.convertToString(testByteArr, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testBitType() {
        ColumnSchema schema = new ColumnSchema("id", DataType.BIT, "bit(5)", true, "", "");

        BitSet testBit;
        String expected, actual;

        {
            testBit     = new BitSet();
            testBit.set(0);
            testBit.set(3);
            testBit.set(4);

            expected    = "11001";

            actual = MysqlTypeStringifier.convertToString(testBit, schema, null);
            assertEquals(expected, actual);
        }

        {
            testBit     = new BitSet();
            expected    = "0";

            actual = MysqlTypeStringifier.convertToString(testBit, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testDateType() {
        ColumnSchema schema = new ColumnSchema("dt", DataType.DATE, "date", true, "", "");

        Long testDate;
        String expected, actual;

        {
            testDate   = 1548979200000L;
            expected    = "2019-02-01";

            actual = MysqlTypeStringifier.convertToString(testDate, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testDateTimeType() {
        ColumnSchema schema = new ColumnSchema("ts", DataType.DATETIME, "datetime", true, "", "");

        Long testDateTime;
        String expected, actual;

        {
            testDateTime    = 1548982800123L;
            expected        = "2019-02-01 01:00:00.123";

            actual = MysqlTypeStringifier.convertToString(testDateTime, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testTimestampType() {
        ColumnSchema schema = new ColumnSchema("ts", DataType.TIMESTAMP, "timestamp", true, "", "");

        Long testTimestamp;
        String expected, actual;

        {
            testTimestamp   = 1548982800123L;
            expected        = "2019-02-01 01:00:00.123";

            actual = MysqlTypeStringifier.convertToString(testTimestamp, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testEnumType() {
        ColumnSchema schema = new ColumnSchema("fruit", DataType.ENUM, "enum('apple','banana','orange')", true, "", "");

        String[] groupValues = new String[] {"apple", "banana", "orange"};

        Integer testValue;
        String expected, actual;

        {
            testValue   = 1;
            expected    = "apple";

            actual = MysqlTypeStringifier.convertToString(testValue, schema, groupValues);
            assertEquals(expected, actual);
        }

        {
            testValue   = 2;
            expected    = "banana";

            actual = MysqlTypeStringifier.convertToString(testValue, schema, groupValues);
            assertEquals(expected, actual);
        }

        {
            testValue   = 3;
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testValue, schema, groupValues);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testSetType() {
        ColumnSchema schema = new ColumnSchema("fruit", DataType.SET, "set('apple','banana','orange')", true, "", "");

        String[] groupValues = new String[] {"apple", "banana", "orange"};

        Long testValue;
        String expected, actual;

        {
            testValue   = 1L;
            expected    = "apple";

            actual = MysqlTypeStringifier.convertToString(testValue, schema, groupValues);
            assertEquals(expected, actual);
        }

        {
            testValue   = 2L;
            expected    = "banana";

            actual = MysqlTypeStringifier.convertToString(testValue, schema, groupValues);
            assertEquals(expected, actual);
        }

        {
            testValue   = 3L;
            expected    = "apple,banana";

            actual = MysqlTypeStringifier.convertToString(testValue, schema, groupValues);
            assertEquals(expected, actual);
        }

        {
            testValue   = 4L;
            expected    = "orange";

            actual = MysqlTypeStringifier.convertToString(testValue, schema, groupValues);
            assertEquals(expected, actual);
        }

        {
            testValue   = 5L;
            expected    = "apple,orange";

            actual = MysqlTypeStringifier.convertToString(testValue, schema, groupValues);
            assertEquals(expected, actual);
        }

        {
            testValue   = 6L;
            expected    = "banana,orange";

            actual = MysqlTypeStringifier.convertToString(testValue, schema, groupValues);
            assertEquals(expected, actual);
        }

        {
            testValue   = 7L;
            expected    = "apple,banana,orange";

            actual = MysqlTypeStringifier.convertToString(testValue, schema, groupValues);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testSignedTinyInt() {
        ColumnSchema schema = new ColumnSchema("id", DataType.TINYINT, "tinyint(4)", true, "", "");

        Integer testTinyInteger;
        String expected, actual;

        {
            testTinyInteger = 0;
            expected        = "0";

            actual = MysqlTypeStringifier.convertToString(testTinyInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testTinyInteger = 127;
            expected        = "127";

            actual = MysqlTypeStringifier.convertToString(testTinyInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testTinyInteger = -128;
            expected        = "-128";

            actual = MysqlTypeStringifier.convertToString(testTinyInteger, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testUnsignedTinyInt() {
        ColumnSchema schema = new ColumnSchema("id", DataType.TINYINT, "tinyint(4) unsigned", true, "", "");

        Integer testTinyInteger;
        String expected, actual;

        {
            testTinyInteger = 0;
            expected        = "0";

            actual = MysqlTypeStringifier.convertToString(testTinyInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testTinyInteger = 127;
            expected        = "127";

            actual = MysqlTypeStringifier.convertToString(testTinyInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testTinyInteger = -128;
            expected        = "128";

            actual = MysqlTypeStringifier.convertToString(testTinyInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testTinyInteger = -1;
            expected        = "255";

            actual = MysqlTypeStringifier.convertToString(testTinyInteger, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testSignedSmallInt() {
        ColumnSchema schema = new ColumnSchema("id", DataType.SMALLINT, "smallint(6)", true, "", "");

        Integer testSmallInteger;
        String expected, actual;

        {
            testSmallInteger    = 0;
            expected            = "0";

            actual = MysqlTypeStringifier.convertToString(testSmallInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testSmallInteger    = 32767;
            expected            = "32767";

            actual = MysqlTypeStringifier.convertToString(testSmallInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testSmallInteger    = -32768;
            expected            = "-32768";

            actual = MysqlTypeStringifier.convertToString(testSmallInteger, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testUnsignedSmallInt() {
        ColumnSchema schema = new ColumnSchema("id", DataType.SMALLINT, "smallint(6) unsigned", true, "", "");

        Integer testSmallInteger;
        String expected, actual;

        {
            testSmallInteger    = 0;
            expected            = "0";

            actual = MysqlTypeStringifier.convertToString(testSmallInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testSmallInteger    = 32767;
            expected            = "32767";

            actual = MysqlTypeStringifier.convertToString(testSmallInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testSmallInteger    = -32768;
            expected            = "32768";

            actual = MysqlTypeStringifier.convertToString(testSmallInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testSmallInteger    = -1;
            expected            = "65535";

            actual = MysqlTypeStringifier.convertToString(testSmallInteger, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testSignedMediumInt() {
        ColumnSchema schema = new ColumnSchema("id", DataType.MEDIUMINT, "mediumint(9)", true, "", "");

        Integer testMediumInteger;
        String expected, actual;

        {
            testMediumInteger   = 0;
            expected            = "0";

            actual = MysqlTypeStringifier.convertToString(testMediumInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testMediumInteger   = 8388607;
            expected            = "8388607";

            actual = MysqlTypeStringifier.convertToString(testMediumInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testMediumInteger   = -8388608;
            expected            = "-8388608";

            actual = MysqlTypeStringifier.convertToString(testMediumInteger, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testUnsignedMediumInt() {
        ColumnSchema schema = new ColumnSchema("id", DataType.MEDIUMINT, "mediumint(9) unsigned", true, "", "");

        Integer testMediumInteger;
        String expected, actual;

        {
            testMediumInteger   = 0;
            expected            = "0";

            actual = MysqlTypeStringifier.convertToString(testMediumInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testMediumInteger   = 8388607;
            expected            = "8388607";

            actual = MysqlTypeStringifier.convertToString(testMediumInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testMediumInteger   = -8388608;
            expected            = "8388608";

            actual = MysqlTypeStringifier.convertToString(testMediumInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testMediumInteger   = -1;
            expected            = "16777215";

            actual = MysqlTypeStringifier.convertToString(testMediumInteger, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testSignedInt() {
        ColumnSchema schema = new ColumnSchema("id", DataType.INT, "int(11)", true, "", "");

        Integer testInteger;
        String expected, actual;

        {
            testInteger = 0;
            expected    = "0";

            actual = MysqlTypeStringifier.convertToString(testInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testInteger = 2147483647;
            expected    = "2147483647";

            actual = MysqlTypeStringifier.convertToString(testInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testInteger = -2147483648;
            expected    = "-2147483648";

            actual = MysqlTypeStringifier.convertToString(testInteger, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testUnsignedInt() {
        ColumnSchema schema = new ColumnSchema("id", DataType.INT, "int(10) unsigned", true, "", "");

        Integer testInteger;
        String expected, actual;

        {
            testInteger = 0;
            expected    = "0";

            actual = MysqlTypeStringifier.convertToString(testInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testInteger = 2147483647;
            expected    = "2147483647";

            actual = MysqlTypeStringifier.convertToString(testInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testInteger = -2147483648;
            expected    = "2147483648";

            actual = MysqlTypeStringifier.convertToString(testInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testInteger = -1;
            expected    = "4294967295";

            actual = MysqlTypeStringifier.convertToString(testInteger, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testSignedBigInt() {
        ColumnSchema schema = new ColumnSchema("id", DataType.BIGINT, "bigint(20)", true, "", "");

        Long testBigInteger;
        String expected, actual;

        {
            testBigInteger  = 0L;
            expected        = "0";

            actual = MysqlTypeStringifier.convertToString(testBigInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testBigInteger  = 9223372036854775807L;
            expected        = "9223372036854775807";

            actual = MysqlTypeStringifier.convertToString(testBigInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testBigInteger  = -9223372036854775808L;
            expected        = "-9223372036854775808";

            actual = MysqlTypeStringifier.convertToString(testBigInteger, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testUnsignedBigInt() {
        ColumnSchema schema = new ColumnSchema("id", DataType.BIGINT, "bigint(20) unsigned", true, "", "");

        Long testBigInteger;
        String expected, actual;

        {
            testBigInteger  = 0L;
            expected        = "0";

            actual = MysqlTypeStringifier.convertToString(testBigInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testBigInteger  = 9223372036854775807L;
            expected        = "9223372036854775807";

            actual = MysqlTypeStringifier.convertToString(testBigInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testBigInteger  = -9223372036854775808L;
            expected        = "9223372036854775808";

            actual = MysqlTypeStringifier.convertToString(testBigInteger, schema, null);
            assertEquals(expected, actual);
        }

        {
            testBigInteger  = -1L;
            expected        = "18446744073709551615";

            actual = MysqlTypeStringifier.convertToString(testBigInteger, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testBigDecimal() {
        ColumnSchema schema = new ColumnSchema("currency", DataType.DECIMAL, "decimal(5,3)", true, "", "");

        BigDecimal testBigDecimal;
        String expected, actual;

        {
            testBigDecimal  = new BigDecimal("99.122");
            expected        = "99.122";

            actual = MysqlTypeStringifier.convertToString(testBigDecimal, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testFloat() {
        ColumnSchema schema = new ColumnSchema("currency", DataType.FLOAT, "float(5,3)", true, "", "");

        Float testFloat;
        String expected, actual;

        {
            testFloat   = new Float("99.122");
            expected    = "99.122";

            actual = MysqlTypeStringifier.convertToString(testFloat, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testDouble() {
        ColumnSchema schema = new ColumnSchema("currency", DataType.DOUBLE, "double(5,3)", true, "", "");

        Double testDouble;
        String expected, actual;

        {
            testDouble  = new Double("99.122");
            expected    = "99.122";

            actual = MysqlTypeStringifier.convertToString(testDouble, schema, null);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testJson() {
        ColumnSchema schema = new ColumnSchema("jsn", DataType.JSON, "json", true, "", "");

        byte[] testJson;
        String expected, actual;

        {
            testJson  = new byte[]{0, 3, 0, 77, 0, 25, 0, 2, 0, 27, 0, 4, 0, 31, 0, 10, 0, 12, 41, 0, 12, 49, 0, 0, 57,
                    0, 111, 115, 110, 97, 109, 101, 114, 101, 115, 111, 108, 117, 116, 105, 111, 110, 7, 87, 105, 110,
                    100, 111, 119, 115, 7, 70, 105, 114, 101, 102, 111, 120, 2, 0, 20, 0, 18, 0, 1, 0, 19, 0, 1, 0, 5,
                    0, 10, 5, 64, 6, 120, 121};

            expected    = "{\"os\":\"Windows\",\"name\":\"Firefox\",\"resolution\":{\"x\":2560,\"y\":1600}}";

            actual = MysqlTypeStringifier.convertToString(testJson, schema, null);
            assertEquals(expected, actual);
        }


    }
}
