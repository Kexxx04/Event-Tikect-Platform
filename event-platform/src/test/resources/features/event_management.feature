# language: en

Feature: Event management
  As an event platform administrator
  I want to create events and control their lifecycle
  So that only valid events with a positive capacity accept participants

  Background:
    Given the following event data:
      | name        | Java Conf           |
      | description | Talks and workshops |
      | startDate   | 2030-05-10T18:00:00 |
      | endDate     | 2030-05-10T21:00:00 |
      | capacity    | 100                 |

  @acceptance @create_event
  Scenario: Create an event with valid data
    When the administrator creates the event
    Then the event response has HTTP status code 201
    And the event is SCHEDULED with 0 registered participants and capacity 100

  @acceptance @invalid_capacity
  Scenario Outline: Reject an event whose capacity is not positive
    Given the event capacity is <capacity>
    When the administrator creates the event
    Then the event response has HTTP status code 400
    And the validation error for "capacity" is "Capacity must be greater than zero"
    And no event has been created

    Examples:
      | capacity |
      | 0        |
      | -10      |

  @acceptance @valid_transition
  Scenario Outline: Apply a valid status transition
    Given an existing event with status <from>
    When the administrator changes the event status to <to>
    Then the event response has HTTP status code 200
    And the event status is <to>

    Examples:
      | from      | to        |
      | SCHEDULED | ACTIVE    |
      | ACTIVE    | FINISHED  |
      | SCHEDULED | CANCELLED |
      | ACTIVE    | CANCELLED |

  @acceptance @invalid_transition
  Scenario Outline: Reject an invalid status transition
    Given an existing event with status <from>
    When the administrator changes the event status to <to>
    Then the event response has HTTP status code 409
    And the event error message is "Cannot change event status from <from> to <to>"
    And the event status is <from>

    Examples:
      | from      | to        |
      | SCHEDULED | FINISHED  |
      | FINISHED  | ACTIVE    |
      | CANCELLED | SCHEDULED |

  @acceptance @closed_event
  Scenario Outline: Reject modifying a closed event
    Given an existing event with status <status>
    When the administrator replaces the event name with "Spring Conf"
    Then the event response has HTTP status code 409
    And the event error message is "Event with status <status> cannot be modified"

    Examples:
      | status    |
      | FINISHED  |
      | CANCELLED |
