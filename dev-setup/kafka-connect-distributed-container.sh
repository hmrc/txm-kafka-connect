#!/bin/bash

profile_name=
bucket_name=
build_images=

build_docker_images() {
    pushd ..
    make connect-node
    make connect-msg-producer
    popd
}

aws_credentials() {

    rm -f ~/.aws/cli/cache/docker_env

    aws-profile

    ACCOUNT=$( cat ~/.aws/credentials | grep -A 1 ${profile_name} | tail -1 | awk -F':' '{print $5}' )

    KEY_ID=$( grep -h $ACCOUNT ~/.aws/cli/cache/* | jq '.Credentials.AccessKeyId' | tr -d '"' )
    SECRET_KEY=$( grep -h $ACCOUNT ~/.aws/cli/cache/* | jq '.Credentials.SecretAccessKey' | tr -d '"' )
    SESSION_TOKEN=$( grep -h $ACCOUNT ~/.aws/cli/cache/* | jq '.Credentials.SessionToken' | tr -d '"' )

    echo "AWS_ACCESS_KEY_ID=${KEY_ID}" > ~/.aws/cli/cache/docker_env
    echo "AWS_SECRET_ACCESS_KEY=${SECRET_KEY}" >> ~/.aws/cli/cache/docker_env
    echo "AWS_SESSION_TOKEN=${SESSION_TOKEN}" >> ~/.aws/cli/cache/docker_env
}

refresh_connector_config() {
    echo "Refreshing S3 Connector config"
    curl http://localhost:28083/connectors
    curl -X DELETE http://localhost:28083/connectors/s3-sink-connector-dev
    sed "s/{{S3_BUCKET}}/${bucket_name}/g" txm-s3-connector.tpl > txm-s3-connector.config
    curl -H 'Content-Type:application/json' -d '@txm-s3-connector.config' http://localhost:28083/connectors
}

usage() {
    echo "Usage ./$0 [-bi|--build-images] (-a|--aws-profile) profile_name (-b|--s3-bucket) bucket_name"
}

while [ "$1" != "" ]; do
    case $1 in
        -bi | --build-images )  build_images=1
                                ;;
        -a | --aws-profile )    shift
                                profile_name=$1
                                ;;
        -b | --s3-bucket )      shift
                                bucket_name=$1
                                ;;
        -h | --help )           usage
                                exit
                                ;;
        * )                     usage
                                exit 1
    esac
    shift
done

if [[ -z $profile_name ]] || [[ -z $bucket_name ]]; then
    usage
    exit 1
fi

export AWS_PROFILE=${profile_name}
aws_credentials
echo "Profile used: <${profile_name}> S3 bucket <$bucket_name>"
aws s3 ls > /dev/null

if [[ $build_images -eq 1 ]]; then
    echo "Building Docker images"
    build_docker_images
fi

echo "Bringing Kafka Cluster up"
docker-compose up --scale message-producer=0 &

# Wait for Cluster to start
sleep 30

## Disabled for now. Potentiall useful if connector sink does not connect to missing topic ##
# echo "Starting message producer"
# docker-compose up message-producer &

echo "Configuring S3 Sink Connector"
refresh_connector_config



