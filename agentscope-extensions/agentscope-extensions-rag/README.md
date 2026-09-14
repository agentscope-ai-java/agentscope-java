# agentscope-extensions-rag

Parent module for AgentScope Java's Retrieval-Augmented Generation (RAG) integrations. It
aggregates one "build-it-yourself" module (readers + embeddings + vector stores) and several
adapters for hosted/self-hosted RAG platforms, so you can plug document retrieval into an agent
without writing the wiring yourself.

## Sub-modules

| Module | Artifact | Backend |
| --- | --- | --- |
| [`agentscope-extensions-rag-simple`](agentscope-extensions-rag-simple) | `agentscope-extensions-rag-simple` | DIY: readers + embeddings + vector store, run in-process |
| [`agentscope-extensions-rag-bailian`](agentscope-extensions-rag-bailian) | `agentscope-extensions-rag-bailian` | Alibaba Cloud Bailian knowledge base |
| [`agentscope-extensions-rag-dify`](agentscope-extensions-rag-dify) | `agentscope-extensions-rag-dify` | Dify knowledge base API |
| [`agentscope-extensions-rag-ragflow`](agentscope-extensions-rag-ragflow) | `agentscope-extensions-rag-ragflow` | RAGFlow retrieval API |
| [`agentscope-extensions-rag-haystack`](agentscope-extensions-rag-haystack) | `agentscope-extensions-rag-haystack` | Haystack pipeline API |

This module itself is a `pom`-packaged aggregator — add the specific child artifact you need, not
`agentscope-extensions-rag` directly.

## Quick Start (`agentscope-extensions-rag-simple`)

`rag-simple` is the easiest module to get running end-to-end because everything — embedding,
vector store, and retrieval — runs in-process, with no external RAG service to stand up.

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-rag-simple</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### 1. Embed and index documents

```java
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.VDBStoreBase;
import java.util.List;

EmbeddingModel embeddingModel =
        DashScopeTextEmbedding.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("text-embedding-v3")
                .dimensions(1024)
                .build();

VDBStoreBase vectorStore = InMemoryStore.builder().dimensions(1024).build();

SimpleKnowledge knowledge =
        SimpleKnowledge.builder().embeddingModel(embeddingModel).embeddingStore(vectorStore).build();

TextReader reader = new TextReader(512, SplitStrategy.PARAGRAPH, 50);
ReaderInput input = ReaderInput.fromString("AgentScope is a multi-agent framework...");
List<Document> documents = reader.read(input).block();
knowledge.addDocuments(documents).block();
```

### 2. Expose it to an agent as a tool

Wrap `SimpleKnowledge.retrieve(...)` in your own `@Tool` method and register it with a `Toolkit`,
so the agent decides when to search the knowledge base:

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import java.util.List;

class KnowledgeTools {
    private final SimpleKnowledge knowledge;

    KnowledgeTools(SimpleKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Tool(name = "search_docs", description = "Search the indexed documents for relevant passages")
    public String searchDocs(
            @ToolParam(name = "query", description = "The search query") String query) {
        List<Document> hits =
                knowledge.retrieve(query, RetrieveConfig.builder().limit(5).scoreThreshold(0.5).build())
                        .block();
        return hits.isEmpty() ? "No relevant documents found." : hits.toString();
    }
}

Toolkit toolkit = new Toolkit();
toolkit.registerTool(new KnowledgeTools(knowledge));

ReActAgent agent =
        ReActAgent.builder()
                .name("Assistant")
                .sysPrompt("Answer questions using the search_docs tool when relevant.")
                .model("dashscope:qwen-plus")
                .toolkit(toolkit)
                .build();
```

`ReActAgent` also exposes `knowledge(Knowledge)` / `ragMode(RAGMode)` builder fields, but both
`Knowledge` and `RAGMode` are deprecated for removal since 2.0.0 — retrieval is meant to be wired
in at the application layer, as shown above, rather than through the agent builder.

## Learn more

See [`docs/v2/en/integration/rag/simple.md`](../../docs/v2/en/integration/rag/simple.md) for the
full `rag-simple` guide (built-in readers, embedding providers, vector stores, and retrieval
parameters), and [`docs/v2/en/integration/rag/index.md`](../../docs/v2/en/integration/rag/index.md)
for the RAG integration overview.
