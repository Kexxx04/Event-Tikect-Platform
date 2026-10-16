package steps;

import com.eventplatform.api.EventPlatformApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(classes = EventPlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("acceptance")
public class CucumberSpringConfiguration {
}
