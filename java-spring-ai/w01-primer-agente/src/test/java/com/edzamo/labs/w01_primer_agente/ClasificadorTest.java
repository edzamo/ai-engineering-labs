package com.edzamo.labs.w01_primer_agente;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

public class ClasificadorTest {

    // EL ACTOR DE MENTIRA: hace el papel de Gemini, pero sin red y sin clave.
    // Su único trabajo es decir la línea (el texto) que le den al crearlo.
    static class ModeloFalso implements ChatModel {

        // El "guion": lo que este actor va a responder. Cada test elige el suyo.
        private final String respuesta;

        // La "libreta" del actor: aquí anota la pregunta que le hicieron, para
        // que el test pueda leerla después. Empieza vacía (null) hasta que le pregunten.
        Prompt promptRecibido;

        // El constructor guarda el guion cuando haces `new ModeloFalso(guion)`.
        ModeloFalso(String respuesta) {
            this.respuesta = respuesta;
        }

        // Spring AI llama a este método cuando el cliente "le pregunta" al modelo.
        // Recibe el prompt (lo que se preguntó), pero este actor lo ignora.
        @Override
        public ChatResponse call(Prompt prompt) {
            // Anota en la libreta la pregunta recibida (NUEVO en el test 2).
            this.promptRecibido = prompt;
            // El mensaje del asistente = el texto de la respuesta. Ahora usa el
            // guion (antes tenía el saludo fijo de W00, y por eso el test fallaba).
            AssistantMessage assistantMessage = new AssistantMessage(respuesta);
            // Generation = una opción de respuesta; envuelve el mensaje.
            Generation generation = new Generation(assistantMessage);
            // ChatResponse = la caja de entrega; lleva la lista de opciones (aquí, una).
            return new ChatResponse(List.of(generation));
        }

    }

    @Test
    void clasificadorPagoSospechoso() {
        // PREPARAR: el "formulario ya lleno" que el modelo falso va a responder.
        // Los nombres de los campos deben coincidir con los del record Clasificacion.
        var json = """
                {"razonamiento":"monto inusual","categoria":"FRAUDE","riesgo":8}
                """;

        // ACTUAR: usamos el ChatClient igual que con un modelo real.
        Clasificacion clasificacion = ChatClient.create(new ModeloFalso(json)) // cliente + actor falso
                .prompt()                                                   // empieza a armar la pregunta
                .system("Eres un analista de riesgo de pagos")              // ROL: quién eres y cómo actuar
                .user("Pago de 9500 USD a una cuenta nueva")                // TAREA: lo que se pide ahora
                .call()                                                     // envía la pregunta al modelo
                .entity(Clasificacion.class);                               // JSON -> objeto Java (record)

        // VERIFICAR: cada casilla del formulario llegó con su valor.
        assertEquals("monto inusual", clasificacion.razonamiento());
        assertEquals("FRAUDE", clasificacion.categoria());
        assertEquals(8, clasificacion.riesgo());
    }

    @Test 
    void clasificadorPagoNormal() {
        var json = """
                {"razonamiento":"monto habitual","categoria":"NORMAL","riesgo":2}
                """;

        Clasificacion clasificacion = ChatClient.create(new ModeloFalso(json))
                .prompt()
                .system("Eres un analista de riesgo de pagos")
                .user("Pago de 50 USD a un proveedor recurrente")
                .call()
                .entity(Clasificacion.class);

        assertEquals("monto habitual", clasificacion.razonamiento());
        assertEquals("NORMAL", clasificacion.categoria());
        assertEquals(2, clasificacion.riesgo());
    }

    // TEST 2: ¿le llegó al modelo lo que escribimos, MÁS las instrucciones de formato?
    // Aquí no importa la respuesta del actor: importa la PREGUNTA que recibió.
    @Test
    void elPromptLlegaConRolesEInstruccionesDeFormato() {
        // PREPARAR: guardamos al actor en una variable (en el test 1 no hacía falta)
        // porque después queremos leer su libreta.
        var json = """
                {"razonamiento":"monto inusual","categoria":"FRAUDE","riesgo":8}
                """;
        var modelo = new ModeloFalso(json);

        // ACTUAR: misma cadena que el test 1. Aquí el resultado nos da igual.
        ChatClient.create(modelo)
                .prompt()
                .system("Eres un analista de riesgo de pagos")
                .user("Pago de 9500 USD a una cuenta nueva")
                .call()
                .entity(Clasificacion.class);

        // LEER LA LIBRETA: la pregunta tal como llegó al modelo.
        Prompt recibido = modelo.promptRecibido;

        // VERIFICAR 1: el rol "system" llegó con nuestro texto.
        assertEquals("Eres un analista de riesgo de pagos", recibido.getSystemMessage().getText());

        // VERIFICAR 2: el texto del "user" EMPIEZA con nuestra tarea
        // (después de ella, entity() agregó cosas, por eso no usamos assertEquals).
        assertTrue(recibido.getUserMessage().getText().startsWith("Pago de 9500 USD a una cuenta nueva"));

        // VERIFICAR 3: entity() agregó las instrucciones de formato: deben aparecer
        // los nombres de las tres casillas del record, en algún lugar de la pregunta.
        String todoElTexto = recibido.getContents();
        assertTrue(todoElTexto.contains("razonamiento"));
        assertTrue(todoElTexto.contains("categoria"));
        assertTrue(todoElTexto.contains("riesgo"));
    }

    // TEST 3: ¿qué pasa si el modelo NO responde un JSON? (los modelos reales a veces fallan)
    @Test
    void siElModeloNoRespondeJsonSeLanzaUnaExcepcion() {
        // PREPARAR: el guion del actor es un texto que NO es JSON.
        var modelo = new ModeloFalso("esto no es json");

        // ACTUAR + VERIFICAR a la vez: assertThrows ejecuta el bloque y comprueba que
        // lance una excepción. Si NO lanza nada, el test falla (eso sería malo:
        // significaría que aceptamos basura como si fuera un Clasificacion).
        // Por ahora usamos Exception (cualquiera); luego averiguamos la exacta.
        Exception error = assertThrows(Exception.class, () ->
                ChatClient.create(modelo)
                        .prompt()
                        .system("Eres un analista de riesgo de pagos")
                        .user("Pago de 9500 USD a una cuenta nueva")
                        .call()
                        .entity(Clasificacion.class));

        // Imprimimos el tipo real de la excepción para descubrirlo (lo quitamos después).
        System.out.println("EXCEPCION REAL: " + error.getClass().getName() + " -> " + error.getMessage());
    }

    // TEST 4: el modelo responde un JSON VÁLIDO pero INCOMPLETO (falta la casilla "riesgo").
    // Diferencia con el test 3: allí el texto ni siquiera era JSON; aquí sí lo es,
    // pero no llena todo el formulario.
    @Test
    void siFaltaUnCampoSeLanzaUnaExcepcion() {
        // PREPARAR: guion con solo 2 de las 3 casillas del record.
        var jsonIncompleto = """
                {"razonamiento":"monto inusual","categoria":"FRAUDE"}
                """;
        var modelo = new ModeloFalso(jsonIncompleto);

        // ACTUAR + VERIFICAR: debe lanzar MismatchedInputException (JSON bien escrito
        // pero que no encaja con el record). Por qué importa: si NO lanzara nada,
        // el riesgo quedaría en 0 "por defecto" y un pago sospechoso pasaría como seguro.
        assertThrows(MismatchedInputException.class, () ->
                ChatClient.create(modelo)
                        .prompt()
                        .system("Eres un analista de riesgo de pagos")
                        .user("Pago de 9500 USD a una cuenta nueva")
                        .call()
                        .entity(Clasificacion.class));
    }
}

// El "formulario": tres casillas. El orden importa con un modelo real
// (primero razona, luego decide), por eso razonamiento va antes que categoria.
record Clasificacion(String razonamiento, String categoria, int riesgo) {
}
