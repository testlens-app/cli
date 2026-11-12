// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "completion",
    description = "Generate a completion script for bash, zsh, or fish",
    mixinStandardHelpOptions = true
)
public class CompletionCommand implements Callable<Integer> {

    @Spec
    @Nullable
    CommandSpec spec;

    @Option(
        names = { "-s", "--shell" },
        required = true,
        description = "Shell type: ${COMPLETION-CANDIDATES}",
        defaultValue = "bash"
    )
    @Nullable
    Shell shell;

    @Override
    public Integer call() {
        var root = requireNonNull(spec).root();
        var script = switch (requireNonNull(shell)) {
            // picocli's bash script bootstraps bashcompinit when sourced from zsh
            case BASH, ZSH -> AutoComplete.bash(root.name(), root.commandLine());
            case FISH -> fish(root.name(), root);
        };
        var out = spec.commandLine().getOut();
        out.print(script);
        out.flush();
        return 0;
    }

    // picocli has no fish support (remkop/picocli#725); this follows the approach of PR remkop/picocli#2463
    static String fish(String scriptName, CommandSpec root) {
        var out = new StringBuilder();
        appendFish(out, scriptName, root, "");
        return out.toString();
    }

    private static void appendFish(StringBuilder out, String scriptName, CommandSpec spec, String parentCondition) {
        var subcommands = spec
            .subcommands()
            .values()
            .stream()
            .map(CommandLine::getCommandSpec)
            .filter(sub -> !sub.usageMessage().hidden())
            .distinct()
            .toList();
        var names = new StringBuilder();
        subcommands.forEach(sub -> names.append(' ').append(sub.name()));
        var condition = subcommands.isEmpty()
            ? parentCondition
            : parentCondition + " -n 'not __fish_seen_subcommand_from" + names + "'";
        for (OptionSpec option : spec.options()) {
            if (option.hidden()) {
                continue;
            }
            out.append("complete -c ").append(scriptName).append(condition);
            for (String name : option.names()) {
                if (name.startsWith("--")) {
                    out.append(" -l ").append(name.substring(2));
                } else if (name.length() == 2) {
                    out.append(" -s ").append(name.substring(1));
                } else {
                    out.append(" -o ").append(name.substring(1));
                }
            }
            if (option.arity().max() > 0) {
                out.append(" -r");
                var candidates = option.completionCandidates();
                if (candidates != null) {
                    out.append(" -f -a '").append(String.join(" ", candidates)).append('\'');
                }
            }
            out.append(" -d '").append(fishDescription(option.description())).append("'\n");
        }
        for (CommandSpec sub : subcommands) {
            out
                .append("complete -c ")
                .append(scriptName)
                .append(condition)
                .append(" -f -a ")
                .append(sub.name())
                .append(" -d '")
                .append(fishDescription(sub.usageMessage().description()))
                .append("'\n");
            appendFish(out, scriptName, sub, parentCondition + " -n '__fish_seen_subcommand_from " + sub.name() + "'");
        }
    }

    private static String fishDescription(String[] description) {
        return description.length == 0 ? "" : description[0].replace("\\", "\\\\").replace("'", "\\'");
    }

    enum Shell {
        BASH,
        ZSH,
        FISH,
    }
}
