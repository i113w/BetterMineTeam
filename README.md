> This is the `Forge 1.20.1` port branch of Better Mine Team.
> 
> It does not fully share the same codebase as the NeoForge branch, and some internal implementations may differ.


## Building the Mod in an IDE

Compared to the NeoForge branch, the Forge setup may require an additional manual step.

After making code changes — including the first setup — run the following command before launching the game from your IDE:

```bash
# IntelliJ IDEA
./gradlew clean genIntellijRuns build

# Eclipse
./gradlew clean genEclipseRuns build
```