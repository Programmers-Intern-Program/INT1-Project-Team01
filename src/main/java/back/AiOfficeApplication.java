package back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class AiOfficeApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiOfficeApplication.class, args);
	}

}
