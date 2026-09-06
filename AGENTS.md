## OS Map Viewer Agent Guidelines

### General
- Never leave comments or Javadocs in code. .java files are for code only, comments, notes, and explanations should be placed in `docs/agent-notes`. Generate this folder if not present.
- Never mix plugin code and core code. Changes should be made to the API module to permit actions between plugins and the core viewer.
- Never use wildcard imports (ie `java.awt.*`) and instead use single class imports.
- Never use fully qualified paths inline unless there are conflict errors such as `java.awt.timer` and `javax.swing.timer` used in one class. Prefer imports.
- Follow RuneLite's code convention, described here `https://github.com/runelite/runelite/wiki/Code-Conventions`.
- Remove old unit tests and their folders after use. Do not remove `AtlasInspectorApp`, `ShortestPathDataCheck`, or `MapAreaLabelsDump` in any circumstance.
- Never write developer focused wording into user facing docs. This includes method/class/variable names, as well as API endpoints or anything else targeted at developers.
- Only write developer focused wording into `docs/DEVELOPER_NOTES.md` and `docs/PLUGIN_API.md`
- Use US English - not UK English - for both code naming schema and docs.

### Config
- Use the shared config manager for persistent settings.
- Never change a config key or group without migrating.

### Threading and concurrency
- Never use `Thread.sleep()`
- Swing/UI actions should use an `invokeLater` or concurrent queue to act on the render thread. Never execute actions from the wrong thread.

### Testing
You cannot verify runtime visual behavior or GUI layout yourself, even if you have screen-capture or computer use tools available. 
After completing a task, do not declare it done. Instead:
- Tell the user what to test, including changed behavior, edge cases, etc.
- Wait for the user to confirm the changes are functional before marking the task complete. A clean launch is not a passing test.
- Remind the user to insert the license into new files. The copyright may differ, so do not insert it yourself.

### Java Usage
- All code must be Java 17 compatible.
- No use of reflection besides what may already be present. Do not remove existing reflective access calls, but do not add more.
- No executing external processes.
- No downloading or use of dynamic code loading, including classloading. The core plugin loader is an exception to this rule.
- No runtime code generation.