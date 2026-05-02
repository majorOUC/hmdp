package com.hmdp.controller;

import com.hmdp.aiservice.ConsultantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatController {
    
    @Autowired
    private ConsultantService consultantService;

    @RequestMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(String memoryId, String message) {
        return consultantService.chat(memoryId, message);
    }
}
