#!/usr/bin/env python

from confluent_kafka import Producer
from datetime import date, datetime, timedelta
import json
from uuid import uuid1
import random
import argparse


AUDIT_SOURCES = ['source_a', 'source_b', 'source_c']
AUDIT_TYPE = ['type_a', 'type_b', 'type_c']
AUDIT_SOURCES = ['source_a']
AUDIT_TYPE = ['type_a']
TOPIC = "connect-test-audit-topic"


def audit_event(session_id, time_section):
    ev = {
        "auditSource": random.choice(AUDIT_SOURCES),
        "auditType": random.choice(AUDIT_TYPE),
        "sessionId": "{}".format(session_id),
    }
    ev.update(time_section)
    return ev


def generated_at(t):
    return {"generatedAt": event_time(t)}


def response_generated_at(t):
    return {"response": {"generatedAt": event_time(t)}}


def event_time(t):
    return "{}".format(t).replace(" ", "T")[0:-3] + "Z"


def generate_audits(run_id, start_time, min_messages=100, max_messages=100):
    fn_times = [generated_at, response_generated_at]
    number_of_messages = random.randint(min_messages, max_messages)
    print("Generating {} messages to {}".format(number_of_messages, run_id))
    for i in range(0, number_of_messages):
        event_id = "{}::{}".format(run_id, i)
        fn_time = random.choice(fn_times)
        event_time = fn_time(start_time + timedelta(seconds=20 * i))
        yield audit_event(event_id, event_time)


def produce_message(producer, topic, msg):
    producer.poll(0)
    producer.produce(topic, msg)
    producer.flush()


def read_audits(path, start_time, time_placeholder):
    with open(path) as file:
        s = file.read()
        if time_placeholder:
            t = start_time
            t_idx = 0
            while t_idx != -1:
                t = t + timedelta(seconds=20)
                s = s.replace(time_placeholder, event_time(t), 1)
                t_idx = s.find(time_placeholder, t_idx)
        j = json.loads(s)
        if isinstance(j, list):
            return j
        else:
            return [j]


def main():

    parser = argparse.ArgumentParser()
    parser.add_argument('-f', '--file', help="File/Template of audits events to be sent")
    parser.add_argument('-p', '--time_placeholder', help='Timestamp placeholder token to be replaced')
    parser.add_argument('-d', '--start_date', help='Date in format YYYY/MM/DD')
    args = parser.parse_args()

    start_time = datetime.now()
    if args.start_date:
        start_time = datetime.strptime(args.start_date,'%Y/%m/%d')
    else:
        start_time = start_time - timedelta(days=10)


    msgs = []
    if args.file:
        msgs = read_audits(args.file, start_time, args.time_placeholder)
    else:
        msgs = generate_audits(str(uuid1()), start_time, 10, 20)

    producer = Producer({"bootstrap.servers": "broker:9092"})

    print("Sending {} messages to topic: {}".format(len(msgs), TOPIC))

    for msg in msgs:
        print(".", end="")
        produce_message(producer, TOPIC, json.dumps(msg))


if __name__ == "__main__":
    main()
