package com.tianji.aigc.service;

import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.common.utils.UserContext;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatService {

    static String getConversationId(String sessionId) {
        return UserContext.getUser()+"_"+sessionId;
    }

    /**
     * 聊天
     *
     * @param question  问题
     * @param sessionId 会话id
     * @return 回答内容
     */
    Flux<ChatEventVO> chat(String question, String sessionId);

    void stop(String sessionId);


}
