package com.tianji.aigc.vo;

import com.tianji.aigc.enums.MessageTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageVO {
    /**
     * 消息的类型，USER为用户，ASSISTANT为ai
     */
    public MessageTypeEnum type;
    /**
     * 消息的文本
     */
    public String content;

    /**
     * 附件参数,用于在历史记录回显时保存额外数据（如课程卡片）
     */
    public Map<String,Object> params;
}
