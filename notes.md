- later: Show environment data that we already capture: os, java version
- later: ability to filter executions table
- What's the sort order in the statistics table?! percentage? then we need to show two digits or the total number of executions
- Footer line in statistics table need to be at the bottom of the view port
- later later: cli mode should be plain text, no console control chars
- cli mode for test executions shows `sha @ branch` where `branch` seems to be the target branch. it should be better visible whether this was an execution on a PR or on a (default) branch. Also think about gh merge queue branches and whether it makes sense to display them in a specific way.

## Fuzzy matching of failures

- later later later: determine whether failed runs for a sha have something in common compared to successful runs. e.g. for a single sha, do all failures have the same operating system?

