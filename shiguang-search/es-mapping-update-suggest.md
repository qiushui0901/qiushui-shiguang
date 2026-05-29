/**
 * 笔记索引 Mapping 更新脚本
 * 新增 title.suggest 字段（Completion Suggester），用于搜索自动补全
 *
 * 执行方式：在 Kibana Dev Tools 或 curl 中执行
 */

// 1. 创建新索引（包含 suggest 字段）
PUT note_v2
{
  "mappings": {
    "properties": {
      "id":            { "type": "long" },
      "cover":         { "type": "keyword" },
      "title":         { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "title.suggest": { "type": "completion", "analyzer": "ik_max_word" },
      "topic":         { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "nickname":      { "type": "keyword" },
      "avatar":        { "type": "keyword" },
      "type":          { "type": "integer" },
      "create_time":   { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
      "update_time":   { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
      "like_total":    { "type": "integer" },
      "collect_total": { "type": "integer" },
      "comment_total": { "type": "integer" }
    }
  }
}

// 2. 通过 _reindex 将旧索引数据迁移到新索引
POST _reindex
{
  "source": { "index": "note" },
  "dest":   { "index": "note_v2" }
}

// 3. 切换别名（零停机切换）
POST _aliases
{
  "actions": [
    { "remove": { "index": "note",   "alias": "note" } },
    { "add":    { "index": "note_v2", "alias": "note" } }
  ]
}

// ============ 或者：如果可以接受短暂停机，直接更新现有索引的 mapping ============
// 注意：ES 不允许修改已有字段的 mapping，但可以新增字段
PUT note/_mapping
{
  "properties": {
    "title.suggest": { "type": "completion", "analyzer": "ik_max_word" }
  }
}

// ============ Canal 同步时需要同步写入 suggest 字段 ============
// 在 CanalSchedule.java 的 syncNoteIndex() 方法中，写入文档时增加：
// docMap.put("title.suggest", titleValue);
// 这样每次 Canal 同步都会自动填充 suggest 字段
