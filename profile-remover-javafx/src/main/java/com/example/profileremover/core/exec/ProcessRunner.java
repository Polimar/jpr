package com.example.profileremover.core.exec;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ProcessRunner {
    public record Result(int exitCode, String stdout, String stderr) {}

    public static Result run(
            List<String> command,
            File workingDir,
            Duration timeout,
            Charset charset,
            Consumer<String> onStdout,
            Consumer<String> onStderr
    ) throws IOException, InterruptedException, TimeoutException {
        return run(command, workingDir, timeout, charset, charset, onStdout, onStderr);
    }

    public static Result run(
            List<String> command,
            File workingDir,
            Duration timeout,
            Charset stdoutCharset,
            Charset stderrCharset,
            Consumer<String> onStdout,
            Consumer<String> onStderr
    ) throws IOException, InterruptedException, TimeoutException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) pb.directory(workingDir);
        Process p = pb.start();

        ExecutorService ex = Executors.newFixedThreadPool(2);
        Future<String> outFut = ex.submit(() -> readStream(p.getInputStream(), stdoutCharset, onStdout));
        Future<String> errFut = ex.submit(() -> readStream(p.getErrorStream(), stderrCharset, onStderr));

        boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
            ex.shutdownNow();
            throw new TimeoutException("Process timeout");
        }
        String out;
        String err;
        try {
            out = outFut.get(200, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            out = "";
        }
        try {
            err = errFut.get(200, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            err = "";
        }
        ex.shutdown();
        return new Result(p.exitValue(), out, err);
    }

    private static String readStream(InputStream is, Charset cs, Consumer<String> sink) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, cs))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
                if (sink != null) sink.accept(line);
            }
            return sb.toString();
        }
    }
}


