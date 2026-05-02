package com.hmdp.service;

import com.hmdp.dto.ChatRequest;
import com.hmdp.dto.ChatResponse;

public interface IChatService {
    
    ChatResponse chat(ChatRequest request);
}
