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

Optional AI prompt:

```bash
idempiere-cli add callout --name=OrderDateCallout \
  --to=./orderext/org.mycompany.orderext.base \
  --prompt="Reject future order dates and show a user-friendly message"
```

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
idempiere-cli build --dir ./orderext/org.mycompany.orderext.base --clean
```

Note: in multi-module projects, you can also run from root (`--dir ./orderext`) and CLI resolves the `.base` module automatically.

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

## 6. Package

ZIP:

```bash
idempiere-cli package --dir ./orderext/org.mycompany.orderext.base --format=zip
```

P2 (run from multi-module root):

```bash
cd orderext
idempiere-cli package --format=p2
```

## Optional Diagnostics

```bash
idempiere-cli deps --dir ./orderext/org.mycompany.orderext.base
idempiere-cli info --dir ./orderext/org.mycompany.orderext.base
idempiere-cli diff-schema --table=C_Order --dir ./orderext/org.mycompany.orderext.base
```
