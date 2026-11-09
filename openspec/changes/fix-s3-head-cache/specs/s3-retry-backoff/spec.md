# Spec Delta

## Purpose

Instances that retry the same failed S3 request do not retry together, and the number of retries stays the same.

## ADDED Requirements

### Requirement: Full jitter between S3 retries

The S3 client SHALL wait before each retry for a delay chosen at random from zero up to the smaller of 60 seconds and
2 to the power of the retry number in seconds, where the first retry is number 0. The S3 client SHALL use the AWS SDK's full
jitter strategy with a base delay of 1 second and a maximum backoff of 60 seconds.

#### Scenario: Delay bounds per retry

- **WHEN** the delay is computed for retry numbers 0 to 4 and for retry number 6 or above
- **THEN** each delay for numbers 0 to 4 is at least zero and at most 2 to the power of the retry number in seconds, and each delay for number 6 or above is at least zero and at most 60 seconds

#### Scenario: Retries of the same request do not wait in step

- **WHEN** many instances retry the same failed S3 request
- **THEN** the delays they choose are drawn independently, so they do not all wait the same time
