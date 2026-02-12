# Análise de Complementaridade: Ecossistema AI + iDempiere

## Mapa do Ecossistema (o que cada peça faz)

### 1. hengsin/idempiere-mcp — "AI fala com iDempiere rodando"

**O que é:** Plugin OSGi que roda DENTRO do iDempiere e expõe operações via MCP (SSE + Streamable HTTP). Depende do idempiere-rest.

**Capabilities confirmadas (demos no YouTube):**
- Buscar Business Partners/Contatos
- Executar processos (AD_Process)
- Criar registros via Message window
- Gerenciar server jobs

**Camada:** Runtime/operacional. Interage com uma instância iDempiere viva.

**Limitações:**
- Proof of concept (README diz "use with care")
- Requer iDempiere rodando + idempiere-rest instalado
- Não funciona com Claude Desktop (issue declarada)
- Auth via REST Token — single tenant por conexão MCP

**Audiência:** Integrador/consultor que quer operar o ERP via AI agent.

---

### 2. oeig-io/wi-idempiere — "Receitas para a IA não errar"

**O que é:** 17 arquivos Markdown que são work instructions (WI) do Chuck Boecking (OEIG) para seu cliente ANS. Documentação feita PARA LLMs consumirem como prompt/contexto.

**Cobertura:**
- **AD Tools:** table-create, column-create, callout (Groovy), model-validator (Groovy), process (Groovy), info-window
- **Operações:** packout (2Pack), rest-api, environment-management, feature-configuration
- **Infra:** postgresql-logging, ssh-remote-connection, deploy-script-troubleshooting
- **Qualidade:** code-validator (audit Groovy/SQL para resource leaks e convenções)
- **Referência:** system-admin-role, quick-info-widget, request-tool

**Camada:** Conhecimento/documentação. Não executa nada — ensina a IA a gerar código e configurações corretas.

**Abordagem:** 100% Groovy/AD_Rule (no-code dentro do iDempiere). Zero plugins Java/OSGi.

**Audiência:** Implementador/integrador que customiza iDempiere sem compilar código.

---

### 3. johnhuang316/code-index-mcp — "AI entende o codebase"

**O que é:** MCP server genérico (Python, 528 stars) que indexa repositórios de código e permite que LLMs busquem/analisem código com setup mínimo.

**Capabilities:**
- Indexa código fonte com embeddings
- Busca semântica por funcionalidade
- Análise de dependências e estrutura

**Camada:** Ferramenta auxiliar. Reduz alucinação dando à IA acesso indexado ao código real do iDempiere.

**Relevância para iDempiere:** Hengsin mencionou na thread do Mattermost que poderia ajudar a reduzir alucinação no Eclipse CoPilot. Não é iDempiere-específico mas resolve um problema real da comunidade.

**Audiência:** Desenvolvedor core/plugin que trabalha no código fonte.

---

### 4. devcoffee/idempiere-cli — "Toolchain que garante qualidade"

**O que é:** CLI Java (20K LOC) que cobre ciclo completo: init → scaffold → validate → build → deploy → migrate → doctor.

**Camada:** Desenvolvimento/CI/CD. Executa operações locais determinísticas.

**Audiência:** Desenvolvedor de plugins OSGi/Java.

---

## Matriz de Complementaridade

| Dimensão | idempiere-mcp | wi-idempiere | code-index-mcp | idempiere-cli |
|----------|:---:|:---:|:---:|:---:|
| Gera código | — | Ensina IA a gerar | — | Gera (template + AI) |
| Valida código | — | code-validator (Groovy) | — | validate (OSGi/Tycho) |
| Executa em runtime | **SIM** (MCP→REST) | — | — | deploy --hot (OSGi) |
| Requer iDempiere rodando | SIM | NÃO | NÃO | NÃO (exceto deploy) |
| Opera no AD | **SIM** (CRUD, processos) | Ensina como | — | — |
| Indexa código | — | — | **SIM** | — |
| CI/CD ready | — | — | — | **SIM** |
| Setup dev environment | — | environment-mgmt | — | doctor + setup-dev-env |
| Formato | Plugin OSGi (Java) | Markdown (WI) | MCP server (Python) | CLI nativa (GraalVM) |
| Stack alvo | Runtime iDempiere | Groovy/AD_Rule | Qualquer codebase | Plugins Java/OSGi |

**Conclusão visual:** ZERO sobreposição real. Cada ferramenta atua em uma camada diferente.

---

## Conexões Estratégicas

### Conexão 1: CLI + idempiere-mcp (Complemento Direto)

O MCP do Hengsin opera no iDempiere rodando. A CLI opera no código/build. São complementares:

```
Developer workflow:
  1. idempiere-cli init          → cria plugin
  2. idempiere-cli add callout   → scaffold componente (AI + template)
  3. idempiere-cli validate      → garante OSGi correto
  4. idempiere-cli build         → compila
  5. idempiere-cli deploy --hot  → instala no iDempiere
  6. idempiere-mcp               → IA opera o ERP (testa, cria dados, executa processos)
```

**Ação:** Documentar esse workflow integrado. A CLI cuida do lado DEV, o MCP cuida do lado RUNTIME.

### Conexão 2: CLI Skills + wi-idempiere (Absorção de Conhecimento)

Os WI do Chuck são essencialmente Skills para integradores/implementadores. As Skills da CLI (via SkillManager) são para desenvolvedores. Dois públicos diferentes, mesmo formato (Markdown).

**Diferença crítica:**
- wi-idempiere → Groovy/AD_Rule (no-code, roda dentro do iDempiere, sem plugin)
- CLI skills → Java/OSGi (plugins compilados, requires Tycho build)

**Ação:** NÃO absorver os WI do Chuck como skills da CLI — são públicos e stacks diferentes. Mas referenciar como "se você quer customizar sem plugin, use estas work instructions com Gemini/Claude. Se precisa de plugin Java, use idempiere-cli."

### Conexão 3: code-index-mcp + CLI validate (Redução de Alucinação)

O code-index-mcp indexa o codebase para a IA entender o contexto. O CLI validate verifica se o output da IA é válido. São duas metades do mesmo problema:

```
AI escreveu código → é correto semanticamente? (code-index ajudou)
                   → é válido estruturalmente? (CLI validate confirma)
```

**Ação:** No README/docs da CLI, recomendar code-index-mcp como ferramenta companion: "Use code-index-mcp para dar contexto do iDempiere ao seu AI agent. Use idempiere-cli validate para verificar o output."

### Conexão 4: CLI como MCP Server → Complementa idempiere-mcp

Quando a CLI expor seus comandos via MCP (roadmap Q2):

```
AI Agent tem 2 MCP servers:
  idempiere-mcp    → opera o ERP (CRUD, processos, queries)
  idempiere-cli    → valida plugin, diagnostica ambiente, analisa deps

Exemplo de workflow autônomo:
  Agent: "Cria um callout que valida CPF no campo C_BPartner.TaxID"
  1. code-index-mcp → busca como callouts existentes são implementados
  2. AI gera código baseado em skill + contexto
  3. idempiere-cli validate --json → confirma que OSGi está correto
  4. idempiere-cli build → compila
  5. idempiere-cli deploy --hot → instala
  6. idempiere-mcp → cria BP de teste com CPF inválido → verifica se callout rejeita
```

**Isso é o "killer demo" para a World Conference.** Ciclo completo automatizado.

---

## Priorização Atualizada (impacto no roadmap da CLI)

### SUBIU de prioridade

**MCP Server mode (era Q2, considerar antecipar para Q1)**

Razão: Hengsin já publicou o MCP server dele. O ecossistema está se formando AGORA. Se a CLI demora para ter MCP, perde a janela de ser "a outra metade" do toolkit.

Sugestão pragmática: não precisa expor todos os comandos. Começar com 3 tools MCP:
- `validate` → retorna JSON com erros/warnings
- `doctor` → retorna status do ambiente
- `deps` → retorna bundles missing/unused

Isso já é suficiente para um AI agent usar a CLI como "quality gate".

**--json em todos os comandos (P3, já planejado)**

Pré-requisito para MCP. Sem saída estruturada, não tem MCP. Mantém como P3 mas executa com urgência.

### MANTÉM prioridade

**P1-P6-P2-P8** (fixes técnicos nos AI clients)

Ainda são fundação necessária. Sem isso o código é frágil.

### DESCEU de prioridade

**Dynamic skill discovery (P5)**

Razão: Os skills do Hengsin (idempiere-skills) não foram confirmados publicamente. O Chuck usa WIs em formato próprio, não SKILL.md. Investir em discovery dinâmico agora é otimizar para um ecossistema que ainda não existe. O mapeamento estático funciona.

**Publish devcoffee skills (era Q2)**

Razão: Skills para scaffolding Java/OSGi competem com o que a IA já faz bem com code-index-mcp + contexto do codebase. Melhor investir em MCP server (que IA NÃO substitui) do que em skills (que IA já consome de qualquer fonte).

---

## Posicionamento Final: Quem Faz O Quê

```
┌─────────────────────────────────────────────────────┐
│              Developer/Integrator                     │
│                                                       │
│  "Quero customizar sem plugin"                        │
│  → wi-idempiere (WIs) + Gemini/Claude                │
│  → Groovy callouts, model validators, processes       │
│  → Resultado roda no iDempiere via AD_Rule            │
│                                                       │
│  "Quero desenvolver plugin Java"                      │
│  → idempiere-cli (scaffold, validate, build, deploy)  │
│  → code-index-mcp (contexto para IA não alucinar)     │
│  → Resultado: bundle OSGi instalado via hot deploy    │
│                                                       │
│  "Quero que AI opere o ERP"                           │
│  → idempiere-mcp (MCP server no runtime)              │
│  → CRUD, processos, queries via AI agent              │
│                                                       │
│  "Quero que AI desenvolva E valide"                   │
│  → code-index-mcp + idempiere-cli MCP + idempiere-mcp │
│  → Ciclo completo: gerar → validar → deploy → testar  │
└─────────────────────────────────────────────────────┘
```

**One-liner para apresentar na comunidade:**

"idempiere-mcp lets AI operate the ERP. idempiere-cli lets AI build reliable plugins for it. Together they close the loop."