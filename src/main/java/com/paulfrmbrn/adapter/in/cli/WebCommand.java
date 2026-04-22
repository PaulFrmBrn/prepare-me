package com.paulfrmbrn.adapter.in.cli;

import com.paulfrmbrn.adapter.in.web.WebServer;
import com.paulfrmbrn.infrastructure.Settings;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "serve", description = "Start the web UI (default port 8080)")
public class WebCommand implements Callable<Integer> {

    @Option(names = {"--port"}, description = "HTTP port to listen on (default: 8080)", defaultValue = "8080")
    private int port;

    private final Settings settings;

    public WebCommand(Settings settings) {
        this.settings = settings;
    }

    @Override
    public Integer call() throws InterruptedException {
        new WebServer(settings).start(port);
        System.out.printf("Web UI running at http://localhost:%d — press Ctrl+C to stop%n", port);
        Thread.currentThread().join(); // block until process is killed
        return 0;
    }
}
