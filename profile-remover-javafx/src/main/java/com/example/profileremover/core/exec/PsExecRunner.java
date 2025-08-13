package com.example.profileremover.core.exec;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PsExecRunner {
    private final File psExec;
    private final Duration connectTimeout;
    private final Duration execTimeout;
    private final Logger logger = LoggerFactory.getLogger(PsExecRunner.class);

    public PsExecRunner(File psExec, Duration connectTimeout, Duration execTimeout) {
        this.psExec = psExec;
        this.connectTimeout = connectTimeout;
        this.execTimeout = execTimeout;
    }

    public ProcessRunner.Result runWmic(
            String host,
            String wmicArgs,
            Consumer<String> onStdout,
            Consumer<String> onStderr
    ) throws Exception {
        List<String> cmd = List.of(
                psExec.getAbsolutePath(),
                "\\\\" + host,
                "-accepteula",
                "-nobanner",
                "-s",
                "-n", String.valueOf(connectTimeout.toSeconds()),
                "cmd", "/c", "wmic " + wmicArgs
        );
        logger.info("WMIC via PsExec: host={}, args={} (timeout connect={}s exec={}s)", host, wmicArgs,
                connectTimeout.toSeconds(), execTimeout.toSeconds());
        try {
            return ProcessRunner.run(cmd, psExec.getParentFile(), execTimeout, StandardCharsets.UTF_8, onStdout, onStderr);
        } catch (TimeoutException te) {
            throw te;
        }
    }
}


