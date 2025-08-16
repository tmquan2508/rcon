package com.tmquan2508.rcon;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Plugin(name = "NetcatLogAppender", category = "Core", elementType = "appender", printObject = true)
public class LogAppender extends AbstractAppender {
    private final PrintWriter out;

    public LogAppender(PrintWriter out) {
        super("NetcatLogAppender", null,
              PatternLayout.newBuilder().withPattern("[%d{HH:mm:ss} %level]: %msg%n%throwable").build(),
              true, null);
        this.out = out;
    }

    @Override
    public void append(LogEvent event) {
        final byte[] bytes = getLayout().toByteArray(event);
        String message = new String(bytes, StandardCharsets.UTF_8);

        if (out != null && !out.checkError()) {
            out.print(message);
            out.flush();
        }
    }
}