# idempiere-cli — A Developer CLI for iDempiere

> **Disclaimer:**  
> This document is a **draft proposal**. The `idempiere-cli` project is still in its **early design phase** and not yet officially implemented or released.  
> There is currently **no client or production version** available.  
>  
> **We welcome feedback, ideas, and contributions** from the community to shape and improve this tool together!

## Summary

`idempiere-cli` is a command-line tool built with Quarkus, designed to modernize the developer experience for iDempiere plugin development. It streamlines the entire workflow — from setting up a development environment to building, testing, and publishing plugins.

The CLI scaffolds OSGi-ready plugins, integrates with Eclipse PDE, and follows iDempiere architectural standards such as declarative service registration.

---

## Developer Journey

    [doctor]
       ↓
    [setup-dev-env]
       ↓
    [init plugin]
       ↓
    [add features + (future) AD modeling]
       ↓
    [run/test/build]
       ↓
    [publish/distribute]

---

## Core Goals

- Standardize plugin structure
- Simplify local environment setup using Docker and Git
- Scaffold modular extensions (callouts, processes, forms, etc.)
- Generate Eclipse-compatible `.target` and `.launch` files
- Package and publish plugins to GitHub or Maven
- Initial support for Eclipse, with plans for IntelliJ and VS Code

---

## CLI Commands

### doctor

Check required tools and environment:

```bash
idempiere-cli doctor
```

Checks for:
- Java 17+, Maven
- Git
- PostgreSQL or Oracle
- Eclipse (or IntelliJ/VS Code)
- Cloned `idempiere` core (and optionally `idempiere-rest`)

Optional fix mode:

```bash
idempiere-cli doctor --fix
```

---

### setup-dev-env

Bootstrap a complete local development environment using [idempiere-dev-setup](https://github.com/hengsin/idempiere-dev-setup):

```bash
idempiere-cli setup-dev-env --ide=eclipse --db=postgres --with-docker
```

---

### init <PluginName>

Scaffold a new plugin with selected features:

```bash
idempiere-cli init org.mycompany.myplugin \
  --with-callout \
  --with-event-handler \
  --with-process \
  --with-zk-form \
  --with-report
```

Creates:
- OSGi-compliant plugin with `plugin.xml`, `MANIFEST.MF`
- Optional `Activator.java` class
- Feature-specific stubs using Declarative Services annotations
- `.target` and `.launch` files for Eclipse

---

### add <extension-type>

Extend an existing plugin by adding new components:

```bash
idempiere-cli add callout --name=MyCallout --to=org.myplugin
idempiere-cli add zk-form --name=CustomDashboard
```

---

## Plugin Features Supported (Initial Version)

| Feature        | Description                                                           |
|----------------|------------------------------------------------------------------------|
| Callout        | Business logic triggered when users interact with specific fields      |
| Event Handler  | Logic that reacts to system events like record creation or changes     |
| Process        | Background/manual tasks triggered from buttons or menus                |
| ZK Form        | Custom UI forms built with the ZK framework                            |
| Report         | Jasper-based or built-in reports integrated into the user interface    |
| Model Class    | PO-based model class to extend or override default table logic         |

> Note: Additional extension types like REST endpoints, payment gateways, and media viewers are planned for future releases.

---

## (Planned) AD Modeling Support

Future versions will support scaffolding Application Dictionary definitions:

```bash
idempiere-cli add ad-table --name=Z_MyTable
idempiere-cli add ad-window --name=Z_MyWindow
idempiere-cli generate ad-xml --table=Z_MyTable
```

Output formats:
- AD XML
- Optional SQL/JSON
- Future layout hints for field positioning (`top`, `bottom`, `group`, etc.)

---

### eclipse-config

Generate Eclipse PDE configuration files:

```bash
idempiere-cli eclipse-config --include-rest
```

Creates:
- `.target` definition pointing to iDempiere and REST (if selected)
- `.launch` configuration for running Equinox in Eclipse

---

### build / run

Compile or launch plugin inside local workspace:

```bash
idempiere-cli build --plugin=org.myplugin
idempiere-cli run
```

- `build`: Runs `mvn install` and produces `.jar` and `.xml` artifacts
- `run`: Launches OSGi container via Eclipse or embedded test runner (future)

---

### publish

Distribute plugin artifacts to a release platform:

```bash
idempiere-cli publish --to=github
```

Outputs:
- Plugin `.jar` + `.xml`
- GitHub release or Maven-ready package
- Future: GitHub Actions automation

---

## Tech Stack

| Component        | Tool                      |
|------------------|---------------------------|
| Language         | Java 17+                  |
| CLI Framework    | Quarkus CLI Plugin        |
| Templates        | Qute                      |
| Build Tool       | Maven                     |
| IDE Integration  | Eclipse PDE               |
| Packaging        | JBang                     |
| Source Control   | GitHub                    |

---

## Future Roadmap (just initial ideas...)

| Feature                 | Description                                           |
|--------------------------|-------------------------------------------------------|
| `doctor --fix`           | Auto-install missing dependencies                     |
| `generate zk-form`       | ZUL form + controller scaffolding                     |
| `add rest-process`       | REST-exposed process with CLI + AD integration        |
| `generate ad-xml`        | Reverse-generate from database table                  |
| `publish`                | CI template generation, Maven deploy                  |
| `create-template`        | Save plugin boilerplates as reusable templates        |

---

## Community Value

- Simplifies plugin development
- Encourages modular architecture
- Increases discoverability of iDempiere extension points
- Aligns with iDempiere 9+ standards (Declarative Services, REST)
- Creates a consistent onboarding path for new contributors

---

## Contributors

This proposal is being developed by the **dev&Co. Team**, **Saul Piña** and **Eduardo Gil**

---

## Feedback & Collaboration

We’re looking for feedback from:
- Plugin developers
- Core maintainers
- DX/tooling specialists
- Newcomers to the platform

Your ideas can shape the future of iDempiere development experience.
