@e2e @registrations
Feature: Event management with registrations and waitlist
  As the Event Platform administrator
  I want to register users to events with a limited capacity
  So that an event is never overbooked and freed seats go to the waitlist automatically

  Background:
    Given the administrator has signed in

  Scenario: A freed seat is given to the first person on the waitlist
    Given the administrator has created the event "Java Conf" with capacity 2 starting in 30 days
    And the administrator has created the users:
      | name         |
      | Ada Lovelace |
      | Alan Turing  |
      | Grace Hopper |
    When the administrator registers these users to the event:
      | user         |
      | Ada Lovelace |
      | Alan Turing  |
      | Grace Hopper |
    Then the registrations of the event should be:
      | user         | status       |
      | Ada Lovelace | CONFIRMED    |
      | Alan Turing  | CONFIRMED    |
      | Grace Hopper | WAITING_LIST |
    And the event should show 0 available seats and 1 person on the waitlist
    And "Grace Hopper" should be number 1 on the waitlist
    When the administrator cancels the registration of "Ada Lovelace"
    Then "Grace Hopper" should be promoted from the waitlist
    And the registrations of the event should be:
      | user         | status    |
      | Ada Lovelace | CANCELLED |
      | Alan Turing  | CONFIRMED |
      | Grace Hopper | CONFIRMED |
    And the event should show 0 available seats and 0 people on the waitlist

  Scenario: A new event is scheduled and can be activated
    Given the administrator has created the event "Design Days" with capacity 50 starting in 10 days
    When the administrator looks for the event in the events list
    Then the event should be listed with status "SCHEDULED"
    When the administrator changes the status of the event to "ACTIVE"
    Then the status of the event should be "ACTIVE"

  Scenario Outline: The event form rejects invalid data
    When the administrator tries to create an event starting in <start> days and ending in <end> days with capacity "<capacity>"
    Then the "<field>" field should show the error "<message>"
    And the event should not be created

    Examples:
      | start | end | capacity | field    | message                                           |
      | 30    | 31  | 0        | capacity | Capacity must be a whole number greater than zero |
      | 30    | 31  | -5       | capacity | Capacity must be a whole number greater than zero |
      | 30    | 29  | 10       | endDate  | End date must be after the start date.            |

  Scenario: A registered user is not offered again for the same event
    Given the administrator has created the event "Cloud Summit" with capacity 5 starting in 20 days
    And the administrator has created the users:
      | name           |
      | Barbara Liskov |
      | Ken Thompson   |
    And the administrator has registered these users to the event:
      | user           |
      | Barbara Liskov |
    When the administrator opens the registration form of the event
    Then "Barbara Liskov" should not be offered for registration
    But "Ken Thompson" should be offered for registration

  Scenario: The capacity cannot be lowered below the confirmed registrations
    Given the administrator has created the event "Data Workshop" with capacity 2 starting in 15 days
    And the administrator has created the users:
      | name              |
      | Linus Torvalds    |
      | Margaret Hamilton |
    And the administrator has registered these users to the event:
      | user              |
      | Linus Torvalds    |
      | Margaret Hamilton |
    When the administrator changes the capacity of the event to 1
    Then the error "Capacity cannot be lower than the registered count (2)" should be shown

  Scenario: A user cannot be created with an email that is already registered
    Given the administrator has created the users:
      | name              |
      | Katherine Johnson |
    When the administrator tries to create the user "Dorothy Vaughan" with the email of "Katherine Johnson"
    Then the error "Email is already registered" should be shown
