# Contributing to GFBS: Morphe

Thank you for considering a contribution to GFBS: Morphe. This guide applies to the [official repository](https://github.com/LytharaLab/GFBS-Morphe) and describes the expected workflow for bug reports, feature proposals, code changes, documentation updates, and tests.

By participating in this project, you agree to follow our
[Code of Conduct](CODE_OF_CONDUCT.md).

## Before you begin

- Search the [existing issues](https://github.com/LytharaLab/GFBS-Morphe/issues) and [pull requests](https://github.com/LytharaLab/GFBS-Morphe/pulls) before creating a duplicate.
- Keep each issue or pull request focused on one problem or feature.
- Discuss large API changes, new subsystems, or compatibility-breaking behavior before implementing them.
- Do not include private information, credentials, access tokens, personal file paths, private server addresses, or unrelated internal project material in issues, logs, commits, or pull requests.

## Development environment

The project currently targets:

- Minecraft `1.20.1`
- Minecraft Forge `47.4.16`
- Java `17`
- Gradle through the included wrapper

Clone the official repository:

```bash
git clone https://github.com/LytharaLab/GFBS-Morphe.git
cd GFBS-Morphe
```

Import it as a Gradle project in your IDE. Use the included Gradle wrapper instead of a separately installed Gradle version.

Build the project:

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

Run the full verification suite:

```bash
./gradlew check
```

Run the focused smoke tests when working on the renderer-independent core or Lua runtime:

```bash
./gradlew coreSmoke
./gradlew luaSmoke
```

## Reporting bugs

Open bug reports in the [issue tracker](https://github.com/LytharaLab/GFBS-Morphe/issues). A useful bug report should include:

- The GFBS: Morphe version or commit.
- The Minecraft and Forge versions.
- Whether the problem occurs on the client, dedicated server, or both.
- A minimal list of other installed mods required to reproduce the issue.
- Clear reproduction steps.
- Expected and actual behavior.
- A minimal Lua document or Java integration example when relevant.
- Relevant logs or stack traces with private information removed.

Do not report security vulnerabilities in a public issue when disclosure would put users at risk. Use a private repository security-reporting channel when one is available.

## Proposing features

Open feature proposals in the [issue tracker](https://github.com/LytharaLab/GFBS-Morphe/issues). Feature proposals should explain:

- The concrete use case.
- Why the existing API cannot solve it cleanly.
- The expected Lua and Java-facing behavior.
- Compatibility, rendering-backend, networking, and security implications.
- A small example of the proposed API when possible.

Avoid proposals that add project-specific behavior to the general-purpose runtime. Reusable functionality belongs in GFBS: Morphe; content that only serves one dependent mod usually belongs in that mod.

## Code standards

### Java

- Use Java 17 language features only.
- Follow the existing four-space indentation and brace style.
- Keep public API names explicit and stable.
- Prefer small, focused classes and methods over unrelated utility collections.
- Validate arguments at API and network boundaries.
- Keep common code side-neutral. Client-only Minecraft classes must not leak into code that can load on a dedicated server.
- Use namespaced `ResourceLocation` identifiers for externally registered widgets, effects, modules, systems, documents, and HUD layers.
- Preserve the backend-neutral `UiCanvas` abstraction unless a feature genuinely requires Minecraft-specific rendering access.
- Do not silently swallow failures that should be visible to developers. Provide actionable error messages without exposing sensitive data.

### Lua runtime and UI scripts

- Keep the sandbox restrictions intact. Do not expose filesystem access, process execution, unrestricted networking, Java reflection, or unrestricted class loading.
- Treat all values received from scripts or clients as untrusted input.
- Keep frame-based behavior independent of Minecraft's 20 TPS tick where the existing runtime uses frame timing.
- Use namespaced IDs for extension-owned widget and effect types.
- Add or update a minimal showcase when introducing user-facing Lua behavior.
- Avoid undocumented global state and project-specific assumptions in the shared runtime.

### Networking and server authority

- Never transmit arbitrary Lua source from a server or client.
- Preserve session and document validation for UI actions.
- Keep payload depth, size, and rate limits enforced.
- Validate all client-provided action data before changing authoritative server state.
- Consider multiplayer, reconnects, stale sessions, and dedicated-server class loading in every networking change.

### Documentation

- Write repository-facing documentation in clear English.
- Document behavior that is present in the submitted code, not planned or private functionality.
- Keep examples compilable or directly runnable whenever practical.
- Use relative links for files inside the repository and verify that every link resolves.
- Do not add private contact details, private roadmaps, private service endpoints, or internal-only notes.

## Tests

Add tests for bug fixes and behavior changes whenever the affected code can be tested outside Minecraft.

At minimum, a pull request should pass:

```bash
./gradlew check
```

Changes to the Lua sandbox must include tests for both allowed and rejected behavior. Changes to layout, animation, input routing, serialization, or other renderer-independent systems should include focused core tests.

Manual in-game testing is expected for rendering, input, Forge lifecycle, screen layering, HUD interaction, and networking changes. Describe the manual test scenario in the pull request.

## Pull request process

1. Create a branch from the current default branch.
2. Make a focused change with clear commits.
3. Update tests and public documentation together with the implementation.
4. Run the relevant Gradle verification tasks.
5. Open a [pull request](https://github.com/LytharaLab/GFBS-Morphe/pulls) with a concise title and a complete description.
6. Explain the problem, the chosen solution, compatibility impact, tests performed, and any remaining limitations.
7. Address review comments with additional commits instead of hiding unrelated changes in a force-pushed rewrite unless maintainers request it.

A pull request should not contain generated build output, IDE metadata, local run directories, logs, credentials, unrelated formatting changes, or bundled third-party binaries unless the repository explicitly requires them.

Maintainers may request changes, split an oversized pull request, or decline a contribution that conflicts with the project's scope, compatibility requirements, security model, or maintenance capacity.

## Commit messages

Use short, descriptive commit messages written in the imperative mood. Examples:

```text
Fix HUD layering below active screens
Add validation for external module values
Document interactive HUD behavior
```

## Licensing

By submitting a contribution, you agree that your contribution will be licensed under the repository's [MIT License](LICENSE).
