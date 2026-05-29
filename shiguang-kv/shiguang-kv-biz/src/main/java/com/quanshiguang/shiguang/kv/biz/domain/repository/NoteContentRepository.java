package com.quanshiguang.shiguang.kv.biz.domain.repository;

import com.quanshiguang.shiguang.kv.biz.domain.dataobject.NoteContentDO;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.UUID;

/**
 * @author: 犬小哈
 * @date: 2024/7/14 16:21
 * @version: v1.0.0
 * @description: TODO
 **/
public interface NoteContentRepository extends CassandraRepository<NoteContentDO, UUID> {

}
