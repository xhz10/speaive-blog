package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAuthorPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogAuthorDatabaseMapper extends BaseMapper<BlogAuthorPo> {
}
