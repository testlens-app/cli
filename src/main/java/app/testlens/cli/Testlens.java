package app.testlens.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "testlens",
    mixinStandardHelpOptions = true,
    subcommands = { PrCommand.class },
    versionProvider = Testlens.VersionResourceReader.class
)
public class Testlens {

    static void main(String[] args) {
        System.exit(new CommandLine(new Testlens()).setCaseInsensitiveEnumValuesAllowed(true).execute(args));
    }

    public static class VersionResourceReader implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            var resourceAsStream = getClass().getResourceAsStream("/version.txt");
            if (resourceAsStream == null) {
                return new String[0];
            }
            try (var inputStream = resourceAsStream) {
                return new String[] { new String(inputStream.readAllBytes(), UTF_8).trim() };
            } catch (IOException e) {
                return new String[0];
            }
        }
    }
}
