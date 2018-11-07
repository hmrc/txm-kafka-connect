connect-node:
	docker build -t txm-kafka-connect:dev -f dev-setup/ConnectDockerfile .

connect-msg-producer:
	docker build -t txm-kafka-connect-producer -f dev-setup/ProducerDockerfile .

