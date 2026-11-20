package com.eventplatform.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eventplatform.api.inmemory.InMemoryDatabase;
import com.eventplatform.api.inmemory.InMemoryEventRepository;
import com.eventplatform.api.inmemory.InMemoryRegistrationRepository;
import com.eventplatform.api.inmemory.InMemoryTransactionManager;
import com.eventplatform.api.inmemory.InMemoryUserRepository;
import com.eventplatform.api.model.Event;
import com.eventplatform.api.repository.EventRepository;
import com.eventplatform.api.repository.RegistrationRepository;
import com.eventplatform.api.repository.UserRepository;
import com.eventplatform.api.service.WaitlistService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
@ActiveProfiles("test")
class EventPlatformApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private InMemoryDatabase database;

	@BeforeEach
	void clearDatabase() {
		database.clear();
	}

	@Test
	void contextLoads() {
	}

	@Test
	void theTestProfileRunsOnTheInMemoryFakesWithoutJpa() {
		assertInstanceOf(InMemoryUserRepository.class, context.getBean(UserRepository.class));
		assertInstanceOf(InMemoryEventRepository.class, context.getBean(EventRepository.class));
		assertInstanceOf(InMemoryRegistrationRepository.class, context.getBean(RegistrationRepository.class));
		assertInstanceOf(InMemoryTransactionManager.class, context.getBean(PlatformTransactionManager.class));
		assertArrayEquals(new String[0], context.getBeanNamesForType(EntityManagerFactory.class));
	}

	@Test
	void transactionalServicesKeepTheirPropagationOnTheInMemoryTransactionManager() {
		WaitlistService waitlistService = context.getBean(WaitlistService.class);
		Event event = new Event();

		// Propagation.MANDATORY: promoting the waitlist without the transaction of the event lock fails.
		assertThrows(IllegalTransactionStateException.class, () -> waitlistService.fillFreeSeats(event));
	}

}
