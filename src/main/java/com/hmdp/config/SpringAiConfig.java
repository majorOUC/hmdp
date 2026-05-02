package com.hmdp.config;

import com.hmdp.tools.ReservationTool;
import com.hmdp.tools.ShopTool;
import com.hmdp.tools.VoucherTool;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.index.CreateIndexParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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
import java.util.stream.Collectors;

@Configuration
public class SpringAiConfig {

    private static final Logger log = LoggerFactory.getLogger(SpringAiConfig.class);
    private static final String CONTENT_RESOURCE_PATTERN = "classpath*:content/*";
    private static final Path HASH_RECORD_FILE = Path.of("volumes", "milvus", "ingested-file-hashes.properties");

    @Autowired
    private ChatMemoryRepository chatMemoryRepository;

    @Autowired
    private VectorStore vectorStore;

    @Value("${spring.ai.vectorstore.milvus.collection-name:hmdp_knowledge}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.milvus.embedding-dimension:1024}")
    private int embeddingDimension;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                  ChatMemory chatMemory,
                                  ShopTool shopTool,
                                  VoucherTool voucherTool,
                                  ReservationTool reservationTool) {
        VectorStore safeStore = new SafeVectorStore(vectorStore);
        Advisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        Advisor ragAdvisor = QuestionAnswerAdvisor.builder(safeStore)
                .searchRequest(SearchRequest.builder().topK(3).similarityThreshold(0.5).build())
                .build();

        return builder
                .defaultSystem(loadSystemPrompt())
                .defaultAdvisors(memoryAdvisor, ragAdvisor)
                .defaultTools(shopTool, voucherTool, reservationTool)
                .build();
    }

    @Bean
    public KnowledgeBaseInitializer knowledgeBaseInitializer(VectorStore vectorStore,
                                                              MilvusServiceClient milvusServiceClient) {
        return new KnowledgeBaseInitializer(vectorStore, milvusServiceClient, collectionName, embeddingDimension);
    }

    private String loadSystemPrompt() {
        try {
            Resource resource = new PathMatchingResourcePatternResolver().getResource("classpath:system.txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load system.txt", e);
        }
    }

    public static class KnowledgeBaseInitializer {

        private final VectorStore vectorStore;
        private final MilvusServiceClient milvusClient;
        private final String collectionName;
        private final int dimension;

        public KnowledgeBaseInitializer(VectorStore vectorStore, MilvusServiceClient milvusClient,
                                         String collectionName, int dimension) {
            this.vectorStore = vectorStore;
            this.milvusClient = milvusClient;
            this.collectionName = collectionName;
            this.dimension = dimension;
            ensureCollectionExists();
            initKnowledgeBase();
        }

        private void ensureCollectionExists() {
            try {
                var descResp = milvusClient.describeCollection(
                        io.milvus.param.collection.DescribeCollectionParam.newBuilder()
                                .withCollectionName(collectionName)
                                .build());
                if (descResp.getStatus() == 0) {
                    System.out.println("Milvus collection '" + collectionName + "' already exists.");
                    return;
                }
            } catch (Exception ignored) {
            }
            System.out.println("Creating Milvus collection '" + collectionName + "' ...");
            FieldType idField = FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build();
            FieldType contentField = FieldType.newBuilder()
                    .withName("content")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(65535)
                    .build();
            FieldType vectorField = FieldType.newBuilder()
                    .withName("embedding")
                    .withDataType(DataType.FloatVector)
                    .withDimension(dimension)
                    .build();
            FieldType metadataField = FieldType.newBuilder()
                    .withName("metadata")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(65535)
                    .build();
            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withShardsNum(1)
                    .addFieldType(idField)
                    .addFieldType(contentField)
                    .addFieldType(vectorField)
                    .addFieldType(metadataField)
                    .build();
            milvusClient.createCollection(createParam);
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName("embedding")
                    .withIndexType(IndexType.HNSW)
                    .withMetricType(MetricType.COSINE)
                    .withExtraParam("{\"M\":16,\"efConstruction\":200}")
                    .withSyncMode(true)
                    .build();
            milvusClient.createIndex(indexParam);
            System.out.println("Milvus collection '" + collectionName + "' created with HNSW index.");
        }

        private void initKnowledgeBase() {
            try {
                Map<String, String> recordedHashes = loadRecordedHashes();
                List<ContentFile> changedFiles = loadChangedContentFiles(recordedHashes);

                if (changedFiles.isEmpty()) {
                    System.out.println("No changed files in content/, skip Milvus ingestion.");
                    return;
                }

                List<Document> documents = changedFiles.stream()
                        .map(file -> new Document(file.content(), Map.of("source", file.key())))
                        .collect(Collectors.toList());

                vectorStore.add(documents);

                for (ContentFile file : changedFiles) {
                    recordedHashes.put(file.key(), file.sha256());
                }
                saveRecordedHashes(recordedHashes);
                System.out.println("Ingested " + changedFiles.size() + " changed file(s) into Milvus.");
            } catch (Exception e) {
                System.err.println("Failed to load knowledge base: " + e.getMessage());
                e.printStackTrace();
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

    static class SafeVectorStore implements VectorStore {

        private static final Logger log = LoggerFactory.getLogger(SafeVectorStore.class);
        private final VectorStore delegate;

        SafeVectorStore(VectorStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public void add(List<Document> documents) {
            delegate.add(documents);
        }

        @Override
        public void delete(List<String> idList) {
            delegate.delete(idList);
        }

        @Override
        public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
            delegate.delete(filterExpression);
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            try {
                return delegate.similaritySearch(request);
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("collection not found")
                        || msg.contains("index not found")
                        || msg.contains("can't find collection"))) {
                    log.warn("Milvus collection not ready, skipping RAG: {}", msg);
                    return List.of();
                }
                throw e;
            }
        }
    }
}
