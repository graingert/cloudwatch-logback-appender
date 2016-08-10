CloudWatchAppender
==================

Emits logback events into AWS CloudWatch streams.

## Build

    $ git clone https://github.com/brick-commerce/cloudwatch-logback-appender
    $ cd CloudWatchAppender
    $ mvn install

## Usage
```
    <appender name="CloudWatchAppender" class="com.brickcommerce.aws.logging.CloudWatchAppender">
        <logGroupName>MyGroup</logGroupName>
        <region>eu-central-1</region>
        <flushPeriod>5</flushPeriod>
        <PatternLayout>
            <Pattern>%5p | %d{ISO8601}{UTC} | %t | %C | %M:%L | %m %ex %n</Pattern>
        </PatternLayout>
    </appender>

```
## Configuration variables

Optional logback appender attributes:

+ **logGroupName**: the name of the AWS log group (default: "default").
+ **logStreamName**: the name of the AWS log stream inside the AWS log group from above (default: Instancename_timestamp).
+ **region** the aws region where logs should be saved (default: AWS default)
+ **flushPeriod**: the period of the flusher in seconds (default: 10).
 
Those parameters might also be be overridden by environment variable (see below).

## Environment variables

Your AWS credentials should be specified in the standard way.

You can also supply the AWS log stream name via environment:

+ **LOG_GROUP_NAME**: sets the AWS log group name.
+ **LOG_STREAM_NAME**: sets the AWS log stream name.
+ **LOG_REGION**: set the AWS region
+ **LOG_FLUSH_PERIOD**: set the period for flushing logs to cloud watch

## License

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0

## Issues
Raise issues on https://github.com/brick-commerce/cloudwatch-logback-appender/issues
