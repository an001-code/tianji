package com.tianji.aigc.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.AIProperties;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.enums.MessageTypeEnum;
import com.tianji.aigc.memory.RedisChatMemoryRepository;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.cloud.commons.util.IdUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.tools.Tool;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    public static final ChatEventVO STOP = ChatEventVO.builder()  // 标记输出结束
            .eventType(ChatEventTypeEnum.STOP.getValue())
            .build();
    private final ChatClient chatClient;
    private final SystemPromptConfig systemPromptConfig;
    // 存储大模型的生成状态，这里采用ConcurrentHashMap是确保线程安全
    // 目前的版本暂时用Map实现，如果考虑分布式环境的话，可以考虑用redis来实现
    //private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();
    private final StringRedisTemplate stringRedisTemplate;

    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS_KEY";

    private final ChatMemory chatMemory;

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        var conversationId = ChatService.getConversationId(sessionId);
        var hashOps = this.stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        var requestId = IdUtil.simpleUUID();
        var userId = UserContext.getUser();
        //用作大模型输出的缓存，可以保存大模型在停止输出之后的历史记录
        var outputBuilder = new StringBuilder();
        return this.chatClient.prompt()
                .system(promptSystem -> promptSystem
                        .text(this.systemPromptConfig.getChatSystemMessage().get()) // 设置系统提示语
                        .param("now", DateUtil.now()) // 设置当前时间的参数（在system提示词中的now参数）
                )
                .user(question)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID,conversationId))//设置对话id
                .toolContext(Map.of(Constant.REQUEST_ID,requestId,Constant.USER_ID,userId))//添加toolcontext
                .stream()
                .chatResponse()
                .doFirst(() -> hashOps.put(sessionId, "true")) // 第一次输出内容时执行
                .doOnError(throwable -> hashOps.delete(sessionId)) // 出现异常时，删除标识
                .doOnComplete(() -> hashOps.delete(sessionId)) // 完成时执行，删除标识
                //当输出被取消的时候，保存输出的内容到历史记录
                .doOnCancel(()->{this.saveStopHistoryRecord(conversationId,outputBuilder.toString());})
                .takeWhile(response -> { // 通过返回值来控制Flux流是否继续，true：继续，false：终止
                    return hashOps.get(sessionId)!=null;
                })
                .map(chatResponse -> {
                    // 对于响应结果进行处理，如果是最后一条数据，就把此次消息id放到内存中
                    var finishReason = chatResponse.getResult().getMetadata().getFinishReason();
                    if(StrUtil.equals(Constant.STOP,finishReason)){
                        var messageId = chatResponse.getMetadata().getId();
                        ToolResultHolder.put(messageId,Constant.REQUEST_ID,requestId);
                    }

                    //tips: chatResponse.getMetadata()和chatResponse.getResult().getMetadata()的作用层级是不一样的，前者是
                    //作用于整次的API调用，比方说可以获取到id,而后者是单次的生成结果,比方说可以获得finishReason


                    // 获取大模型的输出的内容
                    String text = chatResponse.getResult().getOutput().getText();
                    outputBuilder.append(text);
                    // 封装响应对象
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                .concatWith(Flux.defer(()->{
                    var result = ToolResultHolder.get(requestId);

                    if(ObjectUtil.isNotEmpty(result)){
                        ToolResultHolder.remove(requestId);
                        //工具被调用，需要向前端传递参数
                         return Flux.just(ChatEventVO.builder()
                                .eventData(result)
                                .eventType(ChatEventTypeEnum.DATA.getValue())
                                .build(), STOP);

                    }
                    return Flux.just(STOP);
                }));

    }

    @Override
    public void stop(String sessionId) {
        var hashOps = this.stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        hashOps.delete(sessionId);
    }



    private void saveStopHistoryRecord(String conversationId,String content){
        chatMemory.add(conversationId,new AssistantMessage(content));
    }


}
