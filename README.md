# MoreThanEnoughUtils — Fabric 1.21

This folder is the **Fabric 1.21** port of **MoreThanEnoughUtils** (MTEU). It is a separate Gradle project from the 1.8.9 Forge version.

## Prerequisites

- **Java 21** (required for Minecraft 1.21 and Fabric Loom).  
  When building, Gradle must run on Java 21 (set `JAVA_HOME` to your JDK 21, or use your IDE’s JDK 21 for this project).
- **Gradle** — use the included wrapper (`gradlew.bat` / `./gradlew`); no need to install Gradle.

## Build & run

**Option A — From this folder (standalone)**  
Open this folder (`MoreThanEnoughUtils`) as the project root in your IDE or terminal.

1. Generate the Gradle wrapper (one time, if you have Gradle installed):
   ```bash
   gradle wrapper
   ```
2. Build:
   ```bash
   ./gradlew build
   ```
3. Run client:
   ```bash
   ./gradlew runClient
   ```

**Option B — From repo root**  
If your IDE has the repo root as project, run the 1.21 tasks by using the wrapper from the parent and the subproject (after adding this project to the root `settings.gradle.kts` and fixing root build so it doesn’t fail). See `MIGRATION_ROADMAP.md` in the repo root.

## Prism Launcher / Fabric

- Install **Fabric Loader** and **Fabric API** for **1.21.4** (or the version in `gradle.properties`).
- Build the mod with `./gradlew build`; the JAR is in `build/libs/`.
- Put the JAR in your Prism Launcher mods folder for the Fabric 1.21 profile.

## Migration status

See **MIGRATION_ROADMAP.md** in the repository root for the full plan. This project currently has:

- Fabric 1.21 build and entry point (`FarmHelperFabric`)
- No mixins or features ported yet (Phase 1 done; Phase 2+ in progress)
