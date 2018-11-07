package uk.gov.hmrc.txm.kafka.connect.storage.partitioner;

import io.confluent.connect.storage.common.StorageCommonConfig;
import io.confluent.connect.storage.partitioner.PartitionerConfig;
import io.confluent.connect.storage.partitioner.TimeBasedPartitioner;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TimeBasedPartitionerTest {

    private static final String TIME_ZONE = "America/Los_Angeles";
    private static final DateTimeZone DATE_TIME_ZONE = DateTimeZone.forID(TIME_ZONE);
    private static final String PATH_FORMAT = "'year'=YYYY/'month'=M/'day'=d/'hour'=H/";

    @Test
    void usesCustomExtractor() {
        TimeBasedPartitioner<String> partitioner = new TimeBasedPartitioner<>();
        partitioner.configure(createConfig("generatedAt", "uk.gov.hmrc.txm.kafka.connect.storage.partitioner.FirstMatchTimestampExtractor"));
        assertEquals(FirstMatchTimestampExtractor.class, partitioner.getTimestampExtractor().getClass());
    }

    private Map<String, Object> createConfig(String timeFieldName, String extractorClass) {
        Map<String, Object> config = new HashMap<>();

        config.put(StorageCommonConfig.DIRECTORY_DELIM_CONFIG, StorageCommonConfig.DIRECTORY_DELIM_DEFAULT);
        config.put(PartitionerConfig.TIMESTAMP_EXTRACTOR_CLASS_CONFIG, extractorClass);
        config.put(PartitionerConfig.PARTITION_DURATION_MS_CONFIG, TimeUnit.HOURS.toMillis(1));
        config.put(PartitionerConfig.PATH_FORMAT_CONFIG, PATH_FORMAT);
        config.put(PartitionerConfig.LOCALE_CONFIG, Locale.US.toString());
        config.put(PartitionerConfig.TIMEZONE_CONFIG, DATE_TIME_ZONE.toString());
        if (timeFieldName != null) {
            config.put(PartitionerConfig.TIMESTAMP_FIELD_NAME_CONFIG, timeFieldName);
        }
        return config;
    }

}
