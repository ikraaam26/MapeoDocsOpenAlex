package docserver.importador.openalex;

import dialnet.docserver.model.Docserver;
import static dialnet.docserver.model.Docserver.DOCSERVER_MONGO_TEMPLATE_NAME;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import openalex.documentos.model.OpenalexDocumentos;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 *
 * @author Javier Hernáez Hurtado
 */
@SpringBootApplication
@EnableScheduling
@EnableMongoAuditing
@EnableFeignClients
@Import({Docserver.class, OpenalexDocumentos.class})
public class Application {
    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(Application.class, args)));
    }

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of("docserver-importador-openalex");
    }
    
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Inherited
    public @interface MongoRepository {
    }

    @Configuration
    @EnableMongoRepositories(includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = MongoRepository.class), mongoTemplateRef = DOCSERVER_MONGO_TEMPLATE_NAME)
    static class EnableCustomMongoRepositories {
    }
}
