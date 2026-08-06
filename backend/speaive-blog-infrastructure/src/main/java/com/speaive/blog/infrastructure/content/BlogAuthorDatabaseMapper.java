package com.speaive.blog.infrastructure.content;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogAuthorDatabaseMapper extends BaseMapper<BlogAuthorPo> {
}
