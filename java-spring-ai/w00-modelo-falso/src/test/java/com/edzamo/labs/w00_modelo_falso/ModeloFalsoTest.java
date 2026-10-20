package com.edzamo.labs.w00_modelo_falso;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

public class ModeloFalsoTest {

    static class ModeloFalso implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            AssistantMessage assistantMessage = new AssistantMessage(
                    "Hola, soy un modelo falso. ¿En qué puedo ayudarte?");
            Generation generation = new Generation(assistantMessage);
            return new ChatResponse(List.of(generation));
        }
    }

    @Test
    void testModeloFalso() {
        var respuesta = ChatClient.create(new ModeloFalso())
                .prompt()
                .user("hola")
                .call()
                .content();

        assertEquals("Hola, soy un modelo falso. ¿En qué puedo ayudarte?", respuesta);
    }
}