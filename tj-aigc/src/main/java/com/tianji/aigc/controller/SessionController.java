package com.tianji.aigc.controller;

import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatSessionVO;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.aigc.vo.SessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final ChatSessionService chatSessionService;

    /**
     * 新建会话
     */
    @PostMapping
    public SessionVO createSession(@RequestParam(value = "n", defaultValue = "3") Integer num) {
        return this.chatSessionService.createSession(num);
    }

    /**
     * 返回热门问题
     * @param num
     * @return
     */
    @GetMapping("/hot")
    public List<SessionVO.Example> hotExamples(@RequestParam(value = "n",defaultValue = "3") Integer num){
        return this.chatSessionService.hotExamples(num);
    }

    /**
     * 通过sessionId查询历史的对话记录
     * @param sessionId
     * @return
     */
    @GetMapping("/sessionId")
    public List<MessageVO> queryBySessionId(@PathVariable("sessionId") String sessionId){

        return chatSessionService.queryBySessionId(sessionId);
    }

    /**
     * 返回历史记录列表
     * @return
     */
    @GetMapping("/history")
    public Map<String,List<ChatSessionVO>> queryHistorySession(){
        return chatSessionService.queryHistorySession();
    }


}