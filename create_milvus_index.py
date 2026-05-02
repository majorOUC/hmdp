from pymilvus import connections, Collection, Index

# 连接到 Milvus
print("正在连接 Milvus...")
connections.connect(
    alias="default",
    host="localhost",
    port="19530"
)

# 获取集合
print("正在获取集合 hmdp_knowledge...")
collection = Collection("hmdp_knowledge")

# 创建索引（不需要先加载集合）
print("正在创建向量索引...")
index_params = {
    "index_type": "HNSW",
    "params": {"M": 8, "efConstruction": 200},
    "metric_type": "COSINE"
}

collection.create_index(
    field_name="vector",
    index_params=index_params
)

print("OK: 索引创建成功！")
print("索引类型：HNSW")
print("度量类型：COSINE")

# 加载集合
print("正在加载集合...")
collection.load()
print("OK: 集合加载成功")
