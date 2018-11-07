
# txm-kafka-connect

Library of custom features that can be used with Kafka Connect

### Classes

*FirstMatchTimestampExtractor*

Used in conjunction with a TimeBasedPartitioner where the partition id is derived from a time value in the event.
The first field found from a pipe delimited string of field names is used as the time value.

In the Connector config
```
{
...
    "partitioner.class": "io.confluent.connect.storage.partitioner.TimeBasedPartitioner",
    "timestamp.extractor": "uk.gov.hmrc.txm.kafka.connect.storage.partitioner.FirstMatchTimestampExtractor",
    "timestamp.field": "response.generatedAt|generatedAt",
...

}
```

## Local Development Setup
A Docker based local development environment is described [here](dev-setup/README.md)
### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
