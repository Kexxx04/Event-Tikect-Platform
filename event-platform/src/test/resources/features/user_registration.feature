# language: en

Feature: User registration
  As an event platform administrator
  I want to register users with unique email addresses
  So that I can maintain valid participant records

  @acceptance @successful_registration
  Scenario: Register a user with valid data
    Given no user exists with the email "ana@example.com"
    And no user exists with the document "1001001001"
    When I send a POST request to "/api/users" with the following JSON:
      """json
      {
        "name": "Ana Pérez",
        "email": "ana@example.com",
        "document": "1001001001"
      }
      """
    Then the response has HTTP status code 201
    And it contains a generated user ID
    And it contains the following user data:
      | name     | Ana Pérez       |
      | email    | ana@example.com |
      | document | 1001001001      |
      | status   | ACTIVE          |
    And the user is registered and can be retrieved by their ID

  @acceptance @duplicate_email
  Scenario: Reject registration with an email address that is already registered
    Given a registered user exists with the following data:
      | name     | Ana Pérez       |
      | email    | ana@example.com |
      | document | 1001001001      |
    And no user exists with the document "1001001002"
    When I send a POST request to "/api/users" with the following JSON:
      """json
      {
        "name": "Luis Gómez",
        "email": "ana@example.com",
        "document": "1001001002"
      }
      """
    Then the response has HTTP status code 409
    And it contains the message "Email is already registered"
    And no user is registered with the document "1001001002"
    And the existing user's data remains unchanged
