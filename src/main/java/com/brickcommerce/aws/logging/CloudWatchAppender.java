package com.brickcommerce.aws.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.logs.model.InputLogEvent;
import lombok.Setter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CloudWatchAppender extends AppenderBase<ILoggingEvent> {

    // the following four variables reflect the log configuration that
    // can be set via parameters in the lockback.xml
    @Setter
    private int flushPeriod = 10;
    @Setter
    private String region;
    @Setter
    private String logGroupName = "default";
    @Setter
    private String logStreamName;
    @Setter
    PatternLayout patternLayout = new PatternLayout();

    private CloudWatchWriter cloudWatchWriter;
    private ScheduledFuture<?> schedulerFuture;

    /**
     * Helper method for lazy instantiation of the CloudWatchWriter which is necessary
     * to prevent the instantiation of loggers during initalisation of log4j
     *
     * @return the CloudWatch writer
     */
    public CloudWatchWriter getCloudWatchWriter() {

        if (cloudWatchWriter == null) {

            // determine the configuration in the follwing order:
            // env variables, logback parameters, default values

            String group = System.getProperty("LOG_GROUP_NAME");
            if (group == null) {
                group = logGroupName;
            }

            String stream = System.getProperty("LOG_STREAM_NAME");
            if (stream == null) {
                stream = logStreamName;
            }
            if (stream == null) {
                stream = retrieveInstanceId() + "_" + LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
            }

            if (System.getProperty("LOG_REGION") != null) {
                region = System.getProperty("LOG_REGION");
            }
            Regions logRegion = null;
            if (region != null) {
                logRegion = Regions.fromName(region);
            }

            int period = flushPeriod;
            if (System.getProperty("LOG_FLUSH_PERIOD") != null) {
                period = Integer.valueOf(System.getProperty("LOG_FLUSH_PERIOD"));
            }
            final CloudWatchWriter writer = new CloudWatchWriter(group, stream, logRegion);
            cloudWatchWriter = writer;

            // start scheduler for flushing
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            schedulerFuture = scheduler.scheduleAtFixedRate(writer::flush, period, period, TimeUnit.SECONDS);
        }
        return cloudWatchWriter;
    }

    /**
     * Create AWS log event based on the log4j log event and put it to the cloudwatchwriter.
     */
    @Override
    public void append(final ILoggingEvent event) {

        final InputLogEvent awsLogEvent = new InputLogEvent();
        final long timestamp = event.getTimeStamp();

        awsLogEvent.setTimestamp(timestamp);
        awsLogEvent.setMessage(patternLayout.doLayout(event));

        getCloudWatchWriter().append(awsLogEvent);
    }

    @Override
    public void stop() {
        super.stop();
        if(schedulerFuture != null) {
            schedulerFuture.cancel(false);
            getCloudWatchWriter().flush();
        }
    }

    /**
     * Retrieve AWS instance id you are running on.
     *
     * @return instanceId the instance id
     */
    public static String retrieveInstanceId() {

        final int AWS_CONNECTION_TIMEOUT_MILLIS = 5000;
        final String AWS_INSTANCE_METADATA_SERVICE_URL = "http://169.254.169.254/latest/meta-data/instance-id";

        String hostId;
        try {
            hostId = InetAddress.getLocalHost().getHostName(); // in some unlikely case the AWS metadata service is down
        } catch (UnknownHostException ux) {
            hostId = "unknown";
        }
        String inputLine;
        try {
            final URL EC2MetaData = new URL(AWS_INSTANCE_METADATA_SERVICE_URL);
            final URLConnection EC2MD = EC2MetaData.openConnection();
            EC2MD.setConnectTimeout(AWS_CONNECTION_TIMEOUT_MILLIS);
            final BufferedReader in = new BufferedReader(new InputStreamReader(EC2MD.getInputStream()));
            while ((inputLine = in.readLine()) != null) {
                hostId = inputLine;
            }
            in.close();
        } catch (IOException iox) {
            System.out.println(new SimpleDateFormat("yyyy.MM.dd HH.mm.ss").format(new Date()) +
                    " CloudWatchAppender: Could not connect to AWS instance metadata service, using " + hostId + " for the hostId.");
        }
        return hostId;
    }

}
