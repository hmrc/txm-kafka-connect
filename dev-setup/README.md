# Development Environment

A local development environment based on the Confluent platform Docker images is provided

### What it includes
* Zoopkeeper node
* Kafka Broker node
* Kafka Connect node (REST API port 28083)
* Kafka Control Center node (Web UI port 29021)
* Python based message producer
* Makefile to build custom Docker images

### Pre Requisites
* An AWS user
* An existing AWS S3 Bucket
* The extensions library Jar (needs Maven to build)


### Using the Startup Script
A convenience script is provided that will
* Start the Kafka containers using docker-compose
* Post the S3 connector config to the Connect node
* (Optional) Build the Docker images for the Connect node and Message Producer containers

**Usage** _from from the dev-setup directory_
```
./kafka-connect-distributed-container.sh \
-a txm-sandbox \
-b MY_TEST_BUCKET_NAME \
-bi
```


docker-compose up --scale message-producer=0
docker-compose up message-producer

### Message Producer Utility
A harness application for sending is included as the message-producer docker container. A container is used so it can
share the network hosting the Kafka cluster containers. Running outside this network creates issues when
configuring the Broker advertised listeners.

The container runs the **message_producer.py** file which by default will generate a small set of messages
that contain a _generatedAt_ or _response.generatedAt_ field. A template file of events can also be specified.

**Usage hints**
docker-compose run message-producer ./message_producer.py --help

**Flags**
* -d The starting date for generated timestamps _(default time of 10 days ago if omitted)_
* -f Path to the template file
* -p A token value in the template to be substituted by a timestamp

**Examples** _from dev-setup folder_
```
## Generate and send sample events starting 10 days ago ##
docker-compose run message-producer ./message_producer.py

## Send events in template file my_audits ##
docker-compose run message-producer ./message_producer.py -f my_audits.json

## Send events in template, replacing tstoken with a timestamp starting from 2017/01/22 ##
docker-compose run message-producer ./message_producer.py -f example_audits_mixed.json -p tstoken -d 2017/01/22
```


## Troubleshooting
* Kafka-Connect logs _AmazonsS3Exception 400 BadRequest_, this is usually because the AWS credentials have expired. Stop the docker containers and rerun the setup script remembering (-bi) to rebuild the custom containers
* Nothing shows up in S3
..* flush.size=10 in the connector config means nothing will appear in the S3 bucket until 10 events are consumed by the connector
..* check the Kafka-Connect logs for info
..* resubmit the connector config **DELETE and then POST**  _(see refresh_connector_config in the setup script)_