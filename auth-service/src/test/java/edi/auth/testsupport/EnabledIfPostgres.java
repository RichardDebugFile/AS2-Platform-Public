package edi.auth.testsupport;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Ejecuta la prueba solo si hay PostgreSQL en localhost:5432 (el del docker-compose de la raiz).
 * En el CI ({@code CI=true}) se ejecuta SIEMPRE: alli la base existe y saltarse la prueba
 * escondería un fallo real.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledIfPostgres.Condition.class)
public @interface EnabledIfPostgres {

    class Condition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                return ConditionEvaluationResult.enabled("CI: PostgreSQL es obligatorio");
            }
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("localhost", 5432), 500);
                return ConditionEvaluationResult.enabled("PostgreSQL disponible");
            } catch (IOException _) {
                return ConditionEvaluationResult.disabled(
                        "Sin PostgreSQL en localhost:5432 (docker compose up -d db)");
            }
        }
    }
}
