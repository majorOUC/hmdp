package com.hmdp.aiservice;

import com.hmdp.tools.ReservationTool;
import com.hmdp.tools.ShopTool;
import com.hmdp.tools.VoucherTool;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService
public interface ConsultantService {
    
    @SystemMessage(fromResource = "system.txt")
    Flux<String> chat(@MemoryId String memoryId, @UserMessage String message);
}
