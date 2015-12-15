package com.brickcommerce.aws.logging;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by jo on 12.12.15.
 */
public class CloudWatchWriter {

    private static final int AWS_DRAIN_LIMIT = 256; // 1MB of 4K messages -- estimate
    private static final int AWS_LOG_STREAM_MAX_QUEUE_DEPTH = 10000;

    private final String logGroupName;
    private final String logStreamName;

    private AWSLogsClient awsLogsClient;

    private String sequenceTokenCache = null; // aws doc: "Every PutLogEvents request must include the sequenceToken obtained from the response of the previous request.
    private long lastReportedTimestamp = -1;

    private final BlockingQueue<InputLogEvent> queue = new LinkedBlockingQueue<>(AWS_LOG_STREAM_MAX_QUEUE_DEPTH);

    public CloudWatchWriter(String logGroupName, String logStreamName, Regions region) {
        this.logGroupName = logGroupName;
        this.logStreamName = logStreamName;

        awsLogsClient = new AWSLogsClient(); // this should pull the credentials automatically from the environment

        awsLogsClient.setRegion(Region.getRegion(region));

        createLogGroupAndLogStreamIfNeeded();

    }

    public void append(InputLogEvent awsLogEvent) {
        queue.add(awsLogEvent);
    }

    synchronized public void flush() {
        int drained;
        final List<InputLogEvent> logEvents = new ArrayList<>(AWS_DRAIN_LIMIT);
        do {
            drained = queue.drainTo(logEvents, AWS_DRAIN_LIMIT);
            if (!logEvents.isEmpty()) {

                fixTimeStamps(logEvents);

                final PutLogEventsRequest putLogEventsRequest = new PutLogEventsRequest(logGroupName, logStreamName, logEvents);
                putLogEventsRequest.setSequenceToken(sequenceTokenCache);
                try {

                    final PutLogEventsResult putLogEventsResult = awsLogsClient.putLogEvents(putLogEventsRequest); // 1 MB or 10000 messages AWS cap!
                    sequenceTokenCache = putLogEventsResult.getNextSequenceToken();

                } catch (final DataAlreadyAcceptedException daae) {
                    System.out.println("DataAlreadyAcceptedException, will reset the token to the expected one");
                    sequenceTokenCache = daae.getExpectedSequenceToken();
                } catch (final InvalidSequenceTokenException iste) {
                    System.out.println("InvalidSequenceTokenException, will reset the token to the expected one");
                    sequenceTokenCache = iste.getExpectedSequenceToken();
                } catch (Exception e) {
                    System.out.println("Error writing logs");
                }
                logEvents.clear();
            }
        } while (drained >= AWS_DRAIN_LIMIT);
    }

    /**
     * Ensures that the given List of log events fulfills the needs as purposed by aws cloudwatch logs.
     * Sorts the events by their timestamp and ensures that all timestamps are newer then the ones already sent
     * @param logEvents a list of log events
     */
    private void fixTimeStamps(List<InputLogEvent> logEvents) {

        Collections.sort(logEvents, (event1, event2) -> event1.getTimestamp().compareTo(event2.getTimestamp()));

        if (lastReportedTimestamp > 0) {
            for (InputLogEvent event : logEvents) {
                if (event.getTimestamp() < lastReportedTimestamp)
                    event.setTimestamp(lastReportedTimestamp);
                else
                    break;
            }
        }

        lastReportedTimestamp = logEvents.get(logEvents.size() - 1).getTimestamp();
    }

    /**
     * Create log group ans log stream if needed.
     *
     * @return sequence token for the created stream
     */
    private String createLogGroupAndLogStreamIfNeeded() {
        final DescribeLogGroupsResult describeLogGroupsResult = awsLogsClient.describeLogGroups(new DescribeLogGroupsRequest().withLogGroupNamePrefix(logGroupName));
        boolean createLogGroup = true;
        if (describeLogGroupsResult != null && describeLogGroupsResult.getLogGroups() != null && !describeLogGroupsResult.getLogGroups().isEmpty()) {
            for (final LogGroup lg : describeLogGroupsResult.getLogGroups()) {
                if (logGroupName.equals(lg.getLogGroupName())) {
                    createLogGroup = false;
                    break;
                }
            }
        }
        if (createLogGroup) {
            System.out.println("Creating logGroup: " + logGroupName);
            final CreateLogGroupRequest createLogGroupRequest = new CreateLogGroupRequest(logGroupName);
            awsLogsClient.createLogGroup(createLogGroupRequest);
        }
        String logSequenceToken = null;
        boolean createLogStream = true;
        final DescribeLogStreamsRequest describeLogStreamsRequest = new DescribeLogStreamsRequest(logGroupName).withLogStreamNamePrefix(logStreamName);
        final DescribeLogStreamsResult describeLogStreamsResult = awsLogsClient.describeLogStreams(describeLogStreamsRequest);
        if (describeLogStreamsResult != null && describeLogStreamsResult.getLogStreams() != null && !describeLogStreamsResult.getLogStreams().isEmpty()) {
            for (final LogStream ls : describeLogStreamsResult.getLogStreams()) {
                if (logStreamName.equals(ls.getLogStreamName())) {
                    createLogStream = false;
                    logSequenceToken = ls.getUploadSequenceToken();
                }
            }
        }

        if (createLogStream) {
            System.out.println("Creating logStream: " + logStreamName);
            final CreateLogStreamRequest createLogStreamRequest = new CreateLogStreamRequest(logGroupName, logStreamName);
            awsLogsClient.createLogStream(createLogStreamRequest);
        }
        return logSequenceToken;
    }
}
