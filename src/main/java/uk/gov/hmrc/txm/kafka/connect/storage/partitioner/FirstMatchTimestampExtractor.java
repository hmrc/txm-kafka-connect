package uk.gov.hmrc.txm.kafka.connect.storage.partitioner;

import io.confluent.connect.storage.errors.PartitionException;
import io.confluent.connect.storage.partitioner.PartitionerConfig;
import io.confluent.connect.storage.partitioner.TimestampExtractor;
import io.confluent.connect.storage.util.DataUtils;
import org.apache.kafka.connect.connector.ConnectRecord;

import java.util.Arrays;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.kafka.connect.errors.DataException;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alternative implementation of TimeBasedPartitioner.RecordFieldTimestampExtractor that
 * accepts a pipe delimited sequence of fields to check for the event timestamp.
 *
 *
 * @see io.confluent.connect.storage.partitioner.TimeBasedPartitioner
 */
public class FirstMatchTimestampExtractor implements TimestampExtractor {

    private static final Logger log = LoggerFactory.getLogger(FirstMatchTimestampExtractor.class);
    private static final String CONFIG_TS_FIELDS = PartitionerConfig.TIMESTAMP_FIELD_NAME_CONFIG;

    private String[] fields;
    private DateTimeFormatter dateTime;

    public void configure(Map<String, Object> config) {
        fields = Optional.ofNullable(config.get(CONFIG_TS_FIELDS)).
                    map(o -> o.toString().split("\\|")).
                    filter(a -> a.length > 0).
                    orElseThrow(() -> new IllegalArgumentException("No values found for " + CONFIG_TS_FIELDS));
        dateTime = ISODateTimeFormat.dateTimeParser();
    }

    /**
     * Iterates through each configured timestamp field until a valid timestamp is found in the record value.
     * The record timestamp is used if no timestamp is found in the record value.
     *
     * @param record
     * @return timestamp millis for the record
     */
    public Long extract(ConnectRecord<?> record) {
        return Arrays.stream(fields).
                map(field -> maybeTimestamp(record, field)).
                filter(Optional::isPresent).
                map(Optional::get).
                findFirst().
                orElseThrow(() -> new NoSuchElementException(""));
    }

    private Optional<Long> maybeTimestamp(ConnectRecord<?> record, String fieldName) {
        Object value = record.value();
        if (value instanceof Struct) {
            Struct struct = (Struct) value;
            return timestampValue(struct, fieldName).map(ts -> {
                Schema fieldSchema = DataUtils.getNestedField(record.valueSchema(), fieldName).schema();
                if (fieldSchema.type() != Schema.Type.STRING) {
                    log.error("Unsupported type '{}' for user-defined timestamp field",
                            fieldSchema.type().getName());
                    throw new PartitionException("Error extracting timestamp from record field: " + fieldName);
                }
                return dateTime.parseMillis((String)ts);
            });
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            return timestampValue(map, fieldName).map(ts -> {
                if (!(ts instanceof String)) {
                    log.error("Unsupported type '{}' for user-defined timestamp field.", ts.getClass());
                    throw new PartitionException("Error extracting timestamp from record field: " + fieldName);
                }
                return dateTime.parseMillis((String) ts);
            });

        } else {
            log.error("Record is not of Struct or Map type");
            throw new PartitionException("Error encoding partition");
        }
    }

    private Optional<?> timestampValue(Object structOrMap, String fieldName) {
        try {
            return Optional.of(DataUtils.getNestedFieldValue(structOrMap, fieldName));
        } catch (DataException e) {
            return Optional.empty();
        }
    }
}
