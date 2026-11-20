@e2e @login
Feature: Administrator login
  As the Event Platform administrator
  I want to sign in with my credentials
  So that only I can manage events, users and registrations

  Background:
    Given the administrator is on the login page

  Scenario: The administrator signs in with valid credentials
    When the administrator signs in with valid credentials
    Then the administrator should see the "Events" page
    And the signed-in username should be shown in the sidebar

  Scenario Outline: Invalid credentials are rejected by the API
    When the administrator signs in with username "<username>" and password "<password>"
    Then the error "Invalid username or password" should be shown
    And the administrator should stay on the login page

    Examples:
      | username     | password       |
      | admin        | wrong-password |
      | unknown-user | any-password   |

  Scenario Outline: Empty fields are validated before calling the API
    When the administrator signs in with username "<username>" and password "<password>"
    Then the "<field>" field should show the error "<message>"
    And the administrator should stay on the login page

    Examples:
      | username | password     | field    | message              |
      |          | any-password | username | Username is required |
      | admin    |              | password | Password is required |

  Scenario: A protected page redirects to the login page without a session
    When the administrator opens the "/users" page without a session
    Then the administrator should be on the login page

  Scenario: Signing out closes the session
    Given the administrator has signed in
    When the administrator signs out
    Then the administrator should be on the login page
    And opening the "/events" page should show the login page again
