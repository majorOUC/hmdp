package com.hmdp.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Configuration
public class LangChain4jConfig {

    private static final String CONTENT_RESOURCE_PATTERN = "classpath*:content/*";
    private static final Path HASH_RECORD_FILE = Path.of("volumes", "milvus", "ingested-file-hashes.properties");

    @Autowired
    private ChatMemoryStore redisChatMemoryStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Value("${langchain4j.milvus.host:localhost}")
    private String milvusHost;

    @Value("${langchain4j.milvus.port:19530}")
    private int milvusPort;

    @Value("${langchain4j.milvus.database:default}")
    private String milvusDatabase;

    @Value("${langchain4j.milvus.collection-name:hmdp_knowledge}")
    private String collectionName;

    private MilvusEmbeddingStore embeddingStore;

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }

    @Bean
    public EmbeddingStore embeddingStore() {
        embeddingStore = MilvusEmbeddingStore.builder()
                .host(milvusHost)
                .port(milvusPort)
                .databaseName(milvusDatabase)
                .collectionName(collectionName)
                .dimension(1024)
                .build();

        initKnowledgeBase();
        return embeddingStore;
    }

    @Bean
    public ContentRetriever contentRetriever() {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore())
                .minScore(0.5)
                .maxResults(3)
                .embeddingModel(embeddingModel)
                .build();
    }

    /**
     * Load and ingest knowledge files on startup.
     * Only files with new or changed content hash are ingested.
     */
    public void initKnowledgeBase() {
        try {
            Map<String, String> recordedHashes = loadRecordedHashes();
            List<ContentFile> changedFiles = loadChangedContentFiles(recordedHashes);

            if (changedFiles.isEmpty()) {
                System.out.println("No changed files in content/, skip Milvus ingestion.");
                return;
            }

            DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(embeddingStore)
                    .documentSplitter(splitter)
                    .embeddingModel(embeddingModel)
                    .build();

            int ingestedFileCount = 0;
            for (ContentFile file : changedFiles) {
                Document document = Document.from(file.content());
                ingestor.ingest(List.of(document));
                recordedHashes.put(file.key(), file.sha256());
                ingestedFileCount++;
            }

            saveRecordedHashes(recordedHashes);
            System.out.println("Ingested " + ingestedFileCount + " changed file(s) into Milvus collection '" + collectionName + "'.");
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("index not found")) {
                System.out.println("Milvus collection '" + collectionName + "' not found, it will be created on first insert.");
            } else {
                System.err.println("Failed to load knowledge base: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private List<ContentFile> loadChangedContentFiles(Map<String, String> recordedHashes) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(CONTENT_RESOURCE_PATTERN);

        List<ContentFile> changed = new ArrayList<>();
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue;
            }

            String fileName = resource.getFilename();
            if (fileName == null || fileName.isBlank()) {
                continue;
            }

            byte[] bytes;
            try (InputStream inputStream = resource.getInputStream()) {
                bytes = inputStream.readAllBytes();
            }
            if (bytes.length == 0) {
                continue;
            }

            String sha256 = sha256Hex(bytes);
            String key = "content/" + fileName;
            String oldHash = recordedHashes.get(key);
            if (sha256.equals(oldHash)) {
                continue;
            }

            String content = new String(bytes, StandardCharsets.UTF_8);
            changed.add(new ContentFile(key, content, sha256));
        }
        return changed;
    }

    private Map<String, String> loadRecordedHashes() throws IOException {
        Properties properties = new Properties();
        if (Files.exists(HASH_RECORD_FILE)) {
            try (InputStream in = Files.newInputStream(HASH_RECORD_FILE)) {
                properties.load(in);
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            result.put(key, properties.getProperty(key));
        }
        return result;
    }

    private void saveRecordedHashes(Map<String, String> hashes) throws IOException {
        Properties properties = new Properties();
        properties.putAll(hashes);

        if (HASH_RECORD_FILE.getParent() != null) {
            Files.createDirectories(HASH_RECORD_FILE.getParent());
        }

        try (var out = Files.newOutputStream(HASH_RECORD_FILE)) {
            properties.store(out, "Milvus ingested content file SHA-256 hashes");
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record ContentFile(String key, String content, String sha256) {
    }
}
