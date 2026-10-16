package steps;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;

/**
 * Business scenarios run as an authenticated administrator; MockMvc picks the test security
 * context up automatically. Scenarios tagged @authentication exercise real login and tokens.
 */
public class SecurityHooks {

    @Before(value = "not @authentication", order = 0)
    public void authenticateAsAdministrator() {
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @After
    public void clearSecurityContext() {
        TestSecurityContextHolder.clearContext();
    }
}
