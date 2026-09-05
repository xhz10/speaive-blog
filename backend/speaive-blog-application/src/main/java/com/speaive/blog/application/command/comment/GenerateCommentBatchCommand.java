package com.speaive.blog.application.command.comment;

import java.util.List;

/** 批量生成命令：角色 ID 按选择顺序排列，单批最多 20 个且不能重复。 */
public record GenerateCommentBatchCommand(List<String> agentIds) {}
