package com.example.profileremover.ui;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.function.Consumer;

public class UiAppender extends AppenderBase<ILoggingEvent> {
    private static volatile Consumer<String> listener;

    public static void setListener(Consumer<String> l) {
        listener = l;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        Consumer<String> l = listener;
        if (l != null) {
            l.accept(eventObject.getFormattedMessage());
        }
    }
}


