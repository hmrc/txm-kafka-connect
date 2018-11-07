package uk.gov.hmrc.txm.kafka.connect.storage.partitioner;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.confluent.connect.storage.errors.PartitionException;
import io.confluent.connect.storage.partitioner.PartitionerConfig;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.json.JsonConverterConfig;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.storage.StringConverter;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @see <a href="https://github.com/confluentinc/kafka-connect-storage-common/blob/dac9974152d4d54615e3c8cea38d38a57cc383a0/partitioner/src/test/java/io/confluent/connect/storage/partitioner/TimeBasedPartitionerTest.java">Kafka Connect TimeBasedPartitioner Tests</a>
 * @see <a href="https://github.com/apache/kafka/blob/trunk/connect/json/src/main/java/org/apache/kafka/connect/json/JsonConverter.java">Kafka JsonConverter</a>
 * @see <a href="https://github.com/apache/kafka/blob/trunk/connect/json/src/test/java/org/apache/kafka/connect/json/JsonConverterTest.java">Kafka JsonConverter Tests</a>
 */
class FirstMatchTimestampExtractorTest {

    private static final String TOPIC = "test-topic";
    private static final int PARTITION = 12;
    private static final String GENERATED_AT = "generatedAt";

    @Test
    void extractsTopLevelGeneratedAt() {
        ObjectNode audit = makeAudit(Optional.of("2017-07-14T03:40:00.000Z"), Optional.empty(), Optional.empty());
        SinkRecord sinkRecord = createRecord(audit.toString());
        Long ts = newExtractor(GENERATED_AT).extract(sinkRecord);
        assertEquals(1500003600000L,ts.longValue());
    }

    @Test
    void extractsResponseGeneratedAt() {
        ObjectNode audit = makeAudit(Optional.of("2018-01-01T12:00:01.000Z"), Optional.empty(), Optional.of("2017-07-14T03:40:00.000Z"));
        SinkRecord sinkRecord = createRecord(audit.toString());
        Long ts = newExtractor("response.generatedAt|generatedAt").extract(sinkRecord);
        assertEquals(1500003600000L, ts.longValue());
    }

    @Test
    void extractsFirstMatchingTimestamp() {
        ObjectNode audit = makeAudit(Optional.of("2018-01-01T12:00:01.000Z"), Optional.of("2017-07-14T03:40:00.000Z"), Optional.empty());
        SinkRecord sinkRecord = createRecord(audit.toString());
        Long ts = newExtractor("response.generatedAt|request.generatedAt|generatedAt").extract(sinkRecord);
        assertEquals(ts.longValue(), 1500003600000L);
    }

    @Test
    void throwsForInvalidTimestampFormat() {
        ObjectNode audit = makeAudit(Optional.empty(), Optional.empty(), Optional.empty());
        audit.put(GENERATED_AT, "2017/07/14T03:40:00.000Z");
        SinkRecord sinkRecord = createRecord(audit.toString());
        assertThrows(IllegalArgumentException.class, () ->  newExtractor(GENERATED_AT).extract(sinkRecord));
    }

    @Test
    void throwsForNoMatchingTimestampField() {
        ObjectNode audit = makeAudit(Optional.empty(), Optional.empty(), Optional.empty());
        audit.put("someOtherField", "2017-07-14T03:40:00.000Z");
        SinkRecord sinkRecord = createRecord(audit.toString());
        assertThrows(NoSuchElementException.class, () ->  newExtractor(GENERATED_AT).extract(sinkRecord));
    }

    @Test
    void throwsForIncorrectFieldSchema() {
        ObjectNode audit = makeAudit(Optional.empty(), Optional.empty(), Optional.empty());
        audit.put(GENERATED_AT, 1500003600000L);
        SinkRecord sinkRecord = createRecord(audit.toString());
        assertThrows(PartitionException.class, () ->  newExtractor(GENERATED_AT).extract(sinkRecord), "Error extracting timestamp from record field: " + GENERATED_AT);
    }

    @Test
    void throwsForNoTimestampFieldConfig() {
        FirstMatchTimestampExtractor extractor = new FirstMatchTimestampExtractor();
        assertThrows(IllegalArgumentException.class, () -> extractor.configure(Collections.EMPTY_MAP), "No values found for timestamp.field");
    }

    @Test
    void throwsForUnexpectedRecordType() {
        StringConverter c = new StringConverter();
        SchemaAndValue sav = c.toConnectData(TOPIC, "NotJsonData".getBytes());
        SinkRecord record = createValuedSinkRecord(sav.schema(), sav.value(), 0L);
        FirstMatchTimestampExtractor extractor = newExtractor(GENERATED_AT);
        assertThrows(PartitionException.class, () -> extractor.extract(record), "Error encoding partition");
    }

    private ObjectNode makeAudit(Optional<String> generatedAt, Optional<String> reqGeneratedAt, Optional<String> respGeneratedAt) {
        ObjectNode audit = JsonNodeFactory.instance.objectNode();
        if (generatedAt.isPresent()) {
            audit.put(GENERATED_AT, generatedAt.get());
        }
        if (reqGeneratedAt.isPresent()) {
            ObjectNode req = audit.putObject("request");
            req.put(GENERATED_AT, reqGeneratedAt.get());
        }
        if (respGeneratedAt.isPresent()) {
            ObjectNode resp = audit.putObject("response");
            resp.put(GENERATED_AT, respGeneratedAt.get());
        }
        return audit;
    }

    private FirstMatchTimestampExtractor newExtractor(String timestampFields) {

        FirstMatchTimestampExtractor extractor = new FirstMatchTimestampExtractor();

        Map<String, Object> x = Collections.unmodifiableMap(Stream.of(
                new AbstractMap.SimpleEntry<>(PartitionerConfig.TIMESTAMP_FIELD_NAME_CONFIG, timestampFields)
        ).collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue())));
        extractor.configure(x);

        return extractor;
    }

    private SinkRecord createRecord(String payload) {
        return createRecord(payload, Optional.empty());
    }

    private SinkRecord createRecord(String payload, Optional<Long> recordTimestamp) {
        JsonConverter c = new JsonConverter();
        Map<String, Object> x = Collections.unmodifiableMap(Stream.of(
                new AbstractMap.SimpleEntry<>(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, Boolean.FALSE)
        ).collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue())));
        c.configure(x, false);

        SchemaAndValue sav = c.toConnectData(TOPIC, payload.getBytes());
        return createValuedSinkRecord(sav.schema(), sav.value(), recordTimestamp.orElse(0L));
    }

    private SinkRecord createValuedSinkRecord(Schema valueSchema, Object value, Long timestamp) {
        return new SinkRecord(TOPIC, PARTITION,
                null, null, valueSchema, value,
                0, timestamp,
                timestamp == null ? TimestampType.NO_TIMESTAMP_TYPE : TimestampType.LOG_APPEND_TIME);
    }

}