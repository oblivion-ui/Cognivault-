package com.cognivault.api;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
@Controller
public class WebSocketController {@MessageMapping("ping") @SendToUser("/queue/pong") public String ping(){return "pong";}}
