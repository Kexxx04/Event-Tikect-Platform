package com.eventplatform.api.model;

import com.eventplatform.api.model.enums.EventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "registered_count", nullable = false)
    private Integer registeredCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.SCHEDULED;

    // Constructor vacío requerido por JPA
    public Event() {
    }

    // Constructor para crear eventos
    public Event(
            String name,
            String description,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Integer capacity,
            EventStatus status
    ) {
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.capacity = capacity;
        this.registeredCount = 0;
        this.status = status != null ? status : EventStatus.SCHEDULED;
    }
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getRegisteredCount() {
        return registeredCount;
    }

    public void setRegisteredCount(Integer registeredCount) {
        this.registeredCount = registeredCount;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public boolean validateForPublication() throws Exception {
        boolean valid = true;

        if (name == null) {
            System.out.println("The event name is missing");
            valid = false;
        } else {
            if (name.trim().isEmpty()) {
                System.out.println("The event name is empty");
                valid = false;
            } else {
                if (name.length() < 3) {
                    System.out.println("The event name is too short");
                    valid = false;
                }
            }
        }

        if (description != null) {
            if (description.length() > 1000) {
                System.out.println("The description is too long");
                valid = false;
            }
        }

        if (startDate == null) {
            System.out.println("The start date is missing");
            valid = false;
        } else {
            if (startDate.isBefore(LocalDateTime.now())) {
                System.out.println("The start date is in the past");
                valid = false;
            }
        }

        if (endDate == null) {
            System.out.println("The end date is missing");
            valid = false;
        } else {
            if (startDate != null && endDate.isBefore(startDate)) {
                System.out.println("The end date is before the start date");
                valid = false;
            }
        }

        if (capacity == null) {
            System.out.println("The capacity is missing");
            valid = false;
        } else {
            if (capacity <= 0) {
                System.out.println("The capacity must be positive");
                valid = false;
            }
        }

        if (registeredCount != null) {
            if (capacity != null && registeredCount > capacity) {
                System.out.println("The registered count exceeds capacity");
                valid = false;
            }
        }

        if (status == null) {
            System.out.println("The event status is missing");
            valid = false;
        }

        if (!valid) {
            throw new Exception("The event is not valid for publication");
        }

        return true;
    }
}
