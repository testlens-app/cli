// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.AutoComplete;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
    name = "completion",
    description = "Generate a completion script for bash and zsh",
    mixinStandardHelpOptions = true
)
public class CompletionCommand implements Callable<Integer> {

    @Spec
    @Nullable
    CommandSpec spec;

    @Override
    public Integer call() {
        var root = requireNonNull(spec).root();
        var out = spec.commandLine().getOut();
        // the generated bash script bootstraps bashcompinit when sourced from zsh
        out.print(AutoComplete.bash(root.name(), root.commandLine()));
        out.flush();
        return 0;
    }
}
