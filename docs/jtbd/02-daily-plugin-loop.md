# Daily Plugin Development Loop

This is the practical day-to-day flow for plugin work.

## 1. Create Project

Multi-module (recommended):

```bash
idempiere-cli init org.mycompany.orderext --with-feature --with-fragment
```

Standalone:

```bash
idempiere-cli init org.mycompany.orderext --standalone
```

## 2. Add Components

```bash
idempiere-cli add callout --name=OrderDateCallout --to=./orderext/org.mycompany.orderext.base
idempiere-cli add process --name=GenerateInvoices --to=./orderext/org.mycompany.orderext.base
idempiere-cli add test --dir=./orderext/org.mycompany.orderext.base
```

Recommended for team/agent workflows:
- run deterministic `init/add` first
- let your external agent implement domain logic in generated classes
- keep CLI commands as the objective validation/build gate

If you are evaluating internal AI diagnostics, see:
- [AI Generation Cycle](05-ai-generation-cycle.md)

## 3. Validate Early

```bash
idempiere-cli validate ./orderext/org.mycompany.orderext.base
```

Strict mode for stronger quality gate:

```bash
idempiere-cli validate --strict ./orderext/org.mycompany.orderext.base
```

## 4. Build

```bash
cd orderext
./mvnw verify       # or: ./mvnw clean verify
```

Note: in multi-module projects, run from the project root.

## 5. Deploy

Copy deploy:

```bash
idempiere-cli deploy --dir ./orderext/org.mycompany.orderext.base --target /opt/idempiere
idempiere-cli deploy --dir ./orderext --target /opt/idempiere
```

Hot deploy:

```bash
idempiere-cli deploy --dir ./orderext/org.mycompany.orderext.base --target /opt/idempiere --hot
```

## 6. Package for Distribution

```bash
idempiere-cli dist --dir ./orderext
idempiere-cli dist --dir ./orderext --skip-build   # if already built
```

## Optional Diagnostics

```bash
idempiere-cli deps --dir ./orderext/org.mycompany.orderext.base
idempiere-cli info --dir ./orderext/org.mycompany.orderext.base
idempiere-cli diff-schema --table=C_Order --dir ./orderext/org.mycompany.orderext.base
```
