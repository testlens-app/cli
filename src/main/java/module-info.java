module app.testlens.cli {
    requires dev.tamboui.core;
    requires dev.tamboui.toolkit;
    requires dev.tamboui.tui;
    requires dev.tamboui.widgets;
    requires info.picocli;
    requires io.github.javadiffutils;
    requires java.net.http;
    requires tools.jackson.databind;
    requires static jakarta.annotation;
    requires static org.jspecify;

    opens app.testlens.cli;
    opens app.testlens.cli.client.model to tools.jackson.databind;
}
