# AI Generation Cycle

Use this loop to keep AI-generated code reliable in real projects.

## 1. Configure AI Once

```bash
idempiere-cli config init
```

If AI is unavailable, CLI falls back to templates (same commands still work).

## 2. Generate with Intent

```bash
idempiere-cli add process --name=GenerateInvoices \
  --to=./orderext/org.mycompany.orderext.base \
  --prompt="Generate invoices for confirmed sales orders in the current month, skip already invoiced records"
```

Use prompts that describe:
- business rule
- data scope
- rejection behavior
- side effects

## 3. Validate Structure Immediately

```bash
idempiere-cli validate --strict ./orderext/org.mycompany.orderext.base
```

This catches:
- manifest/build/pom inconsistencies
- missing OSGi descriptors
- structural warnings that often break builds later

## 4. Validate Dependencies

```bash
idempiere-cli deps --dir ./orderext/org.mycompany.orderext.base
```

Use this to fix missing or unused `Require-Bundle` entries before build/deploy.

## 5. Build and Deploy

```bash
idempiere-cli build --dir ./orderext/org.mycompany.orderext.base
idempiere-cli deploy --dir ./orderext/org.mycompany.orderext.base --target /opt/idempiere --hot
```

## 6. Tight Iteration Pattern

When generation is not acceptable:
1. refine prompt with specific constraints
2. regenerate component
3. run `validate --strict` again
4. build and test again

Keep changes small per iteration to isolate regressions.

## Suggested Local Gate

```bash
idempiere-cli validate --strict ./orderext/org.mycompany.orderext.base \
&& idempiere-cli deps --dir ./orderext/org.mycompany.orderext.base \
&& idempiere-cli build --dir ./orderext/org.mycompany.orderext.base --clean
```

