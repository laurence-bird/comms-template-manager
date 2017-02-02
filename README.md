# Template manager

[![CircleCI](https://circleci.com/gh/ovotech/comms-template-manager.svg?style=svg)](https://circleci.com/gh/ovotech/comms-template-manager)

A UI for managing and publishing comm templates.

## Running it locally

You can run the service directly with SBT via `sbt run` or by running the docker image:
* `sbt docker:publishLocal`
* `docker-compose up`

## Tests

`sbt testWithDynamo` to run the unit tests.

`sbt dockerComposeTest` to run the service tests. These involve running the service and its dependencies (DynamoDB and a fake S3 API) using docker-compose.

## Deployment

The service is deployed continuously to both the UAT and PRD environments via the [CircleCI build](https://circleci.com/gh/ovotech/comms-template-manager)

## Credstash

This service uses credstash for secret management, and this dependency is required if you want to publish the docker container for this project locally or to a remote server, or run the docker-compose tests. Information on how to install credstash can be found in the [Credstash readme](https://github.com/fugue/credstash)
