# language: en

@registrations
Feature: Event registration and waitlist
  As an event platform administrator
  I want to register users for events and manage the waitlist
  So that an event never exceeds its capacity and released seats are reassigned fairly

  @acceptance @confirmed_registration
  Scenario: Confirm a registration while seats remain
    Given an open event with capacity 3
    And an active user "ana@example.com"
    When "ana@example.com" registers for the event
    Then the registration response has HTTP status code 201
    And the registration status is CONFIRMED
    And the event has 1 registered participant and 0 users on the waitlist
    And the events of "ana@example.com" include the event with registration status CONFIRMED

  @acceptance @waitlist
  Scenario: Send new registrations to the waitlist once the event is full
    Given an open event with capacity 3
    And the event already has 3 confirmed registrations
    And an active user "late@example.com"
    When "late@example.com" registers for the event
    Then the registration response has HTTP status code 201
    And the registration status is WAITING_LIST
    And the event has 3 registered participants and 1 user on the waitlist

  @acceptance @waitlist_promotion
  Scenario: The first waitlisted user takes a released seat automatically
    Given an open event with capacity 100
    And the event already has 100 confirmed registrations
    And an active user "user101@example.com"
    When "user101@example.com" registers for the event
    Then the registration status is WAITING_LIST
    When a confirmed participant cancels their registration
    Then the registration response has HTTP status code 200
    And the cancellation promoted "user101@example.com"
    And "user101@example.com" has a CONFIRMED registration for the event
    And the event has 100 registered participants and 0 users on the waitlist

  @acceptance @waitlist_order
  Scenario: The waitlist is served in registration order
    Given an open event with capacity 1
    And the event already has 1 confirmed registration
    And the following active users register for the event:
      | first@example.com  |
      | second@example.com |
      | third@example.com  |
    Then the waitlist is, in order:
      | first@example.com  |
      | second@example.com |
      | third@example.com  |
    When a confirmed participant cancels their registration
    Then the cancellation promoted "first@example.com"
    And the waitlist is, in order:
      | second@example.com |
      | third@example.com  |

  @acceptance @release_seat
  Scenario: Cancelling a confirmed registration without waitlist releases the seat
    Given an open event with capacity 3
    And an active user "ana@example.com"
    And "ana@example.com" is registered for the event
    When "ana@example.com" cancels their registration
    Then the registration response has HTTP status code 200
    And nobody was promoted
    And "ana@example.com" has a CANCELLED registration for the event
    And the event has 0 registered participants and 0 users on the waitlist

  @acceptance @leave_waitlist
  Scenario: Leaving the waitlist does not release a confirmed seat
    Given an open event with capacity 1
    And the event already has 1 confirmed registration
    And an active user "late@example.com"
    And "late@example.com" is registered for the event
    When "late@example.com" cancels their registration
    Then the registration response has HTTP status code 200
    And nobody was promoted
    And the event has 1 registered participant and 0 users on the waitlist

  @acceptance @register_again
  Scenario: A user can register again after cancelling
    Given an open event with capacity 3
    And an active user "ana@example.com"
    And "ana@example.com" is registered for the event
    And "ana@example.com" cancels their registration
    When "ana@example.com" registers for the event
    Then the registration response has HTTP status code 201
    And the registration status is CONFIRMED
    And the event has 1 registered participant and 0 users on the waitlist

  @acceptance @duplicate_registration
  Scenario Outline: Reject registering the same user twice
    Given an open event with capacity <capacity>
    And the event already has <confirmed> confirmed registrations
    And an active user "ana@example.com"
    And "ana@example.com" is registered for the event
    When "ana@example.com" registers for the event
    Then the registration response has HTTP status code 409
    And the registration error message is "User is already registered for this event"
    And the event has <registered> registered participants and <waiting> users on the waitlist

    Examples:
      | capacity | confirmed | registered | waiting |
      | 3        | 0         | 1          | 0       |
      | 2        | 2         | 2          | 1       |

  @acceptance @inactive_user
  Scenario: Reject registrations of inactive users
    Given an open event with capacity 3
    And an inactive user "old@example.com"
    When "old@example.com" registers for the event
    Then the registration response has HTTP status code 409
    And the registration error message is "Inactive users cannot register for events"
    And the event has 0 registered participants and 0 users on the waitlist

  @acceptance @closed_event_registration
  Scenario Outline: Reject registrations for closed events
    Given an open event with capacity 3
    And the event has been <status>
    And an active user "ana@example.com"
    When "ana@example.com" registers for the event
    Then the registration response has HTTP status code 409
    And the registration error message is "Event with status <status> does not accept registrations"

    Examples:
      | status    |
      | CANCELLED |
      | FINISHED  |

  @acceptance @started_event_registration
  Scenario: Reject registrations after the event has started
    Given an event with capacity 3 that started at "2020-01-01T09:00:00"
    And an active user "ana@example.com"
    When "ana@example.com" registers for the event
    Then the registration response has HTTP status code 409
    And the registration error message is "Registrations are closed because the event has already started"
    And the event has 0 registered participants and 0 users on the waitlist
