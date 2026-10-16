# language: en

@authentication
Feature: Administrator authentication
  As the platform administrator
  I want to log in to obtain an access token
  So that only authenticated clients can manage users and events

  @acceptance @successful_login
  Scenario: Log in with valid credentials and use the token
    When the administrator logs in with valid credentials
    Then the authentication response has HTTP status code 200
    And the response contains a bearer token for "admin"
    When the client requests "/api/events" with the issued token
    Then the authentication response has HTTP status code 200

  @acceptance @failed_login
  Scenario Outline: Reject invalid credentials
    When someone logs in with username "<username>" and password "<password>"
    Then the authentication response has HTTP status code 401
    And the authentication error message is "Invalid username or password"
    And no token is returned

    Examples:
      | username | password            |
      | admin    | wrong-password      |
      | intruder | test-admin-password |

  @acceptance @protected_endpoint
  Scenario Outline: Reject requests without a valid token
    When the client requests "<path>" <credentials>
    Then the authentication response has HTTP status code 401
    And the authentication error message is "<message>"

    Examples:
      | path        | credentials             | message                   |
      | /api/events | without a token         | Authentication is required |
      | /api/users  | without a token         | Authentication is required |
      | /api/events | with an invalid token   | Invalid or expired token  |
